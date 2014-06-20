/*
 TUIOdroid http://www.tuio.org/
 An Open Source TUIO Tracker for Android
 (c) 2011 by Tobias Schwirten and Martin Kaltenbrunner

 TUIOdroid is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 TUIOdroid is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with TUIOdroid.  If not, see <http://www.gnu.org/licenses/>.
 */

package tuioDroid.impl;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.*;
import android.widget.Toast;
import android.hardware.*;

import java.io.IOException;
import java.net.*;

import keimote.Data.MsgType;
import keimote.Data.PhoneEvent;



/**
 * Main Activity
 * @author Tobias Schwirten
 * @author Martin Kaltenbrunner
 */
public class MainActivity extends Activity implements SensorEventListener {
	
	private class Transmitter extends AsyncTask<byte[],Void,Void> {

		@Override
		protected Void doInBackground(byte[]... messages) {
			
			try {
				sk.send(new DatagramPacket(messages[0], messages[0].length, dest, 9999));

			} catch (UnknownHostException e) {
				sensorUnreg();
				popup("InetAddress Error: " + e.toString());
			} catch (SocketException e) {
				sensorUnreg();
				popup("Socket Error: " + e.toString());
			} catch (IOException e) {
				sensorUnreg();
				popup("IO Error: " + e.toString());
			}			
			return null;
		}
	}

	private SensorManager sm;
	private Context context; 

	private boolean working;

	InetAddress dest; // where we'll send the UDP packets
	DatagramSocket sk; // our socket

	Toast notifier; // Pop-up notifier for errors etc

	/**
	 * View that shows the Touch points etc
	 */
	private TouchView touchView;

	/**
	 * Request Code for the Settings activity to define 
	 * which child activity calls back
	 */
	private static final int REQUEST_CODE_SETTINGS = 0;

	/**
	 * IP Address for OSC connection
	 */
	private String oscIP;

	/**
	 * Port for OSC connection
	 */
	private int oscPort;

	/**
	 * Adjusts the Touch View
	 */
	private boolean drawAdditionalInfo;

	/**
	 * Adjusts the TUIO verbosity
	 */
	private boolean sendPeriodicUpdates;

	/**
	 * Adjusts the Touch View
	 */
	private int screenOrientation;

	private boolean up,down;
	/**
	 *  Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		up = false;
		down = false;
		/* load preferences */
		SharedPreferences settings = this.getPreferences(MODE_PRIVATE);

		/* get Values */
		oscIP = settings.getString("myIP", "192.168.100.172");
		oscPort = settings.getInt("myPort", 3333);
		drawAdditionalInfo = settings.getBoolean("ExtraInfo", false);
		sendPeriodicUpdates = settings.getBoolean("VerboseTUIO", true);
		screenOrientation = settings.getInt ("ScreenOrientation", 0);
		this.adjustScreenOrientation(this.screenOrientation);

		touchView  = new TouchView(this,oscIP,oscPort,drawAdditionalInfo,sendPeriodicUpdates);
		setContentView(touchView);

		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

		// register to listen for changes in values for two sensor types
		sensorReg();
		working = true;
		// needed for the toast popups
		context = getApplicationContext();

		// pop-up notifications Toast
		notifier = new Toast(context);
		notifier = Toast.makeText(context, "jubei",Toast.LENGTH_SHORT);

