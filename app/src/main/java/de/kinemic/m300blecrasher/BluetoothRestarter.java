package de.kinemic.m300blecrasher;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

public class BluetoothRestarter {

    private Context mContext;
    private RestartListener mListener;
    private BroadcastReceiver mReceiver;

    public BluetoothRestarter(Context context) {
        mContext = context;
    }

    public void restart(RestartListener listener) {
        Log.d("BluetoothRestarter", "restarting Bluetooth...");
        mListener = listener;
        if (mReceiver == null) {
            mReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                        final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                                BluetoothAdapter.ERROR);
                        switch (state) {
                            case BluetoothAdapter.STATE_OFF:
                                Log.d("BluetoothRestarter", "bluetooth disabled, starting...");
                                BluetoothAdapter.getDefaultAdapter().enable();
                                break;
                            case BluetoothAdapter.STATE_ON:
                                Log.d("BluetoothRestarter", "bluetooth enabled, finished...");
                                mListener.onRestartComplete();
                                context.unregisterReceiver(this);
                                mReceiver = null;
                                break;
                        }
                    }
                }
            };
            mContext.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        }
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            Log.d("BluetoothRestarter", "bluetooth disabled, starting...");
            BluetoothAdapter.getDefaultAdapter().enable();
        } else {
            Log.d("BluetoothRestarter", "bluetooth enabled, disabling...");
            BluetoothAdapter.getDefaultAdapter().disable();
        }
    }

    public interface RestartListener {
        void onRestartComplete();
    }
}
