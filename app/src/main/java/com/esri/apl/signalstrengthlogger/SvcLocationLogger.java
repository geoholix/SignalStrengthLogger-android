package com.esri.apl.signalstrengthlogger;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.esri.apl.signalstrengthlogger.data.DBHelper;
import com.esri.apl.signalstrengthlogger.data.DBUtils;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Logs signal strength readings at the prescribed displacement intervals.
 * Location readings require explicitly granted permissions, which we assume were granted
 * in the initial activity, else this service would not have been started.
 */
@SuppressLint("MissingPermission")
public class SvcLocationLogger extends Service {
  private static final int SVC_ID = 1;
  private static final int ACT_PI_REQ_CODE = 1;
  private static final String TAG = "SvcLocationLogger";
  private static final int MS_PER_S = 1000;

  private URL mUrlPostFeature = null;

//  private SignalReading mSignalReading = null;
  private TelephonyManager mTelMgr;
  private FusedLocationProviderClient mFusedLocationClient = null;
  private SharedPreferences mSharedPrefs = null;
  private SQLiteDatabase mDb;

  // Various fields needing one-time calculation
  private String mDeviceId;
  private String mOsName = "Android";
  private String mOsVersion = Integer.toString(Build.VERSION.SDK_INT);
  private String mPhoneModel = Build.MANUFACTURER + " " + Build.MODEL;

  @Override
  public IBinder onBind(Intent intent) {
    Log.d(TAG, "onBind");
    return null;
  }

  @Override
  public void onDestroy() {
    stopLogging();
    super.onDestroy();
    Log.d(TAG, "onDestroy");
  }

  @Override
  public void onCreate() {
    super.onCreate();
    mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
    String sUrl = mSharedPrefs.getString(getString(R.string.pref_key_feat_svc_url), null);
    try {
      mUrlPostFeature = new URL(sUrl);
    } catch (Exception e) {
      Log.e(TAG, "Exception creating feature-creation URL.", e);
    }

    mDeviceId = mSharedPrefs.getString(getString(R.string.pref_key_device_id), null);

    // This creates the API client, but doesn't call connect.
    mFusedLocationClient = new FusedLocationProviderClient(this);

    mTelMgr = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

    Log.d(TAG, "onCreate");
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    startLogging();
    Log.d(TAG, "onStartCommand");
    return super.onStartCommand(intent, flags, startId);
  }


  /**
   * Performs various tasks to start logging, including:
   * <ul>
   *     <li>Making the service foreground</li>
   *     <li>Creating a persistent notification</li>
   *     <li>Connecting to the API client and listening to location updates</li>
   * </ul>
   */
  private void startLogging() {
    // Create notification and bring to foreground
    Intent intent = new Intent(this, ActMain.class);
    PendingIntent contentIntent =
        PendingIntent.getActivity(this, ACT_PI_REQ_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT);

    NotificationCompat.Builder nb = new NotificationCompat.Builder(this)
//                .setSmallIcon(android.R.drawable.ic_menu_compass)
        .setSmallIcon(R.drawable.ic_cellsignal)
        .setTicker(getString(R.string.notif_ticker))
        .setContentTitle(getString(R.string.notif_title))
//                .setContentText(getString(R.string.notif_content_text))
        .setContentIntent(contentIntent);

    startForeground(SVC_ID, nb.build());

    if (mDb == null || !mDb.isOpen()) {
      SQLiteOpenHelper dbhlp = new DBHelper(this);
      mDb = dbhlp.getWritableDatabase();
    }

    // Start listening to location updates
    String sDisp = mSharedPrefs.getString(getString(R.string.pref_key_tracking_displacement), "1");
    int iDisp = Integer.parseInt(sDisp);
    String sInterval = mSharedPrefs.getString(getString(R.string.pref_key_tracking_interval), "5");
    int iInterval = Integer.parseInt(sInterval);
    LocationRequest locReq = (new LocationRequest())
        .setInterval(iInterval * MS_PER_S)
        .setSmallestDisplacement(iDisp)
        .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

    Task<Void> tskReqLoc =  mFusedLocationClient.requestLocationUpdates(locReq, mLocationListener, Looper.myLooper());
    tskReqLoc.addOnFailureListener(new OnFailureListener() {
      @Override
      public void onFailure(@NonNull Exception e) {
        Log.e(TAG, "Error requesting updates", e);
        // Stop logging and stop the service entirely
        stopSelf();
      }
    });

    Log.d(TAG, "startLogging");
  }

