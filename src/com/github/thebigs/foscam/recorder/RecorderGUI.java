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
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;
import javax.swing.JTextField;

import org.apache.commons.lang.StringUtils;

public class RecorderGUI extends JFrame implements WebCamImageListener {
	private static Recorder recorder = null;
	private static Thread recorderThread = null;
	boolean connect = true;

	private Image image;
	private JLabel imageArea;

	private static String camName = "Brahe";
	private static String camUrl = "";
	private JTextField txtCamUrl;
	private JButton btnConnect;
	private static RecorderGUI gui;
	private JLabel lblCamName;
	private JTextField txtCamName;
	
	public RecorderGUI() {
		super();
		getContentPane().setLayout(new MigLayout("", "[][131.00,grow][335.00,grow][grow][]", "[][][][grow][]"));
		
		lblCamName = new JLabel("Cam Name:");
		getContentPane().add(lblCamName, "flowx,cell 1 0,alignx right");
		
		txtCamName = new JTextField();
		getContentPane().add(txtCamName, "cell 2 0,growx");
		txtCamName.setColumns(10);
		
		JLabel lblCamUrl = new JLabel("Cam URL:");
		getContentPane().add(lblCamUrl, "flowx,cell 1 1,alignx right");
		
		txtCamUrl = new JTextField();
		getContentPane().add(txtCamUrl, "cell 2 1,growx");
		txtCamUrl.setColumns(10);
		
		btnConnect = new JButton("Connect");
		getContentPane().add(btnConnect, "cell 3 2");
		
		btnConnect.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				// Button says "Connect"
				if ( connect ) {
					if ( recorder != null ) {
						recorder.shutdown();
						recorderThread.interrupt();
					}
					camName = txtCamName.getText();
					if ( StringUtils.isBlank(camName)) {
						camName = "defaultCamera";
					}
					camUrl = txtCamUrl.getText();
					if ( StringUtils.isBlank(camUrl) ) {
						// TODO: dialog this
						System.err.println("Cam url is blank.");
					}
					else {
						recorder = new Recorder(camUrl, camName, "recordings", 2L, 20L);

						recorder.addWebCamImageListener(gui);
						recorderThread = new Thread(new Runnable() {
							@Override
							public void run() {
								recorder.run();
							}
						}, "Recorder thread");
						recorderThread.start();
						btnConnect.setText("Disconnect");
						connect = false;
					}
				}
				// Button says "Disconnect"
				else {
					if ( recorder != null ) {
						recorder.shutdown();
						recorderThread.interrupt();
					}
					btnConnect.setText("Connect");
					connect = true;
				}
			}
		});

		JPanel panel = new JPanel();
		getContentPane().add(panel, "cell 1 3 3 1,grow"); 

		imageArea = new JLabel();
		panel.add(imageArea);
	}

	@Override
	public void paint(Graphics g) {
		super.paint(g);
		if ( image != null ) {
			imageArea.setIcon(new ImageIcon(image));
		}
	}
	
	public static void main(String[] args) {
		gui = new RecorderGUI();
		
		gui.setBounds(0, 0, 800, 700);
		gui.setVisible(true);
		gui.addWindowListener(new WindowListener() {
			@Override
			public void windowClosing(WindowEvent arg0) {
				System.out.println("Closing...");
				if ( recorder != null ) {
					recorder.shutdown();
				}
				System.out.println("Finished.");
				System.exit(0);
			}

			@Override
			public void windowActivated(WindowEvent arg0) {	}
			@Override
			public void windowClosed(WindowEvent arg0) { }
			@Override
			public void windowDeactivated(WindowEvent arg0) { }
			@Override
			public void windowDeiconified(WindowEvent arg0) { }
			@Override
			public void windowIconified(WindowEvent arg0) {	}
			@Override
			public void windowOpened(WindowEvent arg0) { }
		});
	}

	@Override
	public void onImage(Image image) {
		this.image = image;
//		System.out.println("Received Image " + new Date().toString());
		repaint();
	}
}
