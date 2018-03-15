package com.esri.apl.signalstrengthlogger.data;

import android.os.Parcel;
import android.os.Parcelable;

// TODO Change this if you want to change the database schema and what's being recorded
/**
 * Class representing a single signal strength reading.
 * Parcelable for sending from service to main activity for charting.
 */
public class ReadingDataPoint implements Parcelable {
  private float _signalStrength;
  private long _datetime;
  private double _x;
  private double _y;
  private double _z;
  private String _osName;
  private String _osVersion;
  private String _phoneModel;
  private String _deviceId;
  private String _carrierId;

  public ReadingDataPoint(float signalStrength, long datetime, double x, double y, double z,
                          String osName, String osVersion, String phoneModel,
                          String deviceId, String carrierId) {
    this._signalStrength = signalStrength;
    this._datetime = datetime;
    this._x = x; this._y = y; this._z = z;
    this._osName = osName;
    this._osVersion = osVersion;
    this._phoneModel = phoneModel;
    this._deviceId = deviceId;
    this._carrierId = carrierId;
  }

  private ReadingDataPoint(Parcel in) {
    _signalStrength = in.readFloat();
    _datetime = in.readLong();
    _x = in.readDouble();
    _y = in.readDouble();
    _z = in.readDouble();
    _osName = in.readString();
    _osVersion = in.readString();
    _phoneModel = in.readString();
    _deviceId = in.readString();
    _carrierId = in.readString();
  }
  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel parcel, int flags) {
    parcel.writeFloat(_signalStrength);
    parcel.writeLong(_datetime);
    parcel.writeDouble(_x);
    parcel.writeDouble(_y);
    parcel.writeDouble(_z);
    parcel.writeString(_osName);
    parcel.writeString(_osVersion);
    parcel.writeString(_phoneModel);
    parcel.writeString(_deviceId);
    parcel.writeString(_carrierId);
  }

  public static final Parcelable.Creator<ReadingDataPoint> CREATOR =
      new Parcelable.Creator<ReadingDataPoint>() {
        public ReadingDataPoint createFromParcel(Parcel in) {
          return new ReadingDataPoint(in);
        }

        @Override
        public ReadingDataPoint[] newArray(int i) {
          return new ReadingDataPoint[i];
        }
      };

  public float get_signalStrength() {
    return _signalStrength;
  }

  public void set_signalStrength(float _signalStrength) {
    this._signalStrength = _signalStrength;
  }

  public long get_datetime() {
    return _datetime;
  }

  public void set_datetime(long _datetime) {
    this._datetime = _datetime;
  }

  public double get_x() {
    return _x;
  }

  public void set_x(double _x) {
    this._x = _x;
  }

  public double get_y() {
    return _y;
  }

  public void set_y(double _y) {
    this._y = _y;
  }

  public double get_z() {
    return _z;
  }

  public void set_z(double _z) {
    this._z = _z;
  }

  public String get_osName() {
    return _osName;
  }

  public void set_osName(String _osName) {
    this._osName = _osName;
  }

  public String get_osVersion() {
    return _osVersion;
  }

  public void set_osVersion(String _osVersion) {
    this._osVersion = _osVersion;
  }

  public String get_phoneModel() {
    return _phoneModel;
  }

  public void set_phoneModel(String _phoneModel) {
    this._phoneModel = _phoneModel;
  }

  public String get_deviceId() {
    return _deviceId;
  }

  public void set_deviceId(String _deviceId) {
    this._deviceId = _deviceId;
  }

  public String get_carrierId() {
    return _carrierId;
  }

  public void set_carrierId(String _carrierId) {
    this._carrierId = _carrierId;
  }
}