  private void stopLogging() {
    if (mDb != null && mDb.isOpen()) {
      mDb.close();
      mDb = null;
    }

    // Stop listening to location updates
    mFusedLocationClient.removeLocationUpdates(mLocationListener);

    mSharedPrefs.edit().putBoolean(getString(R.string.pref_key_logging_enabled), false)
        .apply();

    // Stop the service since it's no longer needed
    stopForeground(true);

    Log.d(TAG, "stopLogging");
  }



  private LocationCallback mLocationListener = new LocationCallback() {
    @Override
    public void onLocationResult(final LocationResult locationResult) {
      Log.d(TAG, "onLocationResults");

      new Thread(new Runnable() {
        @Override
        public void run() {
          Location loc = locationResult.getLastLocation();
          if (loc != null) { // Save to DB
            int signalStrength = getCellSignalStrength();

            // Save reading to DB
            ContentValues vals = new ContentValues();
            vals.put(getString(R.string.columnname_longitude), loc.getLongitude());
            vals.put(getString(R.string.columnname_latitude), loc.getLatitude());
            if (loc.hasAltitude())
              vals.put(getString(R.string.columnname_altitude), loc.getAltitude());
            vals.put(getString(R.string.columnname_signalstrength), signalStrength);
            vals.put(getString(R.string.columnname_date), loc.getTime());
            vals.put(getString(R.string.columnname_osname), mOsName);
            vals.put(getString(R.string.columnname_osversion), mOsVersion);
            vals.put(getString(R.string.columnname_phonemodel), mPhoneModel);
            vals.put(getString(R.string.columnname_deviceid), mDeviceId);

            mDb.insert(getString(R.string.tablename_readings), null, vals);
          }
        }
      }).start();
    }
  };

  private void saveReadingToFeatureService() {
    // Save info to feature service
/*    mSignalReading.setLocation(location);
    (new Thread(new Runnable() {
      @Override
      public synchronized void run() {
        HashMap<String, String> params = new HashMap<String, String>();
        params.put("f", "json");
        params.put("features", mSignalReading.getFeatureJSON());
        try {
          HttpURLConnection conn = (HttpURLConnection) mUrlPostFeature.openConnection();

          conn.setReadTimeout(10000);
          conn.setConnectTimeout(15000);
          conn.setRequestMethod("POST");
          conn.setDoInput(true);
          conn.setDoOutput(true);

          OutputStream os = conn.getOutputStream();
          BufferedWriter writer = new BufferedWriter(
              new OutputStreamWriter(os, "UTF-8"));
          String sPostData = getPostDataString(params);
          writer.write(sPostData);
          writer.flush();
          writer.close();
          os.close();

          conn.connect();

          int iResp = conn.getResponseCode();
          Log.d(TAG, getString(R.string.log_create_success, iResp, sPostData));

          conn.disconnect();
        } catch (Exception e) {
          Log.e(TAG, "Problem creating new location log", e);
          // Need to create a notification here
        }

      }
    })).start();*/
  }

  /** Signal strength value
   *  Note: Suppress missing permission error, as this check was done in the main activity
   *  before this service was started.
   * @return 0 through 4
   */
  // TODO Detect and handle transition to/from airplane mode
  private int getCellSignalStrength() {
    int strength = Integer.MIN_VALUE;
    List<CellInfo> cellInfos = mTelMgr.getAllCellInfo();   //This will give info of all sims present inside your mobile
    if(cellInfos!=null) {
      for (int i = 0; i < cellInfos.size(); i++) {
        if (cellInfos.get(i).isRegistered()) {
          if (cellInfos.get(i) instanceof CellInfoWcdma) {
            CellInfoWcdma cellInfoWcdma = (CellInfoWcdma) mTelMgr.getAllCellInfo().get(0);
            CellSignalStrengthWcdma cellSignalStrengthWcdma = cellInfoWcdma.getCellSignalStrength();
            strength = cellSignalStrengthWcdma.getLevel();
          } else if (cellInfos.get(i) instanceof CellInfoLte) {
            CellInfoLte cellInfoLte = (CellInfoLte) mTelMgr.getAllCellInfo().get(0);
            CellSignalStrengthLte cellSignalStrengthLte = cellInfoLte.getCellSignalStrength();
            strength = cellSignalStrengthLte.getLevel();
          } else if (cellInfos.get(i) instanceof CellInfoGsm) {
            CellInfoGsm cellInfogsm = (CellInfoGsm) mTelMgr.getAllCellInfo().get(0);
            CellSignalStrengthGsm cellSignalStrengthGsm = cellInfogsm.getCellSignalStrength();
            strength = cellSignalStrengthGsm.getLevel();
          }
        }
      }
    }
    Log.i(TAG, "Strength: " + strength);

    return strength;
  }

