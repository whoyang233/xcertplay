# Called by the local shell through app_process, not from an Android component.
-keep class com.shilapi.xcertplay.network.WifiScanPauseMain {
    public static void main(java.lang.String[]);
}
