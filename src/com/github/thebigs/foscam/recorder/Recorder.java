/**
 *  foscamRecorder - http://github.com/TheBigS
 *  Copyright (C) 2012
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.thebigs.foscam.recorder;

import java.awt.Image;
import java.awt.Toolkit;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.DirectoryScanner;

public class Recorder implements Runnable {
	public static final String DEFAULT_CAM_NAME            = "webcam";
	public static final String DEFAULT_OUTPUT_DIR          = "./";
	public static final Long   DEFAULT_MAX_DISK_SPACE_MB   = -1L;
	public static final Long   DEFAULT_CYCLE_DURATION_MINS = 60L;
	
	public void setDefaults() {
		this.camName           = DEFAULT_CAM_NAME;
		this.outputDir         = DEFAULT_OUTPUT_DIR;
		this.cycleDurationMins = DEFAULT_CYCLE_DURATION_MINS;
		this.maxDiskSpaceMb    = DEFAULT_MAX_DISK_SPACE_MB;
	}
	
	private String camUrl;
	private String camName;
	private String outputDir;
	private long   cycleDurationMins;
	private long   maxDiskSpaceMb;
	
	
	private List<WebCamImageListener> imageListeners = new ArrayList<WebCamImageListener>();
	private volatile boolean shutdown = false;
	private Recording currentRecording = null;
	private long totalBytesSaved = 0L;
	
	public Recorder(String camUrl) {
		super();
		setDefaults();
		
		if ( !StringUtils.isBlank(camUrl)) {
			this.camUrl = camUrl;
		}
		else {
			throw new IllegalArgumentException("Cam URL cannot be blank.");
		}
	}
	
	public Recorder(String camUrl, String camName, String outputDir, Long cycleDurationMins, Long maxDiskSpaceMb) {
		super();
		setDefaults();

		if ( !StringUtils.isBlank(camUrl)) {
			this.camUrl = camUrl;
		}
		else {
			throw new IllegalArgumentException("Cam URL cannot be blank.");
		}
		
		if ( !StringUtils.isBlank(camName) ) {
			this.camName = camName;
		}
		if ( !StringUtils.isBlank(outputDir) ) {
			this.outputDir = outputDir;
		}
		if ( cycleDurationMins != null ) {
			this.cycleDurationMins = cycleDurationMins;
		}
		if ( maxDiskSpaceMb != null ) {
			this.maxDiskSpaceMb = maxDiskSpaceMb;
		}
	}
	
	private void initTotalBytesSaved() {
		// initialize the totalBytesSaved
		String[] files = getRecordedVideoFiles();
		for ( String file : files ) {
			totalBytesSaved += new File(outputDir + "/" + file).length();
		}
	}
	
	public void addWebCamImageListener(WebCamImageListener l) {
		synchronized(imageListeners) {
			if ( !imageListeners.contains(l) ) {
				imageListeners.add(l);
			}
		}
	}
	public boolean removeWebCamImageListener(WebCamImageListener l) {
		synchronized(imageListeners) {
			return imageListeners.remove(l);
		}
	}
	public void clearWebCamImageListeners() {
		synchronized(imageListeners) {
			imageListeners.clear();
		}
	}

	public void shutdown() {
		shutdown = true;
		if (currentRecording != null) {
			currentRecording.close();
		}
		clearWebCamImageListeners();
	}
	
	@Override
	public void run() {
		new File(outputDir).mkdirs();
		initTotalBytesSaved();
		
		while( !shutdown ) {
			try {
				URL url = new URL(camUrl);
				
				// Open a Connection to the server
				URLConnection urlc = url.openConnection();
				if (urlc == null) {
					throw new IOException("Unable to make a connection to the image source");
				}
				// Turn off caches to force fresh reload of the jpg
				urlc.setUseCaches(false);
				urlc.connect(); // ignored if already connected.
				InputStream stream = urlc.getInputStream();

				// Line 1: "--ipcamera"
				String delimiter = new String(readLine(stream)); 
				while ( !shutdown && !imageListeners.isEmpty()) {
					// Line 2: "Content-Type: image/jpeg"
					String contentType = new String(readLine(stream)).split(":")[1].trim(); 
					// Line 3: "Content-Length: 23304"
					int contentLength = Integer.parseInt(new String(readLine(stream)).split(":")[1].trim());
					// Line 4: <Blank Line>
					readLine(stream);

					// read image data
					byte[] imageData = new byte[contentLength];
					for (int i = 0; i < contentLength; i++ ){
						int readByte = stream.read();
						imageData[i] = (byte) readByte;
					}

					Image image = Toolkit.getDefaultToolkit().createImage(imageData);
					// save the image to the current recording
					saveImage(image);
					
					// notify listeners
					synchronized(imageListeners) {
						for ( WebCamImageListener l : imageListeners ) {
							l.onImage(image);
						}
					}

					// read stream till next delimiter
					boolean delimiterFound = false;
					ByteArrayOutputStream delimiterBuffer = new ByteArrayOutputStream();
					while (!delimiterFound) {
						int nextByte = stream.read();
						if ( nextByte == 0x2d ) {
							int followingByte = stream.read();
							if ( followingByte == 0x2d ) {
								// read 8 bytes into the delimiter buffer
								for ( int i = 0; i < 8; i++ ) {
									delimiterBuffer.write(stream.read());
								}
								if (new String(delimiterBuffer.toByteArray()).equals("ipcamera")) {
									delimiterFound = true;
									// skip CR/LF
									stream.skip(2);
								}
							}
						}
					}
				}
			} 
			catch (MalformedURLException e) {
				System.err.println("Unable to parse URL: '" + camUrl + "'");
				System.exit(-1);
			}
			catch (IOException e) {
				System.err.println("IO Exception: server not responding at : '" + camUrl + "'. retrying in 1 second");
				
				// sleep for 1 sec
				try { Thread.sleep(1000); } catch (InterruptedException e1) { }
				continue;
			}
			
			
			if ( imageListeners.isEmpty() ) {
				// sleep for 5 secs
				try { Thread.sleep(5000); } catch (InterruptedException e) { }
			}
		}
	}
	
	private void saveImage(Image image) {
		Date currTime = new Date();
		if ( currentRecording == null ) {
			currentRecording = new Recording(outputDir, camName, currTime);
			System.out.println("Recording file: opening " + currentRecording.getRecordingFileLocation());
		}
		// check if we need to cycle the recording file
		else if ( currTime.getTime() - currentRecording.getStartTime() >= TimeUnit.MINUTES.toMillis(cycleDurationMins) ) {
			System.out.println("Cycling Recording file: closing " + currentRecording.getRecordingFileLocation());
			currentRecording.close();
			totalBytesSaved += currentRecording.getRecordingFileSize();
			currentRecording = new Recording(outputDir, camName, currTime);
			System.out.println("Cycling Recording file: opening " + currentRecording.getRecordingFileLocation());
		}
		currentRecording.saveImage(image);
		
		// check for max disk space
		if ( maxDiskSpaceMb >= 0 ) {
			double totalMbUsed = ((totalBytesSaved + currentRecording.getRecordingFileSize()) / 1024.0) / 1024.0;
			if (totalMbUsed >= maxDiskSpaceMb - 5.0) { //5mb ceiling so we don't go over the limit
				System.out.println("Deleting oldest Recording.. finding oldest file");
				deleteOldestRecording();
			}
		}
	}

	private void deleteOldestRecording() {
		String[] files = getRecordedVideoFiles();
		if ( files.length > 0 ) {
			String recordingToDelete = outputDir + "/" + files[0];
			System.out.println("Deleting oldest Recording: " + recordingToDelete);
			File file = new File(recordingToDelete);
			totalBytesSaved -= file.length();
			file.delete();
		}
	}
	
	private String[] getRecordedVideoFiles() {
		DirectoryScanner scanner = new DirectoryScanner();
		scanner.setIncludes(new String[]{"*.mp4"});
		scanner.setBasedir(outputDir);
		scanner.setCaseSensitive(false);
		scanner.scan();
		
		String[] mp4Files = scanner.getIncludedFiles();
		
		List<String> validRecordedVideoFiles = new ArrayList<String>();
		for( String mp4File : mp4Files ) {
			try {
				Recording.FILE_DATE_FORMAT.parse(mp4File);
				if ( mp4File.endsWith(camName + ".mp4") ) {
					validRecordedVideoFiles.add(mp4File);
				}
			} catch (java.text.ParseException e) {
				// didn't match
			}
		}
		
		Collections.sort(validRecordedVideoFiles, new Comparator<String>() {
			@Override
			public int compare(String arg0, String arg1) {
				try {
					Date date0 = Recording.FILE_DATE_FORMAT.parse(arg0);
					Date date1 = Recording.FILE_DATE_FORMAT.parse(arg1);
					
					return date0.compareTo(date1);
				} catch (java.text.ParseException e) {
					e.printStackTrace();
				}
				return 0;
			}
		});

		String[] recordedVideoFilesSorted = new String[validRecordedVideoFiles.size()];
		for ( int i = 0; i <  validRecordedVideoFiles.size(); i++ ) {
			recordedVideoFilesSorted[i] = validRecordedVideoFiles.get(i);
		}
		return recordedVideoFilesSorted;
	}

	private byte[] readLine(InputStream stream) throws IOException {
		ByteArrayOutputStream buffered = new ByteArrayOutputStream();

		while (true) {
			int nextByte = stream.read();
			
			// carrige return
			if ( nextByte == 0x0d ) {
				int lineFeed = stream.read();
				if ( lineFeed != 0x0a ) {
					System.err.println("Parse error: found 0x0d not followed by 0x0a");
				}
				else {
					break;
				}
			}
			else {
				buffered.write(nextByte);
			}
		}
		return buffered.toByteArray();
	}
	
	@Override
	public String toString() {
		return "Record [camUrl=" + camUrl + ", camName=" + camName
				+ ", outputDir=" + outputDir + ", cycleDurationMins="
				+ cycleDurationMins + ", maxDiskSpace=" + maxDiskSpaceMb + "]";
	}
	
	// Command line app
	public static void main(String[] args) {
		Options options = new Options();
		
		options.addOption("c", true, "Cam url (eg. http://<ip>:<port>/<stream page>?<params>");
		options.addOption("o", true, "Output dir location (default: './'). File names will default to '<YYYY.MM.DD-mm-ss>-<webcam name>.mp4'.");
		options.addOption("n", true, "Webcam name. Defaults to 'webcam'.");
		options.addOption("d", true, "Duration (in mins) before cycling to new video file (Defaults to 60mins).");
		options.addOption("x", true, "Max disk space (in megabytes [1024kb]) to use before overwritting recordings. Oldest recordings will be overwritten first. Defaults to -1 (unlimited)");
		options.addOption("h", false, "Print this help message.");
		
		try {
			CommandLine cli = new GnuParser().parse(options, args);
			
			String camUrl = null;
			String camName = null;
			String outputDir = null;
			Long durationMins = null;
			Long maxDiskSpaceMb = null;
			
			if ( cli.hasOption("h") ) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp( "java -jar recorder.jar", options );
				System.exit(-1);
			}
			if ( cli.hasOption("c") ) {
				camUrl = cli.getOptionValue("c");
			}
			else {
				System.err.println("Cam Url (-c) is required. Use -h for more information.");
				System.exit(-1);
			}
			if ( cli.hasOption("n") ) {
				camName = cli.getOptionValue("n");
			}
			if ( cli.hasOption("o") ) {
				outputDir = cli.getOptionValue("o");
			}
			if ( cli.hasOption("d") ) {
				durationMins = Long.parseLong(cli.getOptionValue("d"));
			}
			if ( cli.hasOption("x") ) {
				maxDiskSpaceMb = Long.parseLong(cli.getOptionValue("x"));
			}
			
			Recorder recording = new Recorder(camUrl, camName, outputDir, durationMins, maxDiskSpaceMb);
			
			System.out.println("Starting recording...");
			recording.run(); // stay here until program is terminated
		} 
		catch (ParseException e) {
			System.err.println("Unable to parse command line options: " + e.getMessage());
		}
		catch (Exception e) {
			System.err.println("Unable to parse command line options: " + e.getMessage());
		}
	}
}
