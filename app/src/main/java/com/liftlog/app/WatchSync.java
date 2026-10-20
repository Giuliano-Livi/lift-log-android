package com.liftlog.app;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Phone side of the watch sync: queue of watch actions + publishing state to the watch. */
final class WatchSync {
    static volatile MainActivity active;

    private WatchSync() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("watch", Context.MODE_PRIVATE);
    }

    static synchronized void addOp(Context c, String json) {
        try {
            JSONArray q = new JSONArray(prefs(c).getString("ops", "[]"));
            q.put(new JSONObject(json));
            prefs(c).edit().putString("ops", q.toString()).commit();
        } catch (JSONException ignored) {
            // malformed action from the watch: drop it
        }
    }

    static synchronized String takeOps(Context c) {
        String q = prefs(c).getString("ops", "[]");
        prefs(c).edit().putString("ops", "[]").commit();
        return q;
    }

    static void pushState(Context c, String json) {
        PutDataMapRequest req = PutDataMapRequest.create("/liftlog/state");
        req.getDataMap().putString("json", json);
        req.getDataMap().putLong("ts", System.currentTimeMillis());
        PutDataRequest put = req.asPutDataRequest();
        put.setUrgent();
        Wearable.getDataClient(c.getApplicationContext()).putDataItem(put);
    }
}
