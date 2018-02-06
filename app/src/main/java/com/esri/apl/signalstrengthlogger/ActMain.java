package com.esri.apl.signalstrengthlogger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.esri.apl.signalstrengthlogger.data.DBHelper;
import com.esri.apl.signalstrengthlogger.data.ReadingDataPoint;
import com.esri.apl.signalstrengthlogger.util.DBUtils;
import com.esri.apl.signalstrengthlogger.util.NetUtils;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;

import io.fabric.sdk.android.Fabric;

public class ActMain extends AppCompatActivity {
  public final static String TAG = "ActMain";
  private LineChart mSignalChart;
  private View mLytSignalChart;
  private TextView mLblUnsyncedRecords;

  private SQLiteDatabase mDb;
  private ConnectivityManager mConnMgr;
  SharedPreferences mSharedPrefs;


  private long _unsyncedRecordCount = 0;
  private boolean _isInternetConnected;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Fabric.with(this, new Crashlytics());

    setContentView(R.layout.act_main);

    mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
    mConnMgr = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

    // Create the SQLite database if needed
    (new Thread(new Runnable() {
      @Override
      public void run() {
        DBHelper dbUtils = new DBHelper(ActMain.this);
        mDb = dbUtils.getWritableDatabase();
      }
    })).start();

    mLblUnsyncedRecords = (TextView)findViewById(R.id.lblUnsyncedRecords);

/*    mBtnSyncNow = (Button)findViewById(R.id.btnSyncNow);
    mBtnSyncNow.setOnClickListener(onSyncNowClick);*/

    mLytSignalChart = findViewById(R.id.lytSignalChart);

    // Chart initialization
    mSignalChart = (LineChart)findViewById(R.id.signalChart);
    mSignalChart.setDrawGridBackground(false);
    mSignalChart.setDescription(null);
    mSignalChart.getAxisRight().setEnabled(false);
    XAxis xaxis = mSignalChart.getXAxis();
    xaxis.setEnabled(false);
    YAxis yaxis = mSignalChart.getAxisLeft();
    yaxis.setTextSize(12);
    yaxis.setLabelCount(5, true);
    yaxis.setAxisMaximum(4f);
    yaxis.setAxisMinimum(0f);
    yaxis.setDrawZeroLine(true);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    mDb.close();
  }

  private void doSyncNow() {
    new Thread(new Runnable() {
      @Override
      public void run() {
        Context ctx = ActMain.this;
        ConnectivityManager connMgr =
            (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
          long unposted = DBUtils.doSyncRecords(ctx, mDb, mSharedPrefs, connMgr);
          set_unsyncedRecordCount(unposted);
        } catch (Exception e) {
          Log.e(TAG,"Error synchronizing", e);
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              Toast.makeText(ctx, getString(R.string.msg_err_synchronization, e.getMessage()),
                  Toast.LENGTH_LONG)
                  .show();
            }
          });
        }
      }
    }).start();
  }

  @Override
  protected void onStart() {
    super.onStart();

    // Start listening for internet connectivity changes
    IntentFilter fltConn = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
    fltConn.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
    registerReceiver(mConnectivityStatusReceiver, fltConn);

    // Start listening for chart data from the service
    IntentFilter fltCht = new IntentFilter();
    fltCht.addAction(getString(R.string.intent_category_chart_data_available));
    LocalBroadcastManager.getInstance(this).registerReceiver(mChartDataReceiver, fltCht);

    // Start listening for unsynchronized record counts
    IntentFilter fltUnsyncedCount = new IntentFilter();
    fltUnsyncedCount.addAction(getString(R.string.intent_category_unsynced_count));
    LocalBroadcastManager.getInstance(this).registerReceiver(mUnsyncedCountReceiver, fltUnsyncedCount);
  }

  @Override
  protected void onStop() {
    super.onStop();

    // Stop listening for unsynchronized record counts
    LocalBroadcastManager.getInstance(this).unregisterReceiver(mUnsyncedCountReceiver);

    // Stop listening for chart data
    LocalBroadcastManager.getInstance(this).unregisterReceiver(mChartDataReceiver);

    // Stop listening for internet connectivity changes
    unregisterReceiver(mConnectivityStatusReceiver);
  }

  @Override
  protected void onResume() {
    super.onResume();
    // Get # unposted
    if (mDb != null) {
      long unposted = DBUtils.getUnpostedRecordsCount(mDb, this);
      set_unsyncedRecordCount(unposted);
    }
  }

  private BroadcastReceiver mConnectivityStatusReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      boolean isConnected = NetUtils.isConnectedToNetwork(mConnMgr);
      set_isInternetConnected(isConnected);
    }
  };

  public void set_unsyncedRecordCount(long unsyncedRecordCount) {
    this._unsyncedRecordCount = unsyncedRecordCount;
    String sUnsyncedRecords = getString(R.string.notif_unsynced_records, unsyncedRecordCount);
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        mLblUnsyncedRecords.setText(sUnsyncedRecords);
      }
    });
    doSyncIfNeeded();
  }

  public long get_unsyncedRecordCount() {
    return _unsyncedRecordCount;
  }

  public boolean get_isInternetConnected() {
    return _isInternetConnected;
  }

  public void set_isInternetConnected(boolean isInternetConnected) {
    this._isInternetConnected = isInternetConnected;
    doSyncIfNeeded();
  }

  private boolean get_isLogging() {
    return mSharedPrefs.getBoolean(getString(R.string.pref_key_logging_enabled), false);
  }

  private void doSyncIfNeeded() {
    boolean bNeedToSync = get_unsyncedRecordCount() > 0
        && get_isInternetConnected()
        && !get_isLogging();
    if (bNeedToSync) doSyncNow();

    Log.d(TAG, "Need to sync: " + Boolean.toString(bNeedToSync));
  }

  private BroadcastReceiver mChartDataReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      ArrayList<ReadingDataPoint> chartData =
          intent.getParcelableArrayListExtra(getString(R.string.extra_chart_data_list));
      Log.d(TAG, "onReceive chart data");

      // Update chart
      List<Entry> chartEntries = new ArrayList<>();
      for (int i = 0; i < chartData.size(); i++) {
        chartEntries.add(new Entry(i, chartData.get(i).get_signalStrength()));
      }
      LineDataSet dataset = new LineDataSet(chartEntries, "Readings");
      dataset.setMode(LineDataSet.Mode.STEPPED);
      dataset.setCircleColor(Color.BLACK);
      dataset.setColor(Color.BLACK);
      dataset.setDrawValues(false);
      dataset.setFillColor(Color.DKGRAY);
      dataset.setFillAlpha(85);
      dataset.setDrawFilled(true);
      LineData data = new LineData(dataset);
      mSignalChart.setData(data);
      mSignalChart.getLegend().setEnabled(false);
      mSignalChart.invalidate();

      mLytSignalChart.setVisibility(View.VISIBLE);
    }
  };
  private BroadcastReceiver mUnsyncedCountReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      // Get unsynced record count
      long unsyncedCount = intent.getLongExtra(getString(R.string.extra_unsynced_count), 0);
      set_unsyncedRecordCount(unsyncedCount);
    }
  };
}
