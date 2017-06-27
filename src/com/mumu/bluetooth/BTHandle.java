package com.mumu.bluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;

public class BTHandle {

	private static final String TAG = "BTHandle";

	private Handler mHandler;
	private BluetoothSocket mBTSocket;

	private Thread mReceiveThread = new Thread() {
		@Override
		public void run() {
			StringBuilder builder = new StringBuilder();
			InputStream is = null;
			try {
				is = mBTSocket.getInputStream();
				int read = -1;
				final byte[] bytes = new byte[2048];
				while (!isInterrupted() && (read = is.read(bytes)) > -1) {
					if (mBTSocket == null || !mBTSocket.isConnected() || mHandler == null) {
						Thread.sleep(200);
						continue;
					}
					for (int i = 0; i < read; i++) {
						builder.append((char)bytes[i]);
					}
					if (bytes[read - 1] == (byte)'\n' && bytes[read - 2] == (byte)'\r') {						
						String msg = builder.toString();//new String(Arrays.copyOf(bytes, read));
						Log.d(TAG, "receive = " + msg);
						String[] msgs = msg.split("\r\n");
						for(String m: msgs){
							mHandler.obtainMessage(read, m+"\r\n").sendToTarget();// builder.toString()));
						}
						builder.delete(0, builder.length());
					}
					Arrays.fill(bytes, (byte) 0);
				}
			} catch (Exception e) {
				Log.e(TAG, Log.getStackTraceString(e));
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

	public BTHandle(BluetoothSocket socket) {
		mBTSocket = socket;
		mReceiveThread.start();
	}

	public void receive(Handler handler) {
		mHandler = handler;
	}

	public void send(final String msg) {
		Runnable send = new Runnable() {
			@Override
			public void run() {
				if (msg != null && mBTSocket != null && mBTSocket.isConnected()) {
					try {
						mBTSocket.getOutputStream().write(msg.getBytes());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		};
		if (mHandler != null) {
			mHandler.post(send);
		} else {
			/* this may occur ANR */
			send.run();
		}
	}

	public void release() {
		mReceiveThread.interrupt();
		if (mBTSocket != null && mBTSocket.isConnected()) {
			try {
				mBTSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				mBTSocket = null;
			}
		}
	}
}