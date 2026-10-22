package com.liftlog.wear;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * What the tile and the watch-face complication show, read from the app's saved state:
 * the running workout (with the rest timer), or the day to train today.
 */
final class Glance {
    final boolean running;
    final String dayLabel;
    final int tone;
    final long startedAt, restEnd;
    final MainActivity.Day today;
    final int exercises;
    final String planName;
    final boolean synced;

    private Glance(Context c) {
        SharedPreferences p = c.getSharedPreferences("liftlog", Context.MODE_PRIVATE);
        JSONObject phone = parse(p.getString("phone", "{}"));
        JSONObject s = parse(p.getString("session", null));
        long st = s != null ? s.optLong("startedAt", 0) : 0, en = s != null ? s.optLong("endedAt", 0) : 0;
        running = s != null && st > 0 && en == 0;
        String id = s != null ? s.optString("day", "") : "";
        int bi = -1;
        for (int i = 0; i < MainActivity.DAYS.length; i++) if (MainActivity.DAYS[i].equals(id)) bi = i;
        dayLabel = s != null ? s.optString("dayLabel", bi >= 0 ? MainActivity.LABELS[bi] : "Workout") : "";
        tone = Math.abs(s != null ? s.optInt("tone", Math.max(bi, 0)) : 0) % MainActivity.COLORS.length;
        startedAt = st;
        long re = p.getLong("restEnd", 0);
        restEnd = running && re > System.currentTimeMillis() ? re : 0;
        List<MainActivity.Day> ds = MainActivity.daysOf(phone);
        today = MainActivity.suggestedOf(phone, ds);
        exercises = today != null ? MainActivity.orderOf(phone, today.id).size() : 0;
        planName = phone.optString("planName", "");
        synced = phone.optLong("ts", 0) > 0;
    }

    static Glance read(Context c) {
        return new Glance(c);
    }

    private static JSONObject parse(String s) {
        if (s == null) return null;
        try {
            return new JSONObject(s);
        } catch (JSONException e) {
            return null;
        }
    }
}
