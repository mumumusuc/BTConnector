package com.mumu.bluetooth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.mumu.bluetooth.BTConnector.Callback;
import com.mumu.bluetooth.R;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
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
		mBTConnector.connect();
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
}
