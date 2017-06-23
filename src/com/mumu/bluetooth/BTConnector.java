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
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

public class BTConnector {

	private static final String TAG = "BTConnector";

	static final String SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB";

	private String TARGET_DEVICE_NAME = "";

	private String TARGET_DEVICE_PIN = "1234";

	public static final int PERMISSION_REQUEST_CONSTANT = 1234;

	private BluetoothAdapter bluetoothAdapter;

	private BluetoothDevice mDevice;

	private Context mContext;

	private Map<String, BluetoothDevice> mFoundDeviceMap;

	private Callback mCallback;

	private BTListener mListener;

	private BluetoothSocket mBTSocket;

	private boolean mNeedConnect = false;

	private BTHandle mBTHandler;

	public BTConnector(Context context) {
		init(context);
	}

	/**
	 * 自动配对时的设备名（7.0以下）
	 * 
	 * @param name
	 * 
	 */
	public void setConnectDeviceName(String name) {
		if (name != null) {
			TARGET_DEVICE_NAME = name;
		}
	}

	/**
	 * 自动配对时Pin（7.0以下）
	 * 
	 * @param pin
	 */
	public void setConnectDevicePin(String pin) {
		if (pin != null) {
			TARGET_DEVICE_PIN = pin;
		}
	}

	private BroadcastReceiver receiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {

			String action = intent.getAction();
			Log.v(TAG, "action = " + action);
			/* 搜索到设备 */
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				if (TARGET_DEVICE_NAME.equals(device.getName())) {
					mDevice = device;
					Log.d(TAG, "found device : " + mDevice.getName());
				}
				String addr = device.getAddress();
				if (!mFoundDeviceMap.containsKey(addr)) {
					mFoundDeviceMap.put(addr, device);
					if (mCallback != null) {
						mCallback.onListDataChange(mFoundDeviceMap.values());
					}
				}

			} else /* 设备名称变化 */
			if (BluetoothDevice.ACTION_NAME_CHANGED.equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				if (TARGET_DEVICE_NAME.equals(device.getName())) {
					mDevice = device;
				}
				String addr = device.getAddress();
				if (mFoundDeviceMap.containsKey(addr)) {
					mFoundDeviceMap.put(addr, device);
					Log.d(TAG, "mCallback = " + mCallback);
					if (mCallback != null) {
						mCallback.onListDataChange(mFoundDeviceMap.values());
					}
				}
				Log.d(TAG, "name changed : " + mDevice.getAddress() + " -> " + mDevice.getName());

			} else /* 设备请求配对 */
			if ("android.bluetooth.device.action.PAIRING_REQUEST".equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				Log.d(TAG, "pairing request : " + device.getName());
				if (TARGET_DEVICE_NAME.equals(device.getName())) {
					mDevice = device;
					pair(mDevice, TARGET_DEVICE_PIN);
				}
			} else /* 设备配对状态变化 */
			if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				Log.d(TAG, "BOND_STATE_CHANGED : " + device.getName());
				if (TARGET_DEVICE_NAME.equals(device.getName())) {
					mDevice = device;
					if (mNeedConnect) {
						new Thread() {
							@Override
							public void run() {
								connect(mDevice);
							}
						}.start();
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
		filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
		filter.addAction("android.bluetooth.device.action.PAIRING_REQUEST");
		/* 6.0以上版本需要请求位置权限 */
		if (Build.VERSION.SDK_INT >= 6 && mContext instanceof Activity) {
			ActivityCompat.requestPermissions((Activity) mContext,
					new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, PERMISSION_REQUEST_CONSTANT);
		}
		mContext.registerReceiver(receiver, filter);

	}

	/**
	 * 开始扫描设备
	 */
	public void startDiscovery() {
		mFoundDeviceMap.clear();
		if (mCallback != null) {
			mCallback.onListDataChange(null);
		}
		bluetoothAdapter.startDiscovery();
	}

	/**
	 * ֹͣ取消扫描设备
	 */
	public void cancelDisCovery() {
		bluetoothAdapter.cancelDiscovery();
	}

	/**
	 * 配对设备
	 * 
	 * @param dev
	 * 
	 * @param pin
	 * 
	 */
	public void pair(BluetoothDevice dev, String pin) {
		if (dev == null || pin == null) {
			return;
		}
		setConnectDevicePin(pin);
		if (dev.getBondState() == BluetoothDevice.BOND_NONE) {
			try {
				BTUtils.createBond(dev);
			} catch (Exception e) {
				Log.e(TAG, "error on createBond : " + Log.getStackTraceString(e));
			}
		}
	}

	/**
	 * 连接设备
	 */
	private boolean connect(BluetoothDevice dev) {
		if (dev == null || dev.getBondState() != BluetoothDevice.BOND_BONDED) {
			Log.e(TAG, "connect -> device is null or not paired");
			return false;
		}
		synchronized (this) {
			UUID uuid = UUID.fromString(SPP_UUID);
			bluetoothAdapter.cancelDiscovery();
			mNeedConnect = false;
			try {
				BluetoothSocket btSocket = dev.createRfcommSocketToServiceRecord(uuid);
				if (!btSocket.isConnected()) {
					Log.d(TAG, "connecting...");
					btSocket.connect();
					Log.d(TAG, "connected");
					if (mListener != null) {
						mBTSocket = btSocket;
						mBTHandler = new BTHandle(mBTSocket);
						mListener.onConnect(mBTHandler);
					}
				}
				return true;
			} catch (IOException e) {
				Log.e(TAG, Log.getStackTraceString(e));
			}
		}
		return false;
	}

	/**
	 * 连接设备
	 */
	public void connectDevice(BluetoothDevice dev, BTListener listener) {
		if (dev == null) {
			if (mDevice != null) {
				dev = mDevice;
			}
		}
		mListener = listener;
		if (dev.getBondState() == BluetoothDevice.BOND_NONE) {
			setConnectDeviceName(dev.getName());
			pair(dev, TARGET_DEVICE_PIN);
			mNeedConnect = true;
			return;
		} else if (dev.getBondState() == BluetoothDevice.BOND_BONDING) {
			setConnectDeviceName(dev.getName());
			mNeedConnect = true;
			return;
		} else if (dev.getBondState() == BluetoothDevice.BOND_BONDED) {
			final BluetoothDevice device = dev;
			new Thread() {
				@Override
				public void run() {
					connect(device);
				}
			}.start();
		}
	}

	public void disconnect() {
		mNeedConnect = false;
		if (mBTSocket != null && mBTSocket.isConnected()) {
			if (mBTHandler != null) {
				mBTHandler.release();
			}
			try {
				mBTSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void release() {
		cancelDisCovery();
		disconnect();
		mFoundDeviceMap.clear();
		mContext.unregisterReceiver(receiver);
		mBTSocket = null;
		if (mBTHandler != null) {
			mBTHandler.release();
		}
	}

	public void setListListener(Callback callback) {
		mCallback = callback;
	}

	public static interface Callback {
		public void onListDataChange(Collection<BluetoothDevice> list);
	}

	public static interface BTListener {
		public void onConnect(BTHandle handle);
	}
}
