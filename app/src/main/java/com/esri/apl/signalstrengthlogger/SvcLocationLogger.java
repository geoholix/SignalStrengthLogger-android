package com.esri.apl.signalstrengthlogger;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.esri.apl.signalstrengthlogger.data.DBHelper;
import com.esri.apl.signalstrengthlogger.data.DBUtils;
import com.esri.apl.signalstrengthlogger.data.ReadingDataPoint;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Logs signal strength readings at the prescribed displacement intervals.
 * Location readings require explicitly granted permissions, which we assume were granted
 * in the initial activity, else this service would not have been started.
 */
@SuppressLint("MissingPermission")
public class SvcLocationLogger extends Service {
  private static final int SVC_NOTIF_ID = 1;
  private static final int ERR_NOTIF_ID = 2;
  private static final int ACT_PI_REQ_CODE = 1;
  private static final String TAG = "SvcLocationLogger";
  private static final int MS_PER_S = 1000;
  private static final int S_PER_MIN = 60;

  private TelephonyManager mTelMgr;
  private FusedLocationProviderClient mFusedLocationClient = null;
  private ConnectivityManager mConnectivityManager = null;

  private Timer mSyncTimer = new Timer();

  /** Don't use directly; use getter and setter instead. */
  private AtomicBoolean _isConnected = new AtomicBoolean(false);

  /** Don't use directly; use getter and setter instead. */
  private final AtomicLong _unsyncedRecordCount = new AtomicLong(0);

  private SharedPreferences mSharedPrefs = null;
  private SQLiteDatabase mDb;
  private final ArrayList<ReadingDataPoint> mChartData = new ArrayList<>();

  // Various fields needing one-time calculation
  private String mDeviceId;
  private static final String mOsName = "Android";
  private static final String mOsVersion = Integer.toString(Build.VERSION.SDK_INT);
  private static final String mPhoneModel = Build.MANUFACTURER + " " + Build.MODEL;
  private String mCellCarrierName;
  private int MAX_CHART_DATA_POINTS;


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

  // onCreate() -> onStartCommand() -> startLogging()
  @Override
  public void onCreate() {
    super.onCreate();
    mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

    mDeviceId = mSharedPrefs.getString(getString(R.string.pref_key_device_id), null);

    // This creates the API client, but doesn't call connect.
    mFusedLocationClient = new FusedLocationProviderClient(this);
    mTelMgr = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

    mConnectivityManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

    mCellCarrierName = "<Unobtainable>";
    if (mTelMgr.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA
        && mTelMgr.getSimState() == TelephonyManager.SIM_STATE_READY)
      mCellCarrierName = mTelMgr.getSimOperatorName();
    else mCellCarrierName = mTelMgr.getNetworkOperatorName();

    MAX_CHART_DATA_POINTS = getResources().getInteger(R.integer.max_chart_data_points);

    Log.d(TAG, "onCreate");
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    startLogging();
    Log.d(TAG, "onStartCommand");
    return super.onStartCommand(intent, flags, startId);
  }

  private Notification buildSvcForegroundNotification(String notText) {
    // Create notification and bring to foreground
    Intent intent = new Intent(this, ActMain.class);
    PendingIntent contentIntent =
        PendingIntent.getActivity(this, ACT_PI_REQ_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT);

    NotificationCompat.Builder nb = new NotificationCompat.Builder(this, null)
        .setSmallIcon(R.drawable.ic_cellsignal)
        .setTicker(getString(R.string.notif_ticker))
        .setContentTitle(getString(R.string.notif_title))
        .setContentIntent(contentIntent);
    if (notText != null) nb.setContentText(notText);

    return nb.build();
  }

