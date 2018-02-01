package com.esri.apl.signalstrengthlogger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.esri.apl.signalstrengthlogger.data.DBHelper;
import com.esri.apl.signalstrengthlogger.data.ReadingDataPoint;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;

public class ActMain extends AppCompatActivity {
  public final static String TAG = "ActMain";
  private LineChart mSignalChart;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.act_main);

    // Create the SQLite database if needed
    (new Thread(new Runnable() {
      @Override
      public void run() {
        DBHelper dbUtils = new DBHelper(ActMain.this);
        SQLiteDatabase db = dbUtils.getWritableDatabase();
        db.close();
      }
    })).start();

    // Chart initialization
    mSignalChart = (LineChart)findViewById(R.id.signalChart);
    mSignalChart.setDrawGridBackground(false);
    XAxis xaxis = mSignalChart.getXAxis();
    xaxis.setEnabled(false);
    xaxis.setAxisMaximum(14f);
    xaxis.setAxisMinimum(0f);
    xaxis.setLabelCount(5);
    YAxis yaxis = mSignalChart.getAxisLeft();
    yaxis.setAxisMaximum(4f);
    yaxis.setAxisMinimum(0f);
  }

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
      dataset.setColor(Color.BLACK);
      LineData data = new LineData(dataset);
      mSignalChart.setData(data);
      mSignalChart.invalidate();
      mSignalChart.setVisibility(View.VISIBLE);
    }
  };
}