		// set the destination address
		try {
			dest = InetAddress.getByName(oscIP);
			sk = new DatagramSocket();

		} catch (UnknownHostException e) {
			sensorUnreg();
			popup("UknownHostException :" + e.toString());

		} catch (SocketException e) {
			sensorUnreg();
			popup("SocketException :" + e.toString());
		}

	}

	/**
	 *  Called when the options menu is created
	 *  Options menu is defined in m.xml 
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {   	
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.m, menu);
		return true;
	}


	/**
	 * Called when the user selects an Item in the Menu
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		// Handle item selection
		switch (item.getItemId()) {
		case R.id.settings:
			this.openSettingsActivity();
			return true;

		case R.id.help:
			this.openHelpActivity();
			return true;

		case R.id.calibrate:
			this.calibrate();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}



	/**
	 * Opens the Activity that provides the Settings
	 */
	private void openSettingsActivity (){
		Intent myIntent = new Intent();
		myIntent.setClassName("tuioDroid.impl", "tuioDroid.impl.SettingsActivity"); 
		myIntent.putExtra("IP_in", oscIP);
		myIntent.putExtra("Port_in", oscPort);
		myIntent.putExtra("ExtraInfo", this.drawAdditionalInfo);
		myIntent.putExtra("VerboseTUIO", this.sendPeriodicUpdates);
		myIntent.putExtra("ScreenOrientation", this.screenOrientation);
		startActivityForResult(myIntent, REQUEST_CODE_SETTINGS);
	}


	/**
	 * Opens the Activity that Help information
	 */
	private void openHelpActivity (){
		Intent myIntent = new Intent();
		myIntent.setClassName("tuioDroid.impl", "tuioDroid.impl.HelpActivity");
		startActivity(myIntent);  
	}



	/**
	 * Listens for results of new child activities. 
	 * Different child activities are identified by their requestCode
	 */
	protected void onActivityResult(int requestCode, int resultCode, Intent data){

		// See which child activity is calling us back.
		if(requestCode == REQUEST_CODE_SETTINGS){

			switch (resultCode){

			case RESULT_OK:
				Bundle dataBundle = data.getExtras(); 

				String ip = dataBundle.getString("IP");

				try { InetAddress.getByName(ip); } 
				catch (Exception e) {
					Toast.makeText(this, "Invalid host name or IP address!", Toast.LENGTH_LONG).show();
				}

				int port = 3333;
				try { port = Integer.parseInt(dataBundle.getString("Port")); }
				catch (Exception e) { port = 0; }
				if (port<1024) Toast.makeText(this, "Invalid UDP port number!", Toast.LENGTH_LONG).show();

				this.oscIP = ip;
				this.oscPort = port;        	
				this.drawAdditionalInfo = dataBundle.getBoolean("ExtraInfo");
				this.sendPeriodicUpdates = dataBundle.getBoolean("VerboseTUIO");

				this.touchView.setNewOSCConnection(oscIP, oscPort);

				//Set IP address for sensor data socket 
				try {
					this.dest = InetAddress.getByName(oscIP);
				} catch (UnknownHostException e) {
					Toast.makeText(this, "Invalid host name or IP address!", Toast.LENGTH_LONG).show();
				}

				this.touchView.drawAdditionalInfo = this.drawAdditionalInfo;
				this.touchView.sendPeriodicUpdates = this.sendPeriodicUpdates;

				/* Change behavior of screen rotation */
				this.screenOrientation  = dataBundle.getInt("ScreenOrientation");
				this.adjustScreenOrientation(this.screenOrientation);

				/* Get preferences, edit and commit */
				SharedPreferences settings = this.getPreferences(MODE_PRIVATE);
				SharedPreferences.Editor editor = settings.edit();

				/* define Key/Value */
				editor.putString("myIP", this.oscIP);
				editor.putInt("myPort", this.oscPort);
				editor.putBoolean("ExtraInfo",this.drawAdditionalInfo);
				editor.putBoolean("VerboseTUIO",this.sendPeriodicUpdates);
				editor.putInt("ScreenOrientation",this.screenOrientation);

				/* save Settings*/
				editor.commit();            	    	        			

				break;


			default:
				// Do nothing

			}
		}
	}

	/**
	 * Adjusts the screen orientation
	 */
	private void adjustScreenOrientation (int screenOrientation){

		switch(screenOrientation){

		case 0: this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		break;

		case 1: this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		break;

		case 2: this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		break;

		default: this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		break;
		}	
	}

	// display a toast pop-up for 2 seconds
	private void popup(String msg) {
		notifier.setText(msg);
		notifier.show();
	}

	// functions to register and unregister on sensor event listener
	private void sensorReg() {
		sm.registerListener(this, sm.getSensorList(Sensor.TYPE_ROTATION_VECTOR)
				.get(0), SensorManager.SENSOR_DELAY_FASTEST);
		working = true;
	}

	private void sensorUnreg() {
		sm.unregisterListener(this, sm
				.getSensorList(Sensor.TYPE_ROTATION_VECTOR).get(0));
		working = false;
	}

	public void onAccuracyChanged(Sensor arg0, int arg1) {
		// TODO Auto-generated method stub
	}

	// as soon as a new sensor value exists
	// build a message and put it on the network with tx()
	public void onSensorChanged(SensorEvent s_ev) {
		if (working) {
			PhoneEvent.Builder toTransmit = PhoneEvent.newBuilder()
					.setX(s_ev.values[0])
					.setY(s_ev.values[1])
					.setZ(s_ev.values[2]);
			toTransmit.setType(keimote.Data.MsgType.ROTATION);	
			new Transmitter().execute(toTransmit.build().toByteArray());
		}
	}

	// unregister from listener so that
	// we don't keep transmitting packets
	@Override
	public void onPause() {
		super.onPause();
		sensorUnreg();
	}

	@Override
	public void onStop() {
		super.onStop();
		sensorUnreg();
	}

	@Override
	public void onResume() {
		super.onResume();
		sensorReg();
	}

	public void calibrate() {
		PhoneEvent.Builder toTransmit = PhoneEvent.newBuilder()
				.setType(keimote.Data.MsgType.BUTTON).setButtontype(1);
		new Transmitter().execute(toTransmit.build().toByteArray());
		popup("Orientation Calibrated");
	}

	// handle key presses
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		
			PhoneEvent.Builder toTransmit;
			switch (keyCode) {
			case KeyEvent.KEYCODE_VOLUME_UP:
				if (!up) { //keep track if the button has been pressed to stop repeating
				toTransmit = PhoneEvent.newBuilder().setType(MsgType.BUTTON).setButtontype(2).setState(true);
				new Transmitter().execute(toTransmit.build().toByteArray());
				popup("UP pressed");
				up = true; 
				}
				return true;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				if (!down) {
				toTransmit = PhoneEvent.newBuilder().setType(MsgType.BUTTON).setButtontype(3).setState(true);
				new Transmitter().execute(toTransmit.build().toByteArray());
				popup("DOWN pressed");
				down = true;}
				return true;
				
			default:
				return false;
			}
	}

	// handle key presses
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		
			PhoneEvent.Builder toTransmit;
			switch (keyCode) {
			case KeyEvent.KEYCODE_VOLUME_UP:
				if(up) {
				toTransmit = PhoneEvent.newBuilder().setType(MsgType.BUTTON).setButtontype(2).setState(false);
				new Transmitter().execute(toTransmit.build().toByteArray());
				popup("UP Released");
				up = false;}
				return true;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				if (down) {
				toTransmit = PhoneEvent.newBuilder().setType(MsgType.BUTTON).setButtontype(3).setState(false);
				new Transmitter().execute(toTransmit.build().toByteArray());
				popup("DOWN Released");
				down = false;}
				return true;
			default:
				return false;
			}
	}
}