  private void updateSvcNotificationText(String notText) {
    Notification not = buildSvcForegroundNotification(notText);
    NotificationManagerCompat.from(this).notify(SVC_NOTIF_ID, not);
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
    Notification notification = buildSvcForegroundNotification(null);
    startForeground(SVC_NOTIF_ID, notification);

    if (mDb == null || !mDb.isOpen()) {
      SQLiteOpenHelper dbhlp = new DBHelper(this);
      mDb = dbhlp.getWritableDatabase();
    }

    // Delete previously posted records from local database
    new Thread(new Runnable() {
      @Override
      public void run() {
        DBUtils.deletePreviouslyPostedRecords(mDb, SvcLocationLogger.this);
      }
    }).start();

    // Get initial connectivity status
    set_isConnected(isConnectedToNetwork(mConnectivityManager));

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

    // Start listening to internet connectivity changes (for synchronization)
    IntentFilter flt = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
    flt.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
    registerReceiver(mConnectivityStatusReceiver, flt);

    // Start synchronization on a timer
    String sDefaultSyncMins = getString(R.string.pref_default_sync_interval);
    int iSyncMins = DBUtils.getIntFromStringPref(mSharedPrefs,
        getString(R.string.pref_key_sync_interval), sDefaultSyncMins);
    int iSyncMS = iSyncMins * MS_PER_S * S_PER_MIN;

    mSyncTimer.schedule(new TTSyncToFC(), iSyncMS, iSyncMS);
    Log.d(TAG, "Sync every " + iSyncMS + " ms");

    Log.d(TAG, "startLogging");
  }

  private void stopLogging() {
    // Stop synchronizing on a timer
    mSyncTimer.cancel();

    // Sync updates to feature class
    Thread syncThread = syncRecordsToFCNow();

    // Stop listening to internet connectivity changes
    unregisterReceiver(mConnectivityStatusReceiver);

    // Stop listening to location updates
    mFusedLocationClient.removeLocationUpdates(mLocationListener);

    mSharedPrefs.edit()
        .putBoolean(getString(R.string.pref_key_logging_enabled), false)
        .apply();

    // Close database resources
    // TODO Find a way to do the final sync without waiting here
    try {
      syncThread.join();

      if (mDb != null && mDb.isOpen()) {
        mDb.close();
        mDb = null;
      }
      // Cleanup
      SQLiteDatabase.releaseMemory();
    } catch (InterruptedException e) {
      Log.e(TAG, "Error waiting for sync thread", e);
    }

    // Stop the service since it's no longer needed
//    stopForeground(true);
    NotificationManagerCompat.from(this).cancel(SVC_NOTIF_ID);

    Log.d(TAG, "stopLogging");
  }


  private LocationCallback mLocationListener = new LocationCallback() {
    @Override
    public void onLocationResult(final LocationResult locationResult) {
//      Log.d(TAG, "onLocationResults");

      new Thread(new Runnable() {
        @Override
        public void run() {
          Location loc = locationResult.getLastLocation();
          if (loc != null) { // Save to DB
            int signalStrength = getCellSignalStrength();

            long datetime = loc.getTime();
            double x = loc.getLongitude();
            double y = loc.getLatitude();
            double z = loc.getAltitude();

            // Save reading to DB
            ContentValues vals = new ContentValues();
            vals.put(getString(R.string.columnname_signalstrength), signalStrength);
            vals.put(getString(R.string.columnname_date), datetime);
            vals.put(getString(R.string.columnname_longitude), x);
            vals.put(getString(R.string.columnname_latitude), y);
            if (loc.hasAltitude())
              vals.put(getString(R.string.columnname_altitude), z);
            vals.put(getString(R.string.columnname_osname), mOsName);
            vals.put(getString(R.string.columnname_osversion), mOsVersion);
            vals.put(getString(R.string.columnname_phonemodel), mPhoneModel);
            vals.put(getString(R.string.columnname_deviceid), mDeviceId);
            vals.put(getString(R.string.columnname_carrierid), mCellCarrierName);

            mDb.insert(getString(R.string.tablename_readings), null, vals);
            set_unsyncedRecordCount(get_unsyncedRecordCount() + 1);

            // Save to chart data
            ReadingDataPoint rdp = new ReadingDataPoint(
                signalStrength, loc.getTime(), x, y, z, mOsName, mOsVersion,
                mPhoneModel, mDeviceId, mCellCarrierName);
            mChartData.add(rdp);
            int overflow = mChartData.size() - MAX_CHART_DATA_POINTS;
            while (overflow-- > 0) {
              mChartData.remove(0);
            }
            Intent intent = new Intent();
            intent.setAction(getString(R.string.intent_category_chart_data_available));
            intent.putParcelableArrayListExtra(getString(R.string.extra_chart_data_list), mChartData);
            sendBroadcast(intent);
            Log.d(TAG, "Sent chart data broadcast");
          }
        }
      }).start();
    }
  };



