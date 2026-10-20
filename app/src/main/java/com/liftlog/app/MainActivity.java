package com.liftlog.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView web;

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
        web.setWebChromeClient(new WebChromeClient());
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
