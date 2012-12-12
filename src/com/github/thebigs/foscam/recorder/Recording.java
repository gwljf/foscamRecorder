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

import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.ImageIcon;

import com.xuggle.xuggler.ICodec.ID;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IPixelFormat;
import com.xuggle.xuggler.IRational;
import com.xuggle.xuggler.IStream;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.video.ConverterFactory;
import com.xuggle.xuggler.video.IConverter;

public class Recording {
	public static final DateFormat FILE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
	private static final IPixelFormat.Type pixelFormat = IPixelFormat.Type.YUV420P;
	private static final int NUM_FRAMES_TILL_FILE_SIZE_UPDATE = 30;
	
	private long startTimeMillis = -1L;
	
	private IContainer outContainer = null;
	private long firstTimestamp = -1;
	private String outputVideoFileUrl = "";
	private IStreamCoder outStreamCoder = null;
	private int numFrameCounterTillFileSizeUpdate = NUM_FRAMES_TILL_FILE_SIZE_UPDATE;
	private long recordingFileSizeBytes = -1L;
	
	public Recording(String outputDir, String camName, Date startTime) {
		startTimeMillis = startTime.getTime();

		String fileName = FILE_DATE_FORMAT.format(startTime);
		outputVideoFileUrl = outputDir + "/" + fileName + "-" + camName + ".mp4";
		
		init();
	}
	
	private void init() {
		outContainer = IContainer.make();
		int retval = outContainer.open(outputVideoFileUrl, IContainer.Type.WRITE, null);
		if ( retval < 0 ) {
			throw new RuntimeException("could not open output file");
		}
		IStream outStream = outContainer.addNewStream(ID.CODEC_ID_H264);
		outStreamCoder = outStream.getStreamCoder();
		
		outStreamCoder.setNumPicturesInGroupOfPictures(10);

		outStreamCoder.setBitRate(100000);
//		outStreamCoder.setBitRate(25000);
		outStreamCoder.setBitRateTolerance(9000);
		outStreamCoder.setPixelType(pixelFormat);
		outStreamCoder.setWidth(640);
		outStreamCoder.setHeight(480);
		outStreamCoder.setFlag(IStreamCoder.Flags.FLAG_QSCALE, true);
		outStreamCoder.setGlobalQuality(0);

		IRational frameRate = IRational.make(5);
		outStreamCoder.setFrameRate(frameRate);
		outStreamCoder.setTimeBase(IRational.make(frameRate.getDenominator(), frameRate.getNumerator()));
		frameRate = null; 
			
		outStreamCoder.open();
		outContainer.writeHeader();
	}
	
	public String getRecordingFileLocation() {
		return outputVideoFileUrl;
	}
	
	public long getRecordingFileSize() {
		if ( recordingFileSizeBytes < 0 ) {
			recordingFileSizeBytes = new File(outputVideoFileUrl).length();
		}
		return recordingFileSizeBytes;
	}
	
	public void saveImage(Image image) {
		if ( --numFrameCounterTillFileSizeUpdate <= 0 ) {
			// update filesize bytes
			recordingFileSizeBytes = new File(outputVideoFileUrl).length();
			// reset counter
			numFrameCounterTillFileSizeUpdate = NUM_FRAMES_TILL_FILE_SIZE_UPDATE;
		}
		
		// Save the image to our video stream
		BufferedImage writableBufferImage = convertToType(toBufferedImage(image), BufferedImage.TYPE_3BYTE_BGR);
		
		IPacket packet = IPacket.make();
		IConverter converter = ConverterFactory.createConverter(writableBufferImage, pixelFormat);
		long now = System.currentTimeMillis();
		if ( firstTimestamp  == -1) {
			firstTimestamp = now;
		}
		
		long timestamp = (now-firstTimestamp) * 1000; // convert to microseconds
		
		IVideoPicture outFrame = converter.toPicture(writableBufferImage, timestamp);
		outFrame.setQuality(0);
		outStreamCoder.encodeVideo(packet, outFrame, 0); 
		
		if ( packet.isComplete() ) {
			outContainer.writePacket(packet);
		}
	}
	
	public BufferedImage convertToType(BufferedImage sourceImage, int targetType) {
		BufferedImage image;

		// if the source image is already the target type, return the source
		// image

		if (sourceImage.getType() == targetType)
			image = sourceImage;

		// otherwise create a new image of the target type and draw the new
		// image

		else {
			image = new BufferedImage(sourceImage.getWidth(), sourceImage.getHeight(), targetType);
			image.getGraphics().drawImage(sourceImage, 0, 0, null);
		}

		return image;
	}
	
	public BufferedImage toBufferedImage(Image image) {
	    if (image instanceof BufferedImage) {
	        return (BufferedImage)image;
	    }

	    // This code ensures that all the pixels in the image are loaded
	    image = new ImageIcon(image).getImage();

	    boolean hasAlpha = false;

	    // Create a buffered image with a format that's compatible with the screen
	    BufferedImage bimage = null;
	    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
	    try {
	        // Determine the type of transparency of the new buffered image
	        int transparency = Transparency.OPAQUE;
	        if (hasAlpha) {
	            transparency = Transparency.BITMASK;
	        }

	        // Create the buffered image
	        GraphicsDevice gs = ge.getDefaultScreenDevice();
	        GraphicsConfiguration gc = gs.getDefaultConfiguration();
	        bimage = gc.createCompatibleImage(
	            image.getWidth(null), image.getHeight(null), transparency);
	    } catch (HeadlessException e) {
	        // The system does not have a screen
	    }

	    if (bimage == null) {
	        // Create a buffered image using the default color model
	        int type = BufferedImage.TYPE_INT_RGB;
	        if (hasAlpha) {
	            type = BufferedImage.TYPE_INT_ARGB;
	        }
	        bimage = new BufferedImage(image.getWidth(null), image.getHeight(null), type);
	    }

	    // Copy image to buffered image
	    Graphics g = bimage.createGraphics();

	    // Paint the image onto the buffered image
	    g.drawImage(image, 0, 0, null);
	    g.dispose();

	    return bimage;
	}
	
	public void close() {
		outContainer.writeTrailer();
		outContainer.close();
		
		outStreamCoder.close();
		recordingFileSizeBytes = new File(outputVideoFileUrl).length();
	}

	public long getStartTime() {
		return startTimeMillis;
	}
}
