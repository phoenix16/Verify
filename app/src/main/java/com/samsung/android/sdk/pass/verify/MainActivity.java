package com.samsung.android.sdk.pass.verify;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.SparseArray;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.samsung.android.sdk.SsdkUnsupportedException;
import com.samsung.android.sdk.pass.Spass;
import com.samsung.android.sdk.pass.SpassFingerprint;
import com.samsung.android.sdk.pass.SpassInvalidStateException;

public class MainActivity extends Activity implements Handler.Callback {

    private SpassFingerprint mSpassFingerprint;
    private Spass mSpass;
    private Context mContext;
    private ListView mListView;
    private List<String> mItemArray = new ArrayList<String>();
    private ArrayAdapter<String> mListAdapter;
    private ArrayList<Integer> designatedFingers = null;
    private ArrayList<Integer> designatedFingersDialog = null;

    private boolean needRetryIdentify = false;
    private boolean onReadyIdentify = false;
    private boolean onReadyEnroll = false;
    private boolean hasRegisteredFinger = false;

    private boolean isFeatureEnabled_fingerprint = false;
    private boolean isFeatureEnabled_index = false;
//    private boolean isFeatureEnabled_uniqueId = false;
//    private boolean isFeatureEnabled_custom = false;
//    private boolean isFeatureEnabled_backupPw = false;

    private Handler mHandler;
    private static final int MSG_AUTH = 1000;

    private Button mButton_Auth;
    private SparseArray<Button> mButtonList = null;

    // ====================== Set up Broadcast Receiver ==========================================//

