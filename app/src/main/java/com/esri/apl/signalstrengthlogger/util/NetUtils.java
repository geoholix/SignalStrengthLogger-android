package com.esri.apl.signalstrengthlogger.util;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class NetUtils {
  public static boolean isConnectedToNetwork(ConnectivityManager cm) {
    NetworkInfo ni = cm.getActiveNetworkInfo();
    return ni != null && ni.isConnected();
  }
}