  private String getPostDataString(HashMap<String, String> params) throws UnsupportedEncodingException{
    StringBuilder result = new StringBuilder();
    boolean first = true;
    for(Map.Entry<String, String> entry : params.entrySet()){
      if (first)
        first = false;
      else
        result.append("&");

      result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
//            result.append(entry.getKey());
      result.append("=");
      result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
//            result.append(entry.getValue());
    }

    return result.toString();
  }

  /**
   * Reads unposted records from local database and posts them as inserts to an online feature service
   */
  private void postReadingsToFC() {
    new Thread(new Runnable() {
      @Override
      public void run() {
        deletePreviouslyPostedRecords();

        Cursor curUnposted = getUnpostedRecords();

        // Post adds
        List<Long> recIdsFailedToPost = postUnpostedRecords(curUnposted);

        // Update Sqlite to mark the records as posted
        updateNewlyPostedRecords(recIdsFailedToPost);

        // Cleanup
        SQLiteDatabase.releaseMemory();
      }
    }).start();
  }
  /** Meant to be called from a worker thread **/
  private void deletePreviouslyPostedRecords() {
    String where = getString(R.string.whereclause_posted_records,
        getString(R.string.columnname_was_added_to_fc));
    String table = getString(R.string.tablename_readings);
    int iRes = mDb.delete(table, where, null);
  }

  /** Meant to be called from a worker thread **/
  private Cursor getUnpostedRecords() {
    String table = getString(R.string.viewname_unposted_records);
/*    String where = getString(R.string.whereclause_unposted_records,
        getString(R.string.columnname_was_added_to_fc));*/
    return
        mDb.query(table, null, null, null, null, null, null);
  }

  /** Meant to be called from a worker thread **/
  private List<Long> postUnpostedRecords(Cursor curUnposted) {
    // Get JSON for adds
    List<String> jsonAdds = new ArrayList<>();
    while (curUnposted.moveToNext()) {
      String jsonAdd = DBUtils.jsonForOneFeature(this,
          curUnposted.getFloat(0),
          curUnposted.getFloat(1),
          curUnposted.getFloat(2),
          curUnposted.getFloat(3),
          curUnposted.getLong(4),
          curUnposted.getString(5),
          curUnposted.getString(6),
          curUnposted.getString(7),
          curUnposted.getString(8)
      );
      jsonAdds.add(jsonAdd);
    }
    String sJsonAdds = "[" + TextUtils.join(",", jsonAdds.toArray(new String[]{}));
    String sParams = "?f=json&features=" + sJsonAdds;

    // Post via REST
    String svcUrl = mSharedPrefs.getString(getString(R.string.pref_key_feat_svc_url), null);
    List<Long> failures = new ArrayList<>();
    try {
      OkHttpClient http = new OkHttpClient();
      MediaType mtJSON = MediaType.parse("application/json; charset=utf-8");
      RequestBody reqBody = RequestBody.create(mtJSON, sParams);
      Request req = new Request.Builder()
          .url(svcUrl)
          .post(reqBody)
          .build();
      Response resp = http.newCall(req).execute();

      // Examine results and return list of rowids of failures
      String jsonRes = resp.body().toString();
      JSONObject res = new JSONObject(jsonRes);
      JSONArray addResults = res.getJSONArray("addResults");

      final int rowIdCol = curUnposted.getColumnIndex("rowid");

      for (int iRes = 0; iRes < addResults.length(); iRes++) {
        JSONObject addResult = addResults.getJSONObject(iRes);
        if (!addResult.getString("success").equals("true")) {
          curUnposted.move(iRes);
          long iFailure = curUnposted.getLong(rowIdCol);
          failures.add(iFailure);
        }
      }
    } catch (IOException e) {
      Log.e(TAG, "Error posting adds to feature service", e);
    } catch (JSONException e) {
      Log.e(TAG, "Error parsing add results", e);
    }

    return failures;
  }

  /** Meant to be called from a worker thread **/
  private void updateNewlyPostedRecords(List<Long> failures) {
    String table = getString(R.string.tablename_readings);
    String postStatusColumn = getString(R.string.columnname_was_added_to_fc);
    String where = postStatusColumn + " = 0 "
        + "AND rowid NOT IN ("
//        + StringUtils.join(failures.toArray(new String[]{}), ",")
        + TextUtils.join(",", failures.toArray(new Long[]{}))
        + ")";
    ContentValues postStatus = new ContentValues();
    postStatus.put(postStatusColumn, 1);
    mDb.update(table, postStatus, where, null);
  }
}