  /** Signal strength value
   *  Note: Suppress missing permission error, as this check was done in the main activity
   *  before this service was started.
   * @return 0 through 4 (0 even if signal missing or invalid)
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

    return Math.max(0, strength);
  }


  /**
   * Reads unposted records from local database and posts them as inserts to an online feature service.
   */
  private Thread syncRecordsToFCNow() {
    Thread thread = new Thread(new Runnable() {
      @Override
      public void run() {
        Log.d(TAG, "Starting sync");

        Context ctx = SvcLocationLogger.this;

        try {
          // Don't even try if disconnected or there's nothing to sync
          if (!get_isConnected()) return;

          Cursor curUnposted = DBUtils.getUnpostedRecords(mDb, ctx);
          if (curUnposted.getCount() <= 0) return;

          // Post adds
          List<Long> recIdsSuccessfullyPosted;
          try {
            recIdsSuccessfullyPosted = DBUtils.postUnpostedRecords(curUnposted, ctx, mSharedPrefs);
          } catch (IOException e) {
            throw e;
          } catch (JSONException e) {
            throw new Exception("Error parsing add results", e);
          } finally {
            curUnposted.close();
          }


          // Update Sqlite to mark the records as posted
          DBUtils.updateNewlySentRecordsAsPosted(mDb, ctx, recIdsSuccessfullyPosted);
          long unposted = DBUtils.getUnpostedRecordsCount(mDb, ctx);
          set_unsyncedRecordCount(unposted);
          Log.d(TAG, unposted + " records are unposted");

          // Success; clear any error notification
          NotificationManagerCompat.from(ctx).cancel(ERR_NOTIF_ID);
        } catch (Exception e) {
          Log.e(TAG, "Error synchronizing readings", e);
          String msg = e.getLocalizedMessage();
          if (e.getCause() != null)
            msg += ":\n" + e.getCause().getLocalizedMessage();
          Notification notification = new NotificationCompat.Builder(ctx, null)
              .setContentTitle(getString(R.string.title_err_synchronization))
              .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(getString(R.string.msg_err_synchronization, msg)))
              .setSmallIcon(android.R.drawable.stat_sys_warning)
              .setGroup(getString(R.string.group_err_synchronization))
              .build();
          NotificationManagerCompat.from(ctx)
              .notify(ERR_NOTIF_ID, notification);
        }
      }
    });
    thread.start();
    return thread;
  }
  private class TTSyncToFC extends TimerTask {
    @Override
    public void run() {
      syncRecordsToFCNow();
    }
  }

  BroadcastReceiver mConnectivityStatusReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      // Do any disconnected/reconnected tasks in setter
      set_isConnected(isConnectedToNetwork(mConnectivityManager));
    }
  };

  private boolean isConnectedToNetwork(ConnectivityManager cm) {
    NetworkInfo ni = cm.getActiveNetworkInfo();
    return ni != null && ni.isConnected();
  }

  private boolean get_isConnected() {
    return this._isConnected.get();
  }

  private void set_isConnected(boolean isConnected) {
    if (this._isConnected.get() != isConnected) {
      this._isConnected.set(isConnected);
      // If reconnecting, synchronize
      if (isConnected) syncRecordsToFCNow();
    }
  }

  public long get_unsyncedRecordCount() {
    return _unsyncedRecordCount.get();
  }

  public void set_unsyncedRecordCount(long unsyncedRecordCount) {
    Log.d(TAG, "set_unsyncedRecordCount => " + unsyncedRecordCount);
    if (this._unsyncedRecordCount.get() == 0 || this._unsyncedRecordCount.get() % 5 == 0)
      updateSvcNotificationText(
          getString(R.string.notif_unsynced_records, this._unsyncedRecordCount.get()));

    this._unsyncedRecordCount.set(unsyncedRecordCount);
  }
}
