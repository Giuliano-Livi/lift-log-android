package com.liftlog.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.AtomicFile;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Where Lift Log keeps your data on the phone.
 *
 * 1. liftlog-data.json in the app's own files: written atomically on every save, kept when a new
 *    version is installed over the app.
 * 2. A backup copy outside the app, kept up to date a few seconds after each save. By default it is
 *    Download/LiftLog/LiftLog-backup.json (created through MediaStore, no permission needed); you can
 *    pick another place, such as Google Drive. That copy survives uninstalling the app, and
 *    "Restore from backup file" reads it back.
 */
final class DataStore {
    static final String BACKUP_NAME = "LiftLog-backup.json";
    private static final String FILE = "liftlog-data.json";
    private static final long BACKUP_DELAY_MS = 4000;
    private static final int MAX_BYTES = 30 * 1024 * 1024;

    private static final Object STORE_LOCK = new Object();
    private static final Object BACKUP_LOCK = new Object();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Context app;
    private static volatile boolean pending;
    private static final Runnable BACKUP_TASK = () -> IO.execute(() -> {
        pending = false;
        writeBackup(false);
    });

    private DataStore() {}

    static void init(Context c) {
        app = c.getApplicationContext();
    }

    private static SharedPreferences prefs() {
        return app.getSharedPreferences("backup", Context.MODE_PRIVATE);
    }

    private static AtomicFile file() {
        return new AtomicFile(new File(app.getFilesDir(), FILE));
    }

    // ------------------------------------------------------------- app store

    static String load() {
        synchronized (STORE_LOCK) {
            try {
                return new String(file().readFully(), StandardCharsets.UTF_8);
            } catch (FileNotFoundException e) {
                return "";
            } catch (IOException e) {
                return "";
            }
        }
    }

    static boolean save(String json) {
        if (json == null || json.isEmpty()) return false;
        synchronized (STORE_LOCK) {
            AtomicFile f = file();
            FileOutputStream out = null;
            try {
                out = f.startWrite();
                out.write(json.getBytes(StandardCharsets.UTF_8));
                f.finishWrite(out);
            } catch (IOException e) {
                if (out != null) f.failWrite(out);
                return false;
            }
        }
        pending = true;
        MAIN.removeCallbacks(BACKUP_TASK);
        MAIN.postDelayed(BACKUP_TASK, BACKUP_DELAY_MS);
        return true;
    }

    /** Write a pending backup right away (the app is going to the background). */
    static void flush() {
        if (!pending) return;
        MAIN.removeCallbacks(BACKUP_TASK);
        BACKUP_TASK.run();
    }

    static boolean hasData(String json) {
        if (json == null || json.isEmpty()) return false;
        try {
            JSONObject o = new JSONObject(json);
            JSONObject logs = o.optJSONObject("logs"), body = o.optJSONObject("body");
            return (logs != null && logs.length() > 0) || (body != null && body.length() > 0);
        } catch (JSONException e) {
            return false;
        }
    }

    // ------------------------------------------------------------- backup file

    private static Uri target() {
        String s = prefs().getString("uri", null);
        return s != null ? Uri.parse(s) : null;
    }

    static String status() {
        SharedPreferences p = prefs();
        try {
            JSONObject o = new JSONObject();
            o.put("where", p.getString("uri", null) != null ? p.getString("where", BACKUP_NAME) : JSONObject.NULL);
            o.put("kind", p.getString("kind", "auto"));
            o.put("lastAt", p.getLong("lastAt", 0));
            o.put("error", p.getString("error", null) != null ? p.getString("error", "") : JSONObject.NULL);
            o.put("canAuto", Build.VERSION.SDK_INT >= 29);
            return o.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    private static Uri createAuto() {
        if (Build.VERSION.SDK_INT < 29) return null;
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, BACKUP_NAME);
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/LiftLog");
        return app.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
    }

    private static void writeTo(Uri uri, byte[] bytes) throws IOException {
        ContentResolver cr = app.getContentResolver();
        OutputStream os = null;
        try {
            os = cr.openOutputStream(uri, "wt");
        } catch (Exception e) {
            os = null;
        }
        if (os == null) os = cr.openOutputStream(uri, "rwt");
        if (os == null) throw new IOException("the file could not be opened");
        try (OutputStream o = os) {
            o.write(bytes);
            o.flush();
        }
    }

    private static String readFrom(Uri uri) throws IOException {
        try (InputStream in = app.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("the file could not be opened");
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[64 * 1024];
            int n;
            while ((n = in.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
                if (buf.size() > MAX_BYTES) throw new IOException("the file is too large to be a Lift Log backup");
            }
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String message(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    /**
     * Copy the app's data to the backup file. Never writes when there is no data yet, so a fresh
     * install can never overwrite a real backup with an empty one. Returns the backup status.
     */
    static String writeBackup(boolean userAsked) {
        synchronized (BACKUP_LOCK) {
            String json = load();
            SharedPreferences p = prefs();
            if (!hasData(json)) {
                if (userAsked) p.edit().putString("error", "Nothing to back up yet. Log a session first.").apply();
                return status();
            }
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            Uri uri = target();
            boolean auto = !"chosen".equals(p.getString("kind", "auto"));
            for (int attempt = 0; attempt < 2; attempt++) {
                if (uri == null) {
                    try {
                        uri = createAuto();
                    } catch (Exception e) {
                        uri = null;
                    }
                    if (uri == null) {
                        p.edit().putString("error", Build.VERSION.SDK_INT < 29
                            ? "Choose where to keep the backup file."
                            : "Could not create the backup file in Download/LiftLog.").apply();
                        return status();
                    }
                    p.edit().putString("uri", uri.toString()).putString("kind", "auto")
                        .putString("where", "Download/LiftLog/" + BACKUP_NAME).apply();
                    auto = true;
                }
                try {
                    writeTo(uri, bytes);
                    p.edit().putLong("lastAt", System.currentTimeMillis()).remove("error").apply();
                    return status();
                } catch (Exception e) {
                    if (auto && attempt == 0) {
                        // the automatic file was deleted or moved: create it again
                        p.edit().remove("uri").apply();
                        uri = null;
                        continue;
                    }
                    p.edit().putString("error", "Could not write the backup file: " + message(e)).apply();
                    return status();
                }
            }
            return status();
        }
    }

    /** Content of the backup file this install already knows, or "" (used when the app starts empty). */
    static String readKnown() {
        synchronized (BACKUP_LOCK) {
            Uri uri = target();
            if (uri == null) return "";
            try {
                return readFrom(uri);
            } catch (Exception e) {
                return "";
            }
        }
    }

    static String read(Uri uri) throws IOException {
        return readFrom(uri);
    }

    /** From now on keep the backup in this file (one you picked or created). */
    static void useFile(Uri uri, String how) {
        String name = BACKUP_NAME;
        try (Cursor c = app.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && c.getString(0) != null) name = c.getString(0);
        } catch (Exception ignored) {
            // keep the default name
        }
        prefs().edit().putString("uri", uri.toString()).putString("kind", "chosen")
            .putString("where", name + " (" + how + ")").remove("error").apply();
    }

    static void runInBackground(Runnable r) {
        IO.execute(r);
    }
}
