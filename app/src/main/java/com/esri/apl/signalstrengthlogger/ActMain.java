package com.esri.apl.signalstrengthlogger;

import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.esri.apl.signalstrengthlogger.data.DBHelper;

public class ActMain extends AppCompatActivity {
  public final static String TAG = "ActMain";

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.act_main);

    (new Thread(new Runnable() {
      @Override
      public void run() {
        DBHelper dbUtils = new DBHelper(ActMain.this);
        SQLiteDatabase db = dbUtils.getWritableDatabase();
        db.close();
      }
    })).start();
  }

}
