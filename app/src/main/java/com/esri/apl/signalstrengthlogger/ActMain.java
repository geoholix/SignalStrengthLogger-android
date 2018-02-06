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
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.esri.apl.signalstrengthlogger.data.DBHelper;
import com.esri.apl.signalstrengthlogger.data.ReadingDataPoint;
import com.esri.apl.signalstrengthlogger.util.DBUtils;
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
  private LinearLayout mLytSignalChart;
  private Button mBtnSyncNow;

  private SQLiteDatabase mDb;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Fabric.with(this, new Crashlytics());

    setContentView(R.layout.act_main);

    // Create the SQLite database if needed
    (new Thread(new Runnable() {
      @Override
      public void run() {
        DBHelper dbUtils = new DBHelper(ActMain.this);
        mDb = dbUtils.getWritableDatabase();
      }
    })).start();

    mBtnSyncNow = (Button)findViewById(R.id.btnSyncNow);
    mBtnSyncNow.setOnClickListener(onSyncNowClick);

    mLytSignalChart = (LinearLayout)findViewById(R.id.lytSignalChart);

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

  private View.OnClickListener onSyncNowClick = new View.OnClickListener() {
    @Override
    public void onClick(View view) {
      new Thread(new Runnable() {
        @Override
        public void run() {
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              mBtnSyncNow.setEnabled(false);
            }
          });
          Context ctx = ActMain.this;
          SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
          ConnectivityManager connMgr =
              (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
          try {
            long unposted = DBUtils.doSyncRecords(ctx, mDb, prefs, connMgr);
            String sRes = getString(R.string.notif_unsynced_records, unposted);
            runOnUiThread(new Runnable() {
              @Override
              public void run() {
                Toast.makeText(ctx, sRes, Toast.LENGTH_LONG).show();
              }
            });
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
          } finally {
            runOnUiThread(new Runnable() {
              @Override
              public void run() {
                mBtnSyncNow.setEnabled(true);
              }
            });
          }
        }
      }).start();
    }
  };

  @Override
  protected void onStart() {
    super.onStart();

    // Start listening for chart data from the service
    IntentFilter flt = new IntentFilter();
    flt.addAction(getString(R.string.intent_category_chart_data_available));
    registerReceiver(mChartDataReceiver, flt);
  }

  @Override
  protected void onStop() {
    super.onStop();

    // Stop listening for chart data
    unregisterReceiver(mChartDataReceiver);
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
}
