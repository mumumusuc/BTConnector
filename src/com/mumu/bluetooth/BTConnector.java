package com.mumu.bluetooth;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

public class BTConnector {

	private static final String TAG = "BTConnector";

	static final String SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB";

	private String TARGET_DEVICE_NAME = "";// "bt-mumu";

	private String TARGET_DEVICE_PIN = "1234";

	public static final int PERMISSION_REQUEST_CONSTANT = 1234;

	private BluetoothAdapter bluetoothAdapter;

	private BluetoothDevice mDevice;

	private Context mContext;

	private Map<String, BluetoothDevice> mFoundDeviceMap;

	private Callback mCallback;

	BluetoothSocket mBTSocket;

	public BTConnector(Context context) {
		init(context);
	}

	public void setConnectDeviceName(String name) {
		if (name != null) {
			TARGET_DEVICE_NAME = name;
		}
	}

	public void setConnectDevicePin(String pin) {
		if (pin != null && pin.getBytes().length == 4) {
			TARGET_DEVICE_NAME = pin;
		}
	}

	private BroadcastReceiver receiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {

			boolean foundDevice = false;

			String action = intent.getAction();
			Log.e(TAG, "action = " + action);
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				mDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				String addr = mDevice.getAddress();
				if (!mFoundDeviceMap.containsKey(addr)) {
					mFoundDeviceMap.put(addr, mDevice);
					if (mCallback != null) {
						mCallback.onListDataChange(mFoundDeviceMap.values());
					}
				}
				foundDevice = mDevice.getName().equals(TARGET_DEVICE_NAME);
				Log.d(TAG, "found device : " + mDevice.getName());

			} else if (BluetoothDevice.ACTION_NAME_CHANGED.equals(action)) {
				mDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				String addr = mDevice.getAddress();
				if (mFoundDeviceMap.containsKey(addr)) {
					mFoundDeviceMap.put(addr, mDevice);
					Log.d(TAG, "mCallback = " + mCallback);
					if (mCallback != null) {
						mCallback.onListDataChange(mFoundDeviceMap.values());
					}
				}
				foundDevice = mDevice.getName().equals(TARGET_DEVICE_NAME);
				Log.d(TAG, "name changed : " + mDevice.getAddress() + " -> " + mDevice.getName());

			} else if ("android.bluetooth.device.action.PAIRING_REQUEST".equals(action)) {
				mDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				Log.d(TAG, "pairing request : " + mDevice.getName());
				if (TARGET_DEVICE_NAME.equals(mDevice.getName())) {
					try {
						BTUtils.setPairingConfirmation(mDevice, true);
						abortBroadcast();
						BTUtils.setPin(mDevice, TARGET_DEVICE_PIN);
						Log.i(TAG, "setPin done");
					} catch (Exception e) {
						Log.e(TAG, "error on pairing : " + Log.getStackTraceString(e));
					}
				}
			} else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
				mDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				switch (mDevice.getBondState()) {
				case BluetoothDevice.BOND_BONDING:
					Log.d(TAG, "bonding......");
					break;
				case BluetoothDevice.BOND_BONDED:
					Log.d(TAG, "boned");
					break;
				case BluetoothDevice.BOND_NONE:
					Log.d(TAG, "unbond");
				default:
					break;
				}
			}
			if (foundDevice) {
				if (mDevice.getBondState() == BluetoothDevice.BOND_NONE) {
					Log.i(TAG, "not paired, pair it");
					try {
						BTUtils.createBond(mDevice);
					} catch (Exception e) {
						Log.e(TAG, "error on createBond : " + Log.getStackTraceString(e));
					}
				}
			}
		}

	};

	private void init(Context context) {
		if (context != null) {
			mContext = context;
		}
		mFoundDeviceMap = new HashMap<String, BluetoothDevice>();
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (!bluetoothAdapter.isEnabled()) {
			bluetoothAdapter.enable();
		}
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
		filter.addAction("android.bluetooth.device.action.PAIRING_REQUEST");
		filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
		if (Build.VERSION.SDK_INT >= 6 && mContext instanceof Activity) {
			ActivityCompat.requestPermissions((Activity)mContext, new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
					PERMISSION_REQUEST_CONSTANT);
		}
		mContext.registerReceiver(receiver, filter);
	}

	public void startDiscovery() {
		mFoundDeviceMap.clear();
		if (mCallback != null) {
			mCallback.onListDataChange(null);
		}
		bluetoothAdapter.startDiscovery();
	}

	public void cancelDisCovery() {
		bluetoothAdapter.cancelDiscovery();
	}

	public void connect() {
		UUID uuid = UUID.fromString(SPP_UUID);
		try {
			BluetoothSocket btSocket = mDevice.createRfcommSocketToServiceRecord(uuid);
			Log.d(TAG, "connecting...");
			btSocket.connect();
			if (btSocket.isConnected()) {
				Log.i(TAG, "connected");
				mBTSocket = btSocket;
				send("hello world !");
			}
		} catch (IOException e) {
			Log.e(TAG, Log.getStackTraceString(e));
		}
	}

	public void release() {
		cancelDisCovery();
		mFoundDeviceMap.clear();
		mContext.unregisterReceiver(receiver);
		if (mBTSocket != null && mBTSocket.isConnected()) {
			try {
				mBTSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		mBTSocket = null;
	}

	public void setListListener(Callback callback) {
		mCallback = callback;
	}

	public void send(String msg) {
		if (msg != null && mBTSocket != null && mBTSocket.isConnected()) {
			try {
				mBTSocket.getOutputStream().write(msg.getBytes());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static interface Callback {
		public void onListDataChange(Collection<BluetoothDevice> list);
	}
}
