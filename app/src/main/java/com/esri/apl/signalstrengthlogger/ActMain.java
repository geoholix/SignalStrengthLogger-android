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
import android.widget.LinearLayout;

import com.crashlytics.android.Crashlytics;
import com.esri.apl.signalstrengthlogger.data.DBHelper;
import com.esri.apl.signalstrengthlogger.data.ReadingDataPoint;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;

import java.util.ArrayList;
import java.util.List;

import io.fabric.sdk.android.Fabric;

public class ActMain extends AppCompatActivity {
  public final static String TAG = "ActMain";
  private LineChart mSignalChart;
  private LinearLayout mLytSignalChart;

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
        SQLiteDatabase db = dbUtils.getWritableDatabase();
        db.close();
      }
    })).start();

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

  private class ChartYAxisFormatter implements IAxisValueFormatter {
    @Override
    public String getFormattedValue(float value, AxisBase axis) {
      return String.valueOf(Math.round(value));
    }
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
