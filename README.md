# SignalStrengthLogger-android
Detects and logs cell signal strength along with device location.


### What is it?
This app very simply gathers the device's location and the strength of its
cellular connection at specified time and space intervals,
and saves it to a Feature Service hosted on ArcGIS Online.
The app is very simple in design. It has a main settings page, including a switch to
    start and stop logging location and signal strength. The main activity also
    shows a line chart of the fifteen most recent signal reading values while logging.
    Logging is done in a service, so that it can run in the background even when
    other apps are running or when the display is turned off. A persistent
    notification tells the user that the logger is still active.
### Why build it?
The Nature Conservancy wanted to map cellular signal strength over the new
[Jack and Laura Dangermond Preserve](https://en.wikipedia.org/wiki/Jack_and_Laura_Dangermond_Preserve).
### Android concepts illustrated:
1. Foreground (high priority) logging service<p/>
    When logging is active, a persistent notification in the status bar signals
    that the logging service is running, and also how many locally saved records
    still need to be synchronized to the Feature Service.
    The logger is implemented as a foreground
    service, meaning it should almost never be shut down for lack of resources.
    Logging will continue while other apps are open and in the foreground, or even
    while the device display is turned off.
1. Obtaining cell signal strength value<p/>
    The `SvcLocationLogger.getCellSignalStrength()` method shows how to get the
    current cellular signal strength (values from 0-4). Other more detailed signal
    values are available, but the iOS platform can only read values of 0 to 4; to
    allow like comparisons between the two platforms, the Android version restricts itself
    to this value range.
1. Offline storage: saving to and updating a local Sqlite database<p/>
    This app is meant for use in remote areas where internet may not be available for
    much of the data collection exercise. It's important that it be able to save readings
    on the device and then send them to the Feature Service once internet is available.
    All readings are saved to a local SQLite database, and then synchronized on a schedule,
    or when internet connecivity is reestablished.
1. Using Google Play Services Fused Location Provider<p/>
    Google recommends using the Fused Location Provider when possible. This makes it
    easier to limit readings by time or by distance apart. Unfortunately, this means
    that only Google versions of Android will run this app (leaving out heavily
    customized versions of Android such as run on Kindle Fire devices). Basically, if
    your device has the Google Play Store app and runs Android 4.3 or higher, it should
    run this.
    The app provides settings for both time and distance between readings. Readings will be taken
    as **infrequently** as possible, according to the combination of those two settings.
    You can set either time or distance to zero, if you just want to limit by one of those
    two factors. It's best not to set both to zero.
1. Using ArcGIS REST API to save features to a Feature Service<p/>
    This app does not use the Esri runtime for Android. It only needs to write data
    out to a Feature Service, not to read or to map it. So it makes http POST operations
    directly against the REST endpoint for the Feature Service. You can change this
    service in the settings page; note that it should include the `addFeature` portion of
    the URL.
1. Generating tokens for updating secure feature services<p/>
    If you specify a username, password,
    and token generator URL, the app will generate and use a security token against
     your secured Feature Service.
1. Shared Preferences / PreferenceFragmentCompat<p/>
    All settings on the main activity are saved to persistent application settings,
    or `SharedPreferences`.
1. Backing up & restoring data with Google backup service<p/>
    All settings except username and password are backed up to your Google account automatically.
    That means that if you uninstall and then reinstall the app on the same device
    under the same Google account, all those settings should be automatically restored.
    This assumes you've allowed app data backup in the device settings. For implementation
    details, see the manifest settings and the `PrefsBackup.java` class.
1. Charting ongoing result counts<p/>
    While logging, the main activity shows a line chart of the fifteen most recent signal
    strength values. I used the [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) library
    to do this. The service periodically broadcasts a list of the last fifteen readings;
     if the main activity is up and running and hasn't been killed due to non-use, it will
     receive these and display them.
### Third-party components and licenses
I used a handful of third-party components to build this app; all are licensed under
[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0).
* [OKHTTP3](https://github.com/square/okhttp/tree/master/okhttp/src/main/java/okhttp3) (for POSTing
to the Feature Service)
* [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart)
* [Takisoft Android Support Preference v7 fix](https://github.com/Gericop/Android-Support-Preference-V7-Fix)
This fixes a strange incompatibility between Android's built-in styles and the
PreferenceFragmentCompat class that causes the app to crash when they're used together.