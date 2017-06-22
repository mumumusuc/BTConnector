package com.mumu.bluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
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

	private BluetoothSocket mBTSocket;

	private boolean mNeedConnect = false;

	private BTHandle mBTHandler;

	public BTConnector(Context context) {
		init(context);
	}

	/**
	 * 设置自动配对的设备名称
	 * 
	 * @param name
	 *            设备名
	 */
	public void setConnectDeviceName(String name) {
		if (name != null) {
			TARGET_DEVICE_NAME = name;
		}
	}

	/**
	 * 设置自动配对的Pin（7.0以上非系统应用无法自动配对）
	 * 
	 * @param pin
	 */
	public void setConnectDevicePin(String pin) {
		if (pin != null && pin.getBytes().length == 4) {
			TARGET_DEVICE_NAME = pin;
		}
	}

	private BroadcastReceiver receiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {

			String action = intent.getAction();
			Log.e(TAG, "action = " + action);
			// 发现设备
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				if (TARGET_DEVICE_NAME.equals(device.getName())) {
					mDevice = device;
				}
				String addr = device.getAddress();
				if (!mFoundDeviceMap.containsKey(addr)) {
					mFoundDeviceMap.put(addr, device);
					if (mCallback != null) {
						mCallback.onListDataChange(mFoundDeviceMap.values());
					}
				}
				Log.d(TAG, "found device : " + mDevice.getName());

			} else /* 设备名称变化 */
			if (BluetoothDevice.ACTION_NAME_CHANGED.equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				if (device == null) {
					return;
				}
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

			} else /* 请求配对，7.0以上需系统权限 */
			if ("android.bluetooth.device.action.PAIRING_REQUEST".equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				Log.d(TAG, "pairing request : " + device.getName());
				if (TARGET_DEVICE_NAME.equals(device.getName())) {
					mDevice = device;
					pair(mDevice, TARGET_DEVICE_PIN);
				}
			} else /* 配对状态发生变化 */
			if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				Log.d(TAG, "BOND_STATE_CHANGED : " + device.getName());
				if (TARGET_DEVICE_NAME.equals(device.getName())) {
					mDevice = device;
					if (mNeedConnect) {
						connect(mDevice);
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
		/* 6.0以上需要runtime请求权限，否则无法收到 ACTION_FOUND 的广播 */
		if (Build.VERSION.SDK_INT >= 6 && mContext instanceof Activity) {
			ActivityCompat.requestPermissions((Activity) mContext,
					new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, PERMISSION_REQUEST_CONSTANT);
		}
		mContext.registerReceiver(receiver, filter);
	}

	/**
	 * 开始搜索蓝牙设备
	 */
	public void startDiscovery() {
		mFoundDeviceMap.clear();
		if (mCallback != null) {
			mCallback.onListDataChange(null);
		}
		bluetoothAdapter.startDiscovery();
	}

	/**
	 * 停止搜索蓝牙设备
	 */
	public void cancelDisCovery() {
		bluetoothAdapter.cancelDiscovery();
	}

	/**
	 * 配对指定设备
	 * 
	 * @param dev
	 *            设备
	 * @param pin
	 *            配对Pin
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
	 * 连接指定设备
	 */
	public void connect(BluetoothDevice dev) {
		if (dev == null) {
			connect(mDevice);
			return;
		}
		if (dev.getBondState() == BluetoothDevice.BOND_NONE) {
			setConnectDeviceName(dev.getName());
			pair(dev, null);
			mNeedConnect = true;
			return;
		}
		if (dev.getBondState() == BluetoothDevice.BOND_BONDING) {
			setConnectDeviceName(dev.getName());
			mNeedConnect = true;
			return;
		}
		mNeedConnect = false;
		UUID uuid = UUID.fromString(SPP_UUID);
		try {
			BluetoothSocket btSocket = dev.createRfcommSocketToServiceRecord(uuid);
			Log.d(TAG, "connecting...");
			btSocket.connect();
			if (btSocket.isConnected()) {
				Log.i(TAG, "connected");
				mBTSocket = btSocket;
				if (mCallback != null) {
					mBTHandler = new BTHandle();
					mCallback.onDeviceConnected(mBTHandler);
				}
				// send("hello world !");
			}
		} catch (IOException e) {
			Log.e(TAG, Log.getStackTraceString(e));
		}
	}

	public void disconnect() {
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

	private void send(String msg) {
		if (msg != null && mBTSocket != null && mBTSocket.isConnected()) {
			try {
				mBTSocket.getOutputStream().write(msg.getBytes());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public class BTHandle {
		private Handler mHandler;
		private Thread mReceiveThread = new Thread() {
			@Override
			public void run() {
				// 处理请求内容
				StringBuilder builder = new StringBuilder();
				InputStream is = null;
				try {
					is = mBTSocket.getInputStream();
					int read = -1;
					Log.i(TAG, "builder.length = " + builder.length());
					final byte[] bytes = new byte[2048];
					while (!isInterrupted() && mBTSocket != null && mBTSocket.isConnected() && mHandler != null
							&& (read = is.read(bytes)) > -1) {
						for (int i = 0; i < read; i++) {
							builder.append(bytes[i]);
						}
						
						mHandler.sendMessage(mHandler.obtainMessage(read, new String(bytes)));//builder.toString()));
						builder.delete(0, builder.length());
						Arrays.fill(bytes, (byte)0);
					}
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					if (is != null) {
						try {
							is.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
		};

		// private void receive(){
		// mReceiveThread.notify();
		// }

		private BTHandle() {
			mReceiveThread.start();
		}

		public void receive(Handler handler) {
			mHandler = handler;
		}

		public void send(final String msg) {
			if (mHandler != null) {
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						BTConnector.this.send(msg);
					}
				});
			} else {
				/* this may occur ANR */
				BTConnector.this.send(msg);
			}
		}

		private void release() {
			mReceiveThread.interrupt();
		}
	}

	public static interface Callback {
		public void onListDataChange(Collection<BluetoothDevice> list);

		public void onDeviceConnected(BTHandle bthandle);
	}
}
