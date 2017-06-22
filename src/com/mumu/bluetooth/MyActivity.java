package com.mumu.bluetooth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.mumu.bluetooth.BTConnector.BTHandle;
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
import android.widget.ListView;
import android.widget.TextView;

public class MyActivity extends Activity implements Callback {

	private static final String TAG = "Bt_Activity";

	private final String TARGET_DEVICE_NAME = "bt-mumu";

	private BTConnector mBTConnector;

	private ListView mListView;

	private List<BluetoothDevice> mDeviceList;
	
	private View mConnect,mDisconnect;
	
	private TextView mText;

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
	
	public void onConnectClick(View view){
		mBTConnector.connect(null);
		mConnect.setVisibility(View.GONE);
		mDisconnect.setVisibility(View.VISIBLE);
	}
	
	public void onDisconnectClick(View view){
		mBTConnector.disconnect();
		mConnect.setVisibility(View.VISIBLE);
		mDisconnect.setVisibility(View.GONE);
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

	@Override
	public void onDeviceConnected(BTHandle bthandle) {
		bthandle.receive(mHanlder);
	}
	
	Handler mHanlder = new Handler(Looper.getMainLooper()){
		@Override
		public void handleMessage(Message msg) {
			Log.i("btconnector", msg.what+":"+((String)msg.obj));
			mText.setText((String)msg.obj);
		}
	};
}
