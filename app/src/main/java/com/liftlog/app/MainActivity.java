package com.liftlog.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int REQ_FILE = 41;
    private static final int REQ_HEALTH = 42;
    private static final int REQ_OCR = 43;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private int healthDays = 400;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(pick, "Choose a CSV file"), REQ_FILE);
                    return true;
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    return false;
                }
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        WatchSync.active = this;
        notifyWatchOps();
    }

    /** Tell the page that sets logged on the watch are waiting. */
    void notifyWatchOps() {
        runOnUiThread(() -> web.evaluateJavascript("window.dispatchEvent(new Event('liftlogops'))", null));
    }

    @Override
    protected void onPause() {
        if (WatchSync.active == this) WatchSync.active = null;
        // Let the page save anything still pending before Android may stop the app.
        web.evaluateJavascript("document.dispatchEvent(new Event('visibilitychange'))", null);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCallback = null;
            }
        } else if (requestCode == REQ_OCR) {
            Uri uri = resultCode == RESULT_OK && data != null ? data.getData() : null;
            if (uri == null) deliverOcr("{\"status\":\"cancelled\"}");
            else ReportOcr.read(this, uri, this::deliverOcr);
        } else if (requestCode == REQ_HEALTH) {
            // Read again: if permission was granted the data comes back, otherwise the page shows why not.
            HealthBridge.read(this, healthDays, this::deliverHealth);
        }
    }

    /** Hand a Health Connect result to the page as a JSON string. */
    private void deliverHealth(String json) {
        runOnUiThread(() -> web.evaluateJavascript(
            "window.liftlogHealth && window.liftlogHealth(" + JSONObject.quote(json) + ")", null));
    }

    /** Hand the text read from a report image to the page as a JSON string. */
    private void deliverOcr(String json) {
        runOnUiThread(() -> web.evaluateJavascript(
            "window.liftlogOcr && window.liftlogOcr(" + JSONObject.quote(json) + ")", null));
    }

    private static String status(String json) {
        try {
            return new JSONObject(json).optString("status");
        } catch (JSONException e) {
            return "";
        }
    }

    private class Bridge {
        @JavascriptInterface
        public void keepAwake(final boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        public void vibrate(int ms) {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (ms <= 0 || v == null || !v.hasVibrator()) return;
            long[] pattern = { 0, 250, 120, 250 };
            v.vibrate(VibrationEffect.createWaveform(pattern, -1));
        }

        @JavascriptInterface
        public String takeWatchOps() {
            return WatchSync.takeOps(MainActivity.this);
        }

        @JavascriptInterface
        public void syncWatch(String json) {
            WatchSync.pushState(MainActivity.this, json);
        }

        @JavascriptInterface
        public String healthAvailable() {
            return HealthBridge.status(MainActivity.this);
        }

        /** Read weight and body fat from Health Connect; with prompt=true, ask for permission if needed. */
        @JavascriptInterface
        public void healthImport(final int days, final boolean prompt) {
            runOnUiThread(() -> {
                String st = HealthBridge.status(MainActivity.this);
                if (!"available".equals(st)) {
                    if (prompt && "update_required".equals(st)) {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding")));
                        } catch (ActivityNotFoundException ignored) {
                            // no Play Store: the page explains what to do
                        }
                    }
                    deliverHealth("{\"status\":\"" + st + "\"}");
                    return;
                }
                healthDays = days;
                HealthBridge.read(MainActivity.this, days, json -> {
                    if (prompt && "no_permission".equals(status(json))) {
                        runOnUiThread(() -> {
                            try {
                                startActivityForResult(HealthBridge.permissionIntent(MainActivity.this), REQ_HEALTH);
                            } catch (ActivityNotFoundException e) {
                                deliverHealth("{\"status\":\"unavailable\"}");
                            }
                        });
                    } else {
                        deliverHealth(json);
                    }
                });
            });
        }

        /** Let the user pick a body report image and read its text on the phone. */
        @JavascriptInterface
        public void ocrPickImage() {
            runOnUiThread(() -> {
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("image/*");
                try {
                    startActivityForResult(Intent.createChooser(pick, "Choose the report image"), REQ_OCR);
                } catch (ActivityNotFoundException e) {
                    deliverOcr("{\"status\":\"error\",\"message\":\"no app to pick images\"}");
                }
            });
        }

        @JavascriptInterface
        public void shareText(final String filename, final String text) {
            runOnUiThread(() -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, filename);
                send.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(send, "Export Lift Log"));
            });
        }
    }
}
