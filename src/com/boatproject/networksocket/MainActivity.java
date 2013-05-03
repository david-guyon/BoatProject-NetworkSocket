package com.boatproject.networksocket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;

import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.Editable;
import android.util.Log;
import android.view.Menu;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends Activity {

	// General and debug variables
	static final String TAG = "NETSOCKET";
	static Activity that;

	// Wireless module variables
	WifiManager wifiManager;
	static final String defaultSSID = "RaspberryPiAP";
	static final String raspberryPiIP = "192.168.3.1";
	static final int raspberryPiPort = 5000; 

	// Broadcast receiver variables
	IntentFilter filters;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		that = this;

		/*
		 * Initialize the WifiManager and ensure
		 * that the wireless module is turned on
		 */
		wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		handleSocket();
//		if(wifiManager.isWifiEnabled())
//			askUserForSSID();
//		else
//			activateTheWifi();

	}

	/**
	 * Activate the wireless module. When the terminate flag is fired
	 * a broadcast receiver call a specific method. 
	 */
	private void activateTheWifi() {
		Log.d(TAG, "Turn wireless module on...");
		wifiManager.setWifiEnabled(true);
		filters = new IntentFilter();
		filters.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		registerReceiver(
			new BroadcastReceiver() {

				@Override
				public void onReceive(Context context, Intent intent) {
					if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
						int wifiState = intent.getIntExtra(
								WifiManager.EXTRA_WIFI_STATE, 
								WifiManager.WIFI_STATE_UNKNOWN);
						if(wifiState == WifiManager.WIFI_STATE_ENABLED) {
							Log.d(TAG, "Wifi is on");
							askUserForSSID();
						}
					}
				}
			}, 
			filters
		);
	}

	private void askUserForSSID() {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle("Enter an SSID");
		alert.setMessage("SSID of the AP (default: RaspberryPiAP)");

		// Set an EditText view to get user input 
		final EditText input = new EditText(this);
		alert.setView(input);

		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				Editable value = input.getText();
				if(value.length() > 1)
					connectToSpecificSSID(value.toString());
				else
					connectToSpecificSSID(defaultSSID);
			}
		});

		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				Log.d(TAG, "The end has no end!");
				Toast.makeText(that, "See ya", Toast.LENGTH_SHORT).show();
				that.finish();
			}
		});
		
		alert.show();
	}

	private void connectToSpecificSSID(String SSID) {
		Log.d(TAG, "SSID: " + SSID);
		
		WifiConfiguration conf = new WifiConfiguration();
		conf.SSID = "\"" + SSID + "\"";
		
		if( wifiManager.addNetwork(conf) == -1)
			Log.e(TAG, "Unable to add the network configuration");
		
		List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
		for( WifiConfiguration i : list ) {
			if(i.SSID != null && i.SSID.equals("\"" + SSID + "\"")) {
				Log.d(TAG, "Try to connect to enable the network");
				if(!wifiManager.disconnect())
					Log.e(TAG, "Fail to disconnect from the current network");
				if(!wifiManager.enableNetwork(i.networkId, true))
					Log.e(TAG, "Fail to enable network");
				if(!wifiManager.reconnect())
					Log.e(TAG, "Fail to reconnect to the wanted network");
				Log.d(TAG, "Network configuration enabled");
				handleSocket();
			}
		}
	}

	private void handleSocket() {
		Log.d(TAG, "Create the Server thread");
		Thread thread = new Thread(new Server());
		thread.start();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	/**
	 * The Server class handle a socket and take care
	 * of receiving and sending data to a specific
	 * IP address. This class implements the Runnable class
	 * and so we can execute an instance of this class
	 * in a Thread. 
	 * 
	 * @author David Guyon
	 *
	 */
	public class Server implements Runnable {

		PrintWriter output;
		BufferedReader input;
		private Socket socket;
		private boolean end = false;

		@Override
		public void run() {
			if(openSocket()) {
				Log.d(TAG, "Socket ready");
				initInputOutput();
				readInput();
			} else {
				Log.e(TAG, "Fail to open the socket");
			}
		}
		
		/**
		 * Initialize a socket and try to connect to a specific IP address
		 * @return true if succeed
		 */
		private boolean openSocket() {
			try {
				socket = new Socket(Inet4Address.getByName(raspberryPiIP), raspberryPiPort);
				return true;
			} catch(UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return false;
		}

		/**
		 * Initialize the input buffer and the output writer
		 */
		private void initInputOutput() {
			OutputStream out;
			try {
				out = socket.getOutputStream();
				output = new PrintWriter(out);
				output.println("Hello Raspberry Pi!");
				output.flush();
				input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		/**
		 * While the socket is running, we read the input buffer
		 * If there is a line inside, we send its content to the GUI
		 */
		private void readInput() {
			while(!end) {
				try {
					Log.d(TAG, input.readLine());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		/**
		 * Write on the output the text given as parameter
		 * @param text: Text you want to send through the socket
		 */
		public void sendText(String text) {
			output.println(text);
			output.flush();
		}

		/**
		 * Stop reading the input buffer and close the socket
		 */
		public void stopSocket() {
			end = true;
			try {
				socket.close();
			} catch (IOException e) {
				Log.d(TAG, "Fail to stop the socket");
				e.printStackTrace();
			}
		}
	}
}
