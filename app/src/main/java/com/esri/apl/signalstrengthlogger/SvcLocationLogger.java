package com.esri.apl.signalstrengthlogger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.esri.apl.signalstrengthlogger.data.SignalReading;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class SvcLocationLogger
    extends Service
    implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
                LocationListener {
    private static final int SVC_ID = 1;
    private static final int ACT_PI_REQ_CODE = 1;
    private static final String TAG = "SvcLocationLogger";
    private static final int MS_PER_S = 1000;
    private URL mUrlPostFeature = null;

    private AtomicBoolean mIsCurrentlyLogging = new AtomicBoolean(false);

    private SignalReading mSignalReading = null;
    private GoogleApiClient mGoogleApiClient = null;
    private SharedPreferences mSharedPrefs = null;

    public SvcLocationLogger() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // This is not a bound service
        return null;
    }

    @Override
    public void onDestroy() {
        stopLogging();
        super.onDestroy();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        try {
            mUrlPostFeature = new URL(getString(R.string.http_svc_url));
        } catch (Exception e) {
            Log.e(TAG, "Exception creating feature-creation URL.", e);
        }

        // This creates the API client, but doesn't call connect.
        mGoogleApiClient = buildGoogleApiClient();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startLogging();
        return super.onStartCommand(intent, flags, startId);
    }


    /**
     * Performs various tasks to start logging, including:
     * <ul>
     *     <li>Getting user-entered values to be logged along with location</li>
     *     <li>Making the service foreground</li>
     *     <li>Creating a persistent notification</li>
     *     <li>Connecting to the API client and listening to location updates</li>
     * </ul>
     */
    private void startLogging() {
        if (!mIsCurrentlyLogging.get()) {
            // Get preferences info
            mSignalReading = (new SignalReading(this))
                .setDeviceId(PreferenceManager.getDefaultSharedPreferences(this)
                    .getString(getString(R.string.pref_key_device_id), null));


            // Create notification and bring to foreground
            Intent intent = new Intent(this, ActSettings.class);
            PendingIntent contentIntent =
                PendingIntent.getActivity(this, ACT_PI_REQ_CODE, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationCompat.Builder nb = new NotificationCompat.Builder(this)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setTicker(getString(R.string.notif_ticker))
                .setContentTitle(getString(R.string.notif_title))
//                .setContentText(getString(R.string.notif_content_text))
                .setContentIntent(contentIntent);

            startForeground(SVC_ID, nb.build());

            // Connect; then set up location listening in onConnected()
            mGoogleApiClient.connect();

            mIsCurrentlyLogging.set(true);
        }
    }

    private void stopLogging() {
        if (mIsCurrentlyLogging.get()) {
            // Stop listening to location updates
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();

            // Stop the service since it's no longer needed
            stopForeground(true);

            mIsCurrentlyLogging.set(false);
        }
    }

    protected synchronized GoogleApiClient buildGoogleApiClient() {
        return new GoogleApiClient.Builder(this)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .addApi(LocationServices.API)
            .build();
    }

    @Override
    public void onConnected(Bundle bundle) {
//        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        // Set up and start listening to location updates
        String sIntDef = getString(R.string.pref_default_tracking_interval);
        String sIntMin = getString(R.string.pref_min_tracking_interval);
        int iIntMin = Integer.parseInt(sIntMin);
        int iInt = Integer.parseInt(mSharedPrefs.getString(getString(R.string.pref_key_tracking_interval), sIntDef));
        LocationRequest locReq = (new LocationRequest())
            .setInterval(iInt * MS_PER_S)
            .setFastestInterval(iIntMin * MS_PER_S)
            .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(
            mGoogleApiClient, locReq, this);
    }

    @Override
    public void onConnectionSuspended(int i) {}

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {}

    public void onLocationChanged(Location location) {
        // Save info to feature service
        mSignalReading.setLocation(location);
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
        })).start();
    }

    /** Signal strength value
     *  Note: Suppress missing permission error, as this check was done in the main activity
     *  before this service was started.
     * @return 0 through 4
     */
    // TODO Detect and handle transition to/from airplane mode
    @SuppressLint("MissingPermission")
    private int getCellSignalStrength() {
        int strength = Integer.MIN_VALUE;
        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        List<CellInfo> cellInfos = tm.getAllCellInfo();   //This will give info of all sims present inside your mobile
        if(cellInfos!=null) {
            for (int i = 0; i < cellInfos.size(); i++) {
                if (cellInfos.get(i).isRegistered()) {
                    if (cellInfos.get(i) instanceof CellInfoWcdma) {
                        CellInfoWcdma cellInfoWcdma = (CellInfoWcdma) tm.getAllCellInfo().get(0);
                        CellSignalStrengthWcdma cellSignalStrengthWcdma = cellInfoWcdma.getCellSignalStrength();
                        strength = cellSignalStrengthWcdma.getLevel();
                    } else if (cellInfos.get(i) instanceof CellInfoLte) {
                        CellInfoLte cellInfoLte = (CellInfoLte) tm.getAllCellInfo().get(0);
                        CellSignalStrengthLte cellSignalStrengthLte = cellInfoLte.getCellSignalStrength();
                        strength = cellSignalStrengthLte.getLevel();
                    } else if (cellInfos.get(i) instanceof CellInfoGsm) {
                        CellInfoGsm cellInfogsm = (CellInfoGsm) tm.getAllCellInfo().get(0);
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
}
