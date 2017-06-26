package com.mumu.bluetooth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.mumu.bluetooth.BTConnector.BTListener;
import com.mumu.bluetooth.BTConnector.Callback;
import com.mumu.bluetooth.R;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import rx.Subscriber;

public class MyActivity extends Activity implements Callback, BTListener {

	private static final String TAG = "Bt_Activity";

	private final String TARGET_DEVICE_NAME = "bt-mumu";

	private BTConnector mBTConnector;

	private ListView mListView;

	private List<BluetoothDevice> mDeviceList;

	private View mConnect, mDisconnect;

	private TextView mText;
	
	private EditText mEdit;
	
	private BTHandle mBTH;

	private Subscriber<BTHandle> mSub = new Subscriber<BTHandle>() {
		@Override
		public void onCompleted() {

		}

		@Override
		public void onError(Throwable arg0) {

		}

		@Override
		public void onNext(BTHandle arg0) {
			if (arg0 != null) {
				Toast.makeText(getApplicationContext(), "connected", Toast.LENGTH_SHORT).show();
				arg0.receive(MyActivity.this.mHandler);
			}
		}
	};

	private BaseAdapter mAdapter = new BaseAdapter() {

		@Override
		public int getCount() {
			return mDeviceList == null ? 0 : mDeviceList.size();
		}

		@Override
		public Object getItem(int position) {
			return mDeviceList == null ? null : mDeviceList.get(position);
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (!(convertView instanceof TextView)) {
				TextView tv = new TextView(MyActivity.this);
				tv.setPadding(8, 8, 8, 8);
				tv.setBackgroundColor(Color.LTGRAY);
				tv.setTextColor(Color.BLACK);
				tv.setTextSize(28);
				convertView = tv;
			}
			((TextView) convertView).setText(mDeviceList.get(position).getName());
			return convertView;
		}

	};

	@Override
	public void onCreate(Bundle savedInstanceState) { 
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mListView = (ListView) findViewById(R.id.bt_list);
		mListView.setAdapter(mAdapter);

		mConnect = findViewById(R.id.connect);
		mDisconnect = findViewById(R.id.disconnect);
		mText = (TextView) findViewById(R.id.text);

		mDeviceList = new ArrayList<BluetoothDevice>();

		mBTConnector = new BTConnector(this);
		mBTConnector.setConnectDeviceName(TARGET_DEVICE_NAME);
		mBTConnector.setListListener(this);
	}

	public void onFreshClick(View view) {
		mBTConnector.cancelDisCovery();
		mBTConnector.startDiscovery();
	}

	public void onConnectClick(View view) {
		mBTConnector.connectDevice(null, this);
		;
	}

	public void onDisconnectClick(View view) {
		mBTConnector.disconnect();
		mConnect.setVisibility(View.VISIBLE);
		mDisconnect.setVisibility(View.GONE);
	}

	public void onSendClick(View view) {
		if(mBTH != null){
			mBTH.send("hello world \r\n");
		}
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		mBTConnector.release();
	}

	@Override
	public void onListDataChange(Collection<BluetoothDevice> list) {
		mDeviceList.clear();
		if (list != null) {
			mDeviceList.addAll(list);
		}
		mAdapter.notifyDataSetChanged();
	}

	Handler mHandler = new Handler(Looper.getMainLooper()) {
		@Override
		public void handleMessage(Message msg) {
			mText.setText((String) msg.obj);
			if(mBTH != null){
				mBTH.send((String) msg.obj);
			}
		}
	};

	@Override
	public void onConnect(BTHandle handle) {
		if (handle != null) {
			mBTH = handle;
			mBTH.receive(mHandler);
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					mConnect.setVisibility(View.GONE);
					mDisconnect.setVisibility(View.VISIBLE);
				}
			});
		}
	}
}