    private BroadcastReceiver mPassReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (SpassFingerprint.ACTION_FINGERPRINT_RESET.equals(action)) {
                Toast.makeText(mContext, "all fingerprints are removed", Toast.LENGTH_SHORT).show();
            } else if (SpassFingerprint.ACTION_FINGERPRINT_REMOVED.equals(action)) {
                int fingerIndex = intent.getIntExtra("fingerIndex", 0);
                Toast.makeText(mContext, fingerIndex + " fingerprints is removed", Toast.LENGTH_SHORT).show();
            } else if (SpassFingerprint.ACTION_FINGERPRINT_ADDED.equals(action)) {
                int fingerIndex = intent.getIntExtra("fingerIndex", 0);
                Toast.makeText(mContext, fingerIndex + " fingerprints is added", Toast.LENGTH_SHORT).show();
            }
        }
    };

    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(SpassFingerprint.ACTION_FINGERPRINT_RESET);
        filter.addAction(SpassFingerprint.ACTION_FINGERPRINT_REMOVED);
        filter.addAction(SpassFingerprint.ACTION_FINGERPRINT_ADDED);
        mContext.registerReceiver(mPassReceiver, filter);
    };

    private void unregisterBroadcastReceiver() {
        try {
            if (mContext != null) {
                mContext.unregisterReceiver(mPassReceiver);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void resetAll() {
        designatedFingers = null;
        needRetryIdentify = false;
        onReadyIdentify = false;
        onReadyEnroll = false;
        hasRegisteredFinger = false;
    }

    // ====================== Set up Spass Fingerprint listener Object ===========================//

    private SpassFingerprint.IdentifyListener mIdentifyListener = new SpassFingerprint.IdentifyListener() {
        @Override
        public void onFinished(int eventStatus) {
//            log("identify finished : reason =" + getEventStatusName(eventStatus));
            int FingerprintIndex = 0;
            String FingerprintGuideText = null;
            try {
                FingerprintIndex = mSpassFingerprint.getIdentifiedFingerprintIndex();
            } catch (IllegalStateException ise) {
                log(ise.getMessage());
            }
            if (eventStatus == SpassFingerprint.STATUS_AUTHENTIFICATION_SUCCESS) {
                log("Authentification Success with Fingerprint Index : " + FingerprintIndex);
            }
            else if (eventStatus == SpassFingerprint.STATUS_TIMEOUT_FAILED) {
                log("Time out");
            }
            else if (eventStatus == SpassFingerprint.STATUS_QUALITY_FAILED) {
                log("Authentification Failed");
                needRetryIdentify = true;
                FingerprintGuideText = mSpassFingerprint.getGuideForPoorQuality();
                Toast.makeText(mContext, FingerprintGuideText, Toast.LENGTH_SHORT).show();
            }
            else {
                log("Authentification Failed");
                needRetryIdentify = true;
            }
            if (!needRetryIdentify) {
                resetIdentifyIndex();
            }
        }

        @Override
        public void onReady() {
//            log("Place your finger on the fingerprint sensor to identify");
        }

        @Override
        public void onStarted() {
//            log("User touched fingerprint sensor");
        }

        @Override
        public void onCompleted() {
//            log("the identify is completed");
            onReadyIdentify = false;
            if (needRetryIdentify) {
                needRetryIdentify = false;
                mHandler.sendEmptyMessageDelayed(MSG_AUTH, 100);
            }
        }
    };

    // ====================== List events status ==================================//

    private static String getEventStatusName(int eventStatus) {
        switch (eventStatus) {
            case SpassFingerprint.STATUS_AUTHENTIFICATION_SUCCESS:
                return "STATUS_AUTHENTIFICATION_SUCCESS";
            case SpassFingerprint.STATUS_AUTHENTIFICATION_PASSWORD_SUCCESS:
                return "STATUS_AUTHENTIFICATION_PASSWORD_SUCCESS";
            case SpassFingerprint.STATUS_TIMEOUT_FAILED:
                return "STATUS_TIMEOUT";
            case SpassFingerprint.STATUS_SENSOR_FAILED:
                return "STATUS_SENSOR_ERROR";
            case SpassFingerprint.STATUS_USER_CANCELLED:
                return "STATUS_USER_CANCELLED";
            case SpassFingerprint.STATUS_QUALITY_FAILED:
                return "STATUS_QUALITY_FAILED";
            case SpassFingerprint.STATUS_USER_CANCELLED_BY_TOUCH_OUTSIDE:
                return "STATUS_USER_CANCELLED_BY_TOUCH_OUTSIDE";
            case SpassFingerprint.STATUS_BUTTON_PRESSED:
                return "STATUS_BUTTON_PRESSED";
            case SpassFingerprint.STATUS_OPERATION_DENIED:
                return "STATUS_OPERATION_DENIED";
            case SpassFingerprint.STATUS_AUTHENTIFICATION_FAILED:
            default:
                return "STATUS_AUTHENTIFICATION_FAILED";
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_AUTH:
                startIdentify();
                break;
        }
        return true;
    }

    private void startIdentify() {
        if (onReadyIdentify == false) {
            try {
                onReadyIdentify = true;
                if (mSpassFingerprint != null) {
                    setIdentifyIndex();
                    mSpassFingerprint.startIdentify(mIdentifyListener);
                }
                if (designatedFingers != null) {
                    log("Swipe finger on fingerprint sensor with " + designatedFingers.toString() + " finger");
                } else {
                    log("Swipe finger on fingerprint sensor");
                }
            } catch (SpassInvalidStateException ise) {
                onReadyIdentify = false;
                resetIdentifyIndex();
                if (ise.getType() == SpassInvalidStateException.STATUS_OPERATION_DENIED) {
                    log("Exception: " + ise.getMessage());
                }
            } catch (IllegalStateException e) {
                onReadyIdentify = false;
                resetIdentifyIndex();
                log("Exception: " + e);
            }
        }
        else {
            log("The previous request is continuing. Please finished or cancel first");
        }
    }

    private void setIdentifyIndex() {
        if (isFeatureEnabled_index) {
            if (mSpassFingerprint != null && designatedFingers != null) {
                mSpassFingerprint.setIntendedFingerprintIndex(designatedFingers);
            }
        }
    }

    private void makeIdentifyIndex(int i) {
        if (designatedFingers == null) {
            designatedFingers = new ArrayList<Integer>();
        }
        for(int j = 0; j< designatedFingers.size(); j++){
            if(i == designatedFingers.get(j)){
                return;
            }
        }
        designatedFingers.add(i);
    }

    private void resetIdentifyIndex() {
        designatedFingers = null;
    }

    // ==================================== Activity functions ================================== //

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this;
        mListAdapter = new ArrayAdapter<String>(this, R.layout.list_entry, mItemArray);
        mListView = (ListView)findViewById(R.id.listView1);
        mHandler = new Handler(this);

        if (mListView != null) {
            mListView.setAdapter(mListAdapter);
        }
        mSpass = new Spass();

        try {
            mSpass.initialize(MainActivity.this);
        } catch (SsdkUnsupportedException e) {
            log("Exception: " + e);
        } catch (UnsupportedOperationException e) {
            log("Fingerprint Service is not supported on the device");
        }
        isFeatureEnabled_fingerprint = mSpass.isFeatureEnabled(Spass.DEVICE_FINGERPRINT);

        if (isFeatureEnabled_fingerprint) {
            mSpassFingerprint = new SpassFingerprint(MainActivity.this);
//            log("Fingerprint Service is supported on the device.");
//            log("SDK version : " + mSpass.getVersionName());
        } else {
            logClear();
            log("Fingerprint Service is not supported on the device.");
            return;
        }

        isFeatureEnabled_index = mSpass.isFeatureEnabled(Spass.DEVICE_FINGERPRINT_FINGER_INDEX);

        registerBroadcastReceiver();
        setButton();

    }

    private Button.OnClickListener onButtonClick = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            logClear();

            switch (v.getId()) {
                case R.id.identify:
                    mHandler.sendEmptyMessage(MSG_AUTH);
                    break;
                // more buttons should be added here
            }
        }
    };

    private void setButton() {
        mButton_Auth = (Button)findViewById(R.id.identify);

        mButtonList = new SparseArray<Button>();
        mButtonList.put(R.id.identify, mButton_Auth);
    }

    private void setButtonEnable() {
        if (mSpassFingerprint == null || mButtonList == null) {
            return;
        }
        try {
            hasRegisteredFinger = mSpassFingerprint.hasRegisteredFinger();
        } catch (UnsupportedOperationException e) {
            log("Fingerprint Service is not supported on the device");
        }
        if (!hasRegisteredFinger) {
            log("Please register finger first");
        }

        final int N = mButtonList.size();
        for (int i = 0; i < N; i++) {
            int id = mButtonList.keyAt(i);
            Button button = (Button)findViewById(id);
            if (button != null) {
                button.setOnClickListener(onButtonClick);
                button.setTextAppearance(mContext, R.style.ButtonStyle);
                if (!isFeatureEnabled_fingerprint) {
                    button.setEnabled(false);
                } else {
                    button.setEnabled(true);
                }
            }
        }
    }

    @Override
    protected void onResume() {
        setButtonEnable();
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterBroadcastReceiver();
        resetAll();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    private void log(String text) {
        final String txt = text;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mItemArray.add(0, txt);
                mListAdapter.notifyDataSetChanged();
            }
        });
    }

    private void logClear() {
        if (mItemArray != null) {
            mItemArray.clear();
        }
        if (mListAdapter != null) {
            mListAdapter.notifyDataSetChanged();
        }
    }
}