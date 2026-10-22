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
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int REQ_FILE = 41;
    private static final int REQ_HEALTH = 42;
    private static final int REQ_OCR = 43;
    private static final int REQ_RESTORE = 44;
    private static final int REQ_BACKUP_TO = 45;
    private static final int REQ_NUTRITION = 46;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private int healthDays = 400;
    private int nutritionDays = 120;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DataStore.init(this);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient() {
            // web links (JEFIT, the drawings' licence) open in the browser, not inside the app
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                String sc = u.getScheme();
                if (!"http".equals(sc) && !"https".equals(sc)) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (ActivityNotFoundException e) {
                    // no browser on the phone: stay in the app
                }
                return true;
            }
        });
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
        // and copy the latest data to the backup file a moment later
        web.postDelayed(DataStore::flush, 700);
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
        } else if (requestCode == REQ_RESTORE) {
            Uri uri = resultCode == RESULT_OK && data != null ? data.getData() : null;
            if (uri == null) {
                deliverRestore("{\"status\":\"cancelled\"}");
                return;
            }
            boolean canWrite = keepAccess(uri);
            DataStore.runInBackground(() -> {
                try {
                    String text = DataStore.read(uri);
                    // keep backing up into the file you restored from, so there is only one backup file
                    if (canWrite && DataStore.hasData(text)) DataStore.useFile(uri, "the file you restored from");
                    deliverRestore(text);
                } catch (Exception e) {
                    deliverRestore(errorJson(e));
                }
            });
        } else if (requestCode == REQ_BACKUP_TO) {
            Uri uri = resultCode == RESULT_OK && data != null ? data.getData() : null;
            if (uri == null) return;
            if (!keepAccess(uri)) {
                deliverBackup("{\"error\":\"Lift Log was not allowed to write to that place. Try another location.\"}");
                return;
            }
            DataStore.useFile(uri, "the place you chose");
            DataStore.runInBackground(() -> deliverBackup(DataStore.writeBackup(true)));
        } else if (requestCode == REQ_HEALTH) {
            // Read again: if permission was granted the data comes back, otherwise the page shows why not.
            HealthBridge.read(this, healthDays, this::deliverHealth);
        } else if (requestCode == REQ_NUTRITION) {
            HealthBridge.readNutrition(this, nutritionDays, this::deliverNutrition);
        }
    }

    /** Hand a Health Connect result to the page as a JSON string. */
    private void deliverHealth(String json) {
        runOnUiThread(() -> web.evaluateJavascript(
            "window.liftlogHealth && window.liftlogHealth(" + JSONObject.quote(json) + ")", null));
    }

    private void deliverNutrition(String json) {
        runOnUiThread(() -> web.evaluateJavascript(
            "window.liftlogNutrition && window.liftlogNutrition(" + JSONObject.quote(json) + ")", null));
    }

    /** Keep read and write access to a file you picked, across restarts. Returns true if writing is allowed. */
    private boolean keepAccess(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            return true;
        } catch (Exception e) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
                // reading still works for this session
            }
            return false;
        }
    }

    private static String errorJson(Exception e) {
        try {
            return new JSONObject().put("status", "error").put("message", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()).toString();
        } catch (JSONException x) {
            return "{\"status\":\"error\"}";
        }
    }

    private void deliverRestore(String text) {
        runOnUiThread(() -> web.evaluateJavascript(
            "window.liftlogRestore && window.liftlogRestore(" + JSONObject.quote(text) + ")", null));
    }

    private void deliverBackup(String json) {
        runOnUiThread(() -> web.evaluateJavascript(
            "window.liftlogBackupStatus && window.liftlogBackupStatus(" + JSONObject.quote(json) + ")", null));
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

        /** Read nutrition (calories and macros) from Health Connect; with prompt=true, ask for permission if needed. */
        @JavascriptInterface
        public void healthImportNutrition(final int days, final boolean prompt) {
            runOnUiThread(() -> {
                String st = HealthBridge.status(MainActivity.this);
                if (!"available".equals(st)) {
                    deliverNutrition("{\"status\":\"" + st + "\"}");
                    return;
                }
                nutritionDays = days;
                HealthBridge.readNutrition(MainActivity.this, days, json -> {
                    if (prompt && "no_permission".equals(status(json))) {
                        runOnUiThread(() -> {
                            try {
                                startActivityForResult(HealthBridge.nutritionPermissionIntent(MainActivity.this), REQ_NUTRITION);
                            } catch (ActivityNotFoundException e) {
                                deliverNutrition("{\"status\":\"unavailable\"}");
                            }
                        });
                    } else {
                        deliverNutrition(json);
                    }
                });
            });
        }

        // ---- data store and backup file ----

        @JavascriptInterface
        public String storeLoad() {
            return DataStore.load();
        }

        @JavascriptInterface
        public boolean storeSave(String json) {
            return DataStore.save(json);
        }

        @JavascriptInterface
        public String backupStatus() {
            return DataStore.status();
        }

        @JavascriptInterface
        public String backupReadKnown() {
            return DataStore.readKnown();
        }

        @JavascriptInterface
        public void backupNow() {
            DataStore.runInBackground(() -> deliverBackup(DataStore.writeBackup(true)));
        }

        @JavascriptInterface
        public void backupRestore() {
            runOnUiThread(() -> {
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                try {
                    startActivityForResult(pick, REQ_RESTORE);
                } catch (ActivityNotFoundException e) {
                    deliverRestore("{\"status\":\"error\",\"message\":\"no app to pick files\"}");
                }
            });
        }

        @JavascriptInterface
        public void backupChoose() {
            runOnUiThread(() -> {
                Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                create.addCategory(Intent.CATEGORY_OPENABLE);
                create.setType("application/json");
                create.putExtra(Intent.EXTRA_TITLE, DataStore.BACKUP_NAME);
                create.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                try {
                    startActivityForResult(create, REQ_BACKUP_TO);
                } catch (ActivityNotFoundException e) {
                    deliverBackup("{\"error\":\"No app on this phone can choose a location.\"}");
                }
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
                    startActivityForResult(Intent.createChooser(pick, "Choose an image"), REQ_OCR);
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
