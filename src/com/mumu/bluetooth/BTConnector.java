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
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.functions.Action0;
import rx.schedulers.Schedulers;

public class BTConnector {

	private static final String TAG = "BTConnector";

	static final String SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB";

	private String TARGET_DEVICE_NAME = "";

	private String TARGET_DEVICE_PIN = "1234";

	public static final int PERMISSION_REQUEST_CONSTANT = 1234;

	private BluetoothAdapter bluetoothAdapter;

	private BluetoothDevice mDevice,mTarget;

	private Context mContext;

	private Map<String, BluetoothDevice> mFoundDeviceMap;

	private Callback mCallback;

	private BluetoothSocket mBTSocket;

	private boolean mNeedConnect = false;

	private BTHandle mBTHandler;
	
	private Subscriber<? super BTHandle> mSub = null;

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
						subscribe.call();
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
	public Observable<BTHandle> connect(BluetoothDevice dev) {
		mTarget = dev;
		return Observable
				.create(new OnSubscribe<BTHandle>() {
					@Override
					public void call(Subscriber<? super BTHandle> arg0) {
						Log.d(TAG, "connect -> sub="+arg0);
						mSub = arg0;
						subscribe.call();
						}
					})
		//		.doOnSubscribe(subscribe)
				.subscribeOn(Schedulers.io());
		//		.doOnUnsubscribe(unSubscribe);
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
					final byte[] bytes = new byte[2048];
					while (!isInterrupted() && mBTSocket != null && mBTSocket.isConnected() && mHandler != null
							&& (read = is.read(bytes)) > -1) {
						for (int i = 0; i < read; i++) {
							builder.append(bytes[i]);
						}
						String msg = new String(Arrays.copyOf(bytes, read));
						Log.d(TAG, "receive = "+msg);
						mHandler.obtainMessage(read, msg).sendToTarget();//builder.toString()));
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

	private Action0 unSubscribe = new Action0() {
		@Override
		public void call() {
			Log.d(TAG, "unSubscribe");
			mSub = null;
			//TODO release
		}
	};
	
	private Action0 subscribe = new Action0() {
		@Override
		public void call() {
			if (mTarget == null) {
				if(mDevice != null){
					mTarget = mDevice;
					subscribe.call();
				}
				return;
			}
			if (mTarget.getBondState() == BluetoothDevice.BOND_NONE) {
				setConnectDeviceName(mTarget.getName());
				pair(mTarget, null);
				mNeedConnect = true;
				return;
			}
			if (mTarget.getBondState() == BluetoothDevice.BOND_BONDING) {
				setConnectDeviceName(mTarget.getName());
				mNeedConnect = true;
				return;
			}
			mNeedConnect = false;
			UUID uuid = UUID.fromString(SPP_UUID);
			bluetoothAdapter.cancelDiscovery();
			try {
				BluetoothSocket btSocket = mTarget.createRfcommSocketToServiceRecord(uuid);
				Log.d(TAG, "connecting...");
				btSocket.connect();
				if (btSocket.isConnected()) {
					Log.i(TAG, "connected,mSub="+mSub);
					mBTSocket = btSocket;
					if(mSub != null){
						mBTHandler = new BTHandle();
						mSub.onNext(mBTHandler);
					}
				}
			} catch (IOException e) {
				Log.e(TAG, Log.getStackTraceString(e));
				if(mSub != null){
					mSub.onError(e);
				}
			}
		}
	};
	
	public static interface Callback {
		public void onListDataChange(Collection<BluetoothDevice> list);
	}
}
