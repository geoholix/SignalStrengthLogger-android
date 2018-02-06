package com.esri.apl.signalstrengthlogger.util;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * Created by mark4238 on 2/5/2018.
 */

public class NetUtils {
  public static boolean isConnectedToNetwork(ConnectivityManager cm) {
    NetworkInfo ni = cm.getActiveNetworkInfo();
    return ni != null && ni.isConnected();
  }
}
