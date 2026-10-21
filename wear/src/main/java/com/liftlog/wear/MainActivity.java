package com.liftlog.wear;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Lift Log on the watch: pick a day, log kg and reps per set, workout clock, rest timer and heart rate.
 * Sets are sent to the phone app as Data Layer items under /liftlog/op/...; the phone publishes its state
 * (plan with your swaps, previous sets, suggested weights, records, rest times, today's sessions) under /liftlog/state.
 */
public class MainActivity extends Activity implements DataClient.OnDataChangedListener, SensorEventListener {

    static final class Ex {
        final String id, name;
        final int sets, lo, hi, rest;
        final boolean bw;
        final double inc;

        Ex(String id, String name, int sets, int lo, int hi, int rest, boolean bw, double inc) {
            this.id = id; this.name = name; this.sets = sets; this.lo = lo; this.hi = hi; this.rest = rest; this.bw = bw; this.inc = inc;
        }
    }

    static final String[] DAYS = {"push", "pull", "legs", "upper"};
    static final String[] LABELS = {"Push", "Pull", "Legs", "Upper"};
    static final int[] COLORS = {0xFFEF5A4D, 0xFF5B8FE0, 0xFFF0B90B, 0xFF3FB868};
    static final int ON_COLOR = 0xFF0F1217;
    static final int GREY = 0xFF262B33;
    static final int MUTED = 0xFF9AA3B0;
    static final int WHITE = 0xFFECEFF4;
    static final int GOOD = 0xFF3FB868;
    static final int REQ_BODY_SENSORS = 7;

    /** Built-in plan, used until the phone app has sent its own (which includes your swaps). */
    static final Ex[][] PLAN = {
        {
            new Ex("incline-bb", "Barbell Incline Bench Press", 4, 5, 8, 180, false, 2.5),
            new Ex("machine-bench", "Machine Bench Press", 3, 8, 10, 120, false, 2.5),
            new Ex("machine-shoulder", "Machine Shoulder Press", 3, 8, 10, 120, false, 2.5),
            new Ex("cable-1arm-lateral", "Cable One-Arm Lateral Raise", 3, 12, 15, 75, false, 2.5),
            new Ex("oh-tricep-ext", "Overhead Tricep Extension", 3, 10, 12, 90, false, 2.5),
            new Ex("cable-crunch", "Cable Crunch", 3, 10, 15, 60, false, 2.5)
        },
        {
            new Ex("pull-up", "Pull-Up", 4, 6, 10, 180, true, 2.5),
            new Ex("cs-row", "Chest-Supported Row", 3, 6, 10, 150, false, 2.5),
            new Ex("lat-rev", "Lat Pulldown (Reverse Grip)", 3, 8, 12, 120, false, 2.5),
            new Ex("prone-lateral", "Prone Lateral Raise", 3, 12, 15, 75, false, 1),
            new Ex("bb-curl", "Barbell Curl", 3, 8, 10, 90, false, 2.5)
        },
        {
            new Ex("squat", "Barbell Squat", 3, 5, 8, 180, false, 5),
            new Ex("rdl", "Romanian Deadlift", 3, 6, 10, 180, false, 5),
            new Ex("hack", "Hack Squat", 3, 8, 12, 150, false, 5),
            new Ex("leg-curl", "Seated Leg Curl", 4, 8, 12, 90, false, 2.5),
            new Ex("leg-ext", "Leg Extension", 3, 12, 15, 90, false, 2.5),
            new Ex("seated-calf", "Seated Calf Raise", 3, 10, 15, 60, false, 5),
            new Ex("ab-wheel", "Ab Wheel Rollout", 3, 8, 12, 60, true, 2.5)
        },
        {
            new Ex("lat-pd", "Lat Pulldown", 3, 10, 12, 120, false, 2.5),
            new Ex("cable-row", "Seated Cable Row", 3, 10, 12, 120, false, 2.5),
            new Ex("machine-fly", "Machine Fly", 3, 10, 15, 90, false, 2.5),
            new Ex("dip", "Dip", 3, 8, 12, 120, true, 2.5),
            new Ex("cable-lateral", "Cable Lateral Raise", 3, 12, 15, 75, false, 2.5),
            new Ex("pushdown", "Tricep Pushdown (Rope)", 3, 10, 15, 75, false, 2.5),
            new Ex("preacher", "Preacher Curl Machine", 3, 10, 12, 90, false, 2.5),
            new Ex("smith-calf", "Single-Leg Calf Raise", 3, 12, 15, 60, false, 2.5),
            new Ex("hanging-leg", "Hanging Leg Raise", 3, 10, 15, 60, true, 2.5)
        }
    };
    static final Map<String, Ex> BUILTIN = new HashMap<>();
    static {
        for (Ex[] day : PLAN) for (Ex e : day) BUILTIN.put(e.id, e);
    }

    private SharedPreferences prefs;
    private JSONObject phone = new JSONObject();

    // current session
    private String sid, date;
    private int day;
    private long startedAt, endedAt;
    private List<String> order = new ArrayList<>();
    private Map<String, List<double[]>> entries = new HashMap<>();
    private double hrSum;
    private long hrCount;
    private int hrMax;

    // screen state
    private String screen = "home";
    private int exIdx;
    private String editKey;
    private double curW;
    private int curR;
    private long restEnd;
    private String recordText;
    private boolean confirmFinish;
    private TextView statusTv, restTv, weightTv, repsTv;
    private float density;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // heart rate
    private SensorManager sensors;
    private Sensor hrSensor;
    private boolean hrOn;
    private int hrNow;
    private long hrAt, hrSentAt, hrSavedAt;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            onTick();
            handler.postDelayed(this, 250);
        }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("liftlog", Context.MODE_PRIVATE);
        sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        hrSensor = sensors != null ? sensors.getDefaultSensor(Sensor.TYPE_HEART_RATE) : null;
        load();
        if (isRunning()) {
            screen = "workout";
            exIdx = firstOpenExercise();
            startHr(false);
        }
        Wearable.getDataClient(this).addListener(this);
        fetchPhoneState();
        handler.post(tick);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchPhoneState();
    }

    @Override
    protected void onDestroy() {
        Wearable.getDataClient(this).removeListener(this);
        handler.removeCallbacks(tick);
        stopHr();
        saveSession();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (!"home".equals(screen)) {
            restEnd = 0;
            screen = "home";
            render();
        } else {
            super.onBackPressed();
        }
    }

    // ---------------------------------------------------------------- heart rate

    private boolean hrGranted() {
        return checkSelfPermission(Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED;
    }

    /** Start reading the heart-rate sensor while a workout runs; asks for permission once if allowed to. */
    private void startHr(boolean ask) {
        if (hrSensor == null || hrOn) return;
        if (!hrGranted()) {
            if (ask) requestPermissions(new String[]{Manifest.permission.BODY_SENSORS}, REQ_BODY_SENSORS);
            return;
        }
        hrOn = sensors.registerListener(this, hrSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void stopHr() {
        if (hrOn && sensors != null) sensors.unregisterListener(this);
        hrOn = false;
        hrNow = 0;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_BODY_SENSORS && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED && isRunning()) {
            startHr(false);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_HEART_RATE || event.values.length == 0) return;
        if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE || event.accuracy == SensorManager.SENSOR_STATUS_NO_CONTACT) return;
        int v = Math.round(event.values[0]);
        if (v < 35 || v > 230 || !isRunning()) return;
        hrNow = v;
        hrAt = System.currentTimeMillis();
        hrSum += v;
        hrCount++;
        if (v > hrMax) hrMax = v;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // nothing to do
    }

    private int hrAvg() {
        return hrCount > 0 ? (int) Math.round(hrSum / hrCount) : 0;
    }

    private String hrLabel() {
        if (!hrOn) return "";
        boolean fresh = hrNow > 0 && System.currentTimeMillis() - hrAt < 15000;
        return "  ♥ " + (fresh ? String.valueOf(hrNow) : "--");
    }

    // ---------------------------------------------------------------- phone sync

    private void fetchPhoneState() {
        Uri uri = new Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path("/liftlog/state").build();
        Wearable.getDataClient(this).getDataItems(uri).addOnSuccessListener(buffer -> {
            String best = null;
            long bestTs = -1;
            for (DataItem item : buffer) {
                DataMap dm = DataMapItem.fromDataItem(item).getDataMap();
                long ts = dm.getLong("ts", 0);
                if (ts > bestTs) {
                    bestTs = ts;
                    best = dm.getString("json");
                }
            }
            buffer.release();
            if (best != null && !best.equals(prefs.getString("phone", null))) applyPhone(best);
        });
    }

    @Override
    public void onDataChanged(DataEventBuffer events) {
        String json = null;
        for (DataEvent ev : events) {
            if (ev.getType() != DataEvent.TYPE_CHANGED) continue;
            DataItem item = ev.getDataItem();
            if ("/liftlog/state".equals(item.getUri().getPath())) {
                json = DataMapItem.fromDataItem(item).getDataMap().getString("json");
            }
        }
        if (json != null) applyPhone(json);
    }

    private void applyPhone(String json) {
        try {
            phone = new JSONObject(json);
        } catch (JSONException e) {
            return;
        }
        prefs.edit().putString("phone", json).apply();
        if (sid != null) {
            JSONObject ps = phoneSession(sid);
            if (ps != null) mergeFrom(ps);
        }
        if (!isRunning()) {
            JSONObject pr = phoneRunning();
            if (pr != null && !pr.optString("id").equals(sid)) {
                adopt(pr);
                screen = "workout";
                exIdx = firstOpenExercise();
                editKey = null;
                startHr(false);
            }
        }
        saveSession();
        if (!"rest".equals(screen)) render();
    }

    private JSONObject phoneSession(String id) {
        JSONArray a = phone.optJSONArray("sessions");
        if (a == null) return null;
        for (int i = 0; i < a.length(); i++) {
            JSONObject s = a.optJSONObject(i);
            if (s != null && id.equals(s.optString("id"))) return s;
        }
        return null;
    }

    private JSONObject phoneRunning() {
        JSONArray a = phone.optJSONArray("sessions");
        if (a == null) return null;
        JSONObject best = null;
        for (int i = 0; i < a.length(); i++) {
            JSONObject s = a.optJSONObject(i);
            if (s == null || dayIndex(s.optString("day")) < 0) continue;
            if (s.optLong("startedAt", 0) > 0 && s.optLong("endedAt", 0) == 0) {
                if (best == null || s.optLong("startedAt", 0) > best.optLong("startedAt", 0)) best = s;
            }
        }
        return best;
    }

    private void mergeFrom(JSONObject ps) {
        List<String> o = readIds(ps.optJSONArray("order"));
        if (!o.isEmpty()) order = o;
        JSONObject en = ps.optJSONObject("entries");
        if (en != null) {
            for (Iterator<String> it = en.keys(); it.hasNext(); ) {
                String k = it.next();
                List<double[]> theirs = readSets(en.optJSONArray(k));
                List<double[]> mine = entries.get(k);
                if (mine == null) {
                    mine = new ArrayList<>();
                    entries.put(k, mine);
                }
                for (int i = 0; i < theirs.size(); i++) {
                    double[] p = theirs.get(i);
                    if (p[1] <= 0) continue;
                    while (mine.size() <= i) mine.add(new double[]{0, 0});
                    mine.set(i, p);
                }
            }
        }
        long s = ps.optLong("startedAt", 0), e = ps.optLong("endedAt", 0);
        if (startedAt == 0 && s > 0) startedAt = s;
        if (e > 0 && endedAt == 0) endedAt = e;
    }

    private void adopt(JSONObject ps) {
        sid = ps.optString("id");
        day = Math.max(0, dayIndex(ps.optString("day")));
        date = ps.optString("date");
        startedAt = 0;
        endedAt = 0;
        entries = new HashMap<>();
        order = defaultOrder(day);
        hrSum = 0;
        hrCount = 0;
        hrMax = 0;
        mergeFrom(ps);
    }

    private void sendOp(String type, JSONObject o) {
        try {
            if (o == null) o = new JSONObject();
            o.put("t", type);
            o.put("sid", sid);
            o.put("day", DAYS[day]);
            o.put("date", date);
            o.put("at", "finish".equals(type) ? endedAt : startedAt);
            if (("finish".equals(type) || "hr".equals(type)) && hrCount > 0) {
                o.put("avg", hrAvg());
                o.put("max", hrMax);
            }
            PutDataMapRequest req = PutDataMapRequest.create("/liftlog/op/" + UUID.randomUUID());
            req.getDataMap().putString("json", o.toString());
            req.getDataMap().putLong("ts", System.currentTimeMillis());
            PutDataRequest put = req.asPutDataRequest();
            put.setUrgent();
            Wearable.getDataClient(this).putDataItem(put);
        } catch (JSONException ignored) {
            // cannot happen with these values
        }
    }

    // ---------------------------------------------------------------- local storage

    private void load() {
        try {
            phone = new JSONObject(prefs.getString("phone", "{}"));
        } catch (JSONException e) {
            phone = new JSONObject();
        }
        String s = prefs.getString("session", null);
        if (s == null) return;
        try {
            JSONObject o = new JSONObject(s);
            sid = o.getString("sid");
            day = o.getInt("day");
            date = o.getString("date");
            startedAt = o.optLong("startedAt", 0);
            endedAt = o.optLong("endedAt", 0);
            hrSum = o.optDouble("hrSum", 0);
            hrCount = o.optLong("hrCount", 0);
            hrMax = o.optInt("hrMax", 0);
            order = readIds(o.optJSONArray("order"));
            if (order.isEmpty()) order = defaultOrder(day);
            entries = new HashMap<>();
            JSONObject en = o.optJSONObject("entries");
            if (en != null) {
                for (Iterator<String> it = en.keys(); it.hasNext(); ) {
                    String k = it.next();
                    entries.put(k, readSets(en.optJSONArray(k)));
                }
            }
        } catch (JSONException e) {
            sid = null;
        }
    }

    private void saveSession() {
        if (sid == null) {
            prefs.edit().remove("session").apply();
            return;
        }
        try {
            JSONObject o = new JSONObject();
            o.put("sid", sid);
            o.put("day", day);
            o.put("date", date);
            o.put("startedAt", startedAt);
            o.put("endedAt", endedAt);
            o.put("hrSum", hrSum);
            o.put("hrCount", hrCount);
            o.put("hrMax", hrMax);
            o.put("order", new JSONArray(order));
            JSONObject en = new JSONObject();
            for (Map.Entry<String, List<double[]>> e : entries.entrySet()) {
                JSONArray a = new JSONArray();
                for (double[] p : e.getValue()) {
                    JSONArray pair = new JSONArray();
                    pair.put(p[0]);
                    pair.put(p[1]);
                    a.put(pair);
                }
                en.put(e.getKey(), a);
            }
            o.put("entries", en);
            prefs.edit().putString("session", o.toString()).apply();
        } catch (JSONException ignored) {
            // values are always finite numbers
        }
    }

    private static List<double[]> readSets(JSONArray a) {
        List<double[]> l = new ArrayList<>();
        if (a == null) return l;
        for (int i = 0; i < a.length(); i++) {
            JSONArray p = a.optJSONArray(i);
            double w = 0, r = 0;
            if (p != null) {
                w = p.optDouble(0, 0);
                r = p.optDouble(1, 0);
                if (Double.isNaN(w)) w = 0;
                if (Double.isNaN(r)) r = 0;
            }
            l.add(new double[]{w, r});
        }
        return l;
    }

    private static List<String> readIds(JSONArray a) {
        List<String> l = new ArrayList<>();
        if (a == null) return l;
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "");
            if (!s.isEmpty()) l.add(s);
        }
        return l;
    }

    // ---------------------------------------------------------------- plan and session logic

    private boolean isRunning() {
        return sid != null && startedAt > 0 && endedAt == 0;
    }

    private static int dayIndex(String d) {
        for (int i = 0; i < DAYS.length; i++) if (DAYS[i].equals(d)) return i;
        return -1;
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static int suggestedDay() {
        switch (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            case Calendar.TUESDAY: return 1;
            case Calendar.WEDNESDAY:
            case Calendar.THURSDAY: return 2;
            case Calendar.FRIDAY: return 3;
            default: return 0;
        }
    }

    private List<String> defaultOrder(int d) {
        JSONObject plan = phone.optJSONObject("plan");
        List<String> l = plan != null ? readIds(plan.optJSONArray(DAYS[d])) : new ArrayList<>();
        if (l.isEmpty()) for (Ex e : PLAN[d]) l.add(e.id);
        return l;
    }

    private Ex exDef(String id) {
        JSONObject all = phone.optJSONObject("ex");
        JSONObject o = all != null ? all.optJSONObject(id) : null;
        Ex b = BUILTIN.get(id);
        if (o != null) {
            return new Ex(id, o.optString("name", b != null ? b.name : id), o.optInt("sets", b != null ? b.sets : 3),
                o.optInt("lo", b != null ? b.lo : 8), o.optInt("hi", b != null ? b.hi : 12), o.optInt("rest", b != null ? b.rest : 90),
                o.optBoolean("bw", b != null && b.bw), o.optDouble("inc", b != null ? b.inc : 2.5));
        }
        return b != null ? b : new Ex(id, id, 3, 8, 12, 90, false, 2.5);
    }

    private Ex[] plan() {
        if (order.isEmpty()) order = defaultOrder(day);
        Ex[] out = new Ex[order.size()];
        for (int i = 0; i < out.length; i++) out[i] = exDef(order.get(i));
        return out;
    }

    /** Phone data computed "for today" only applies to a session dated today. */
    private boolean phoneIsCurrent() {
        return date != null && date.equals(phone.optString("today"));
    }

    private List<double[]> sets(Ex e) {
        List<double[]> l = entries.get(e.id);
        if (l == null) {
            l = new ArrayList<>();
            entries.put(e.id, l);
        }
        return l;
    }

    private int doneCount(Ex e) {
        int n = 0;
        for (double[] s : sets(e)) if (s[1] > 0) n++;
        return n;
    }

    private int nextSet(Ex e) {
        List<double[]> l = sets(e);
        for (int i = 0; i < l.size(); i++) if (l.get(i)[1] <= 0) return i;
        return l.size();
    }

    private int firstOpenExercise() {
        Ex[] p = plan();
        for (int i = 0; i < p.length; i++) if (doneCount(p[i]) < p[i].sets) return i;
        return p.length - 1;
    }

    private boolean allDone() {
        for (Ex e : plan()) if (doneCount(e) < e.sets) return false;
        return true;
    }

    private int restFor(Ex e) {
        JSONObject r = phone.optJSONObject("rest");
        int v = r != null ? r.optInt(e.id, 0) : 0;
        return v > 0 ? v : e.rest;
    }

    private List<double[]> lastSets(Ex e) {
        JSONObject last = phone.optJSONObject("last");
        JSONObject l = last != null ? last.optJSONObject(e.id) : null;
        if (l == null || (date != null && date.compareTo(l.optString("date")) <= 0)) return new ArrayList<>();
        return readSets(l.optJSONArray("sets"));
    }

    private JSONObject suggestion(Ex e) {
        if (!phoneIsCurrent()) return null;
        JSONObject s = phone.optJSONObject("sugg");
        return s != null ? s.optJSONObject(e.id) : null;
    }

    private static String kindLabel(String kind) {
        switch (kind) {
            case "up": return "Go up";
            case "down": return "Drop weight";
            case "deload": return "Deload";
            case "same": return "Beat last time";
            default: return "";
        }
    }

    /** A record means beating the phone's best before today and every earlier set in this session. */
    private String checkRecord(Ex e, double w, int r, int setIndex) {
        if (!phoneIsCurrent()) return null;
        JSONObject all = phone.optJSONObject("best");
        JSONObject b = all != null ? all.optJSONObject(e.id) : null;
        if (b == null) return null;
        List<double[]> ss = sets(e);
        double setE1 = w > 0 ? w * (1 + r / 30.0) : 0;
        double sessE1 = 0;
        int sessReps = 0;
        for (int i = 0; i < ss.size(); i++) {
            if (i == setIndex) continue;
            double[] s = ss.get(i);
            if (s[1] <= 0) continue;
            if (s[0] > 0) sessE1 = Math.max(sessE1, s[0] * (1 + s[1] / 30.0));
            else sessReps = Math.max(sessReps, (int) s[1]);
        }
        double bestE1 = b.optDouble("e1", 0);
        int bestReps = b.optInt("reps", 0);
        if (setE1 > 0 && bestE1 > 0 && setE1 > bestE1 + 0.05 && setE1 > sessE1 + 0.05) {
            return "New record! Est. 1RM " + kg(Math.round(setE1 * 10) / 10.0) + " kg";
        }
        if (e.bw && w <= 0 && bestReps > 0 && r > bestReps && r > sessReps) {
            return "New record! " + r + " reps";
        }
        return null;
    }

    private void startSession(int d) {
        String t = today();
        String newSid = "s_" + t + "_" + DAYS[d];
        if (isRunning() && !newSid.equals(sid)) finishSession(false);
        if (!newSid.equals(sid)) {
            sid = newSid;
            day = d;
            date = t;
            startedAt = 0;
            entries = new HashMap<>();
            order = defaultOrder(d);
            hrSum = 0;
            hrCount = 0;
            hrMax = 0;
            JSONObject ps = phoneSession(sid);
            if (ps != null) mergeFrom(ps);
        }
        if (startedAt == 0) startedAt = System.currentTimeMillis();
        endedAt = 0;
        saveSession();
        sendOp("start", null);
        exIdx = firstOpenExercise();
        editKey = null;
        confirmFinish = false;
        hrSentAt = System.currentTimeMillis();
        startHr(true);
        screen = "workout";
        render();
    }

    private void saveSet() {
        Ex[] p = plan();
        Ex e = p[exIdx];
        int si = nextSet(e);
        recordText = checkRecord(e, curW, curR, si);
        List<double[]> ss = sets(e);
        while (ss.size() <= si) ss.add(new double[]{0, 0});
        ss.set(si, new double[]{curW, curR});
        saveSession();
        try {
            JSONObject o = new JSONObject();
            o.put("ex", e.id);
            o.put("i", si);
            o.put("w", curW);
            o.put("r", curR);
            sendOp("set", o);
        } catch (JSONException ignored) {
            // finite values
        }
        buzz(recordText != null ? new long[]{0, 80, 80, 80, 80, 200} : new long[]{0, 60});
        int rest = restFor(e);
        if (doneCount(e) >= e.sets && exIdx < p.length - 1) exIdx++;
        editKey = null;
        if (allDone()) {
            screen = "workout";
            render();
            return;
        }
        restEnd = System.currentTimeMillis() + rest * 1000L;
        screen = "rest";
        render();
    }

    private void finishSession(boolean show) {
        endedAt = System.currentTimeMillis();
        restEnd = 0;
        stopHr();
        saveSession();
        sendOp("finish", null);
        if (show) {
            screen = "done";
            render();
        }
    }

    // ---------------------------------------------------------------- timers

    private void onTick() {
        long now = System.currentTimeMillis();
        if (statusTv != null && isRunning()) statusTv.setText(clock((now - startedAt) / 1000) + hrLabel());
        if (isRunning() && hrCount > 0) {
            if (now - hrSavedAt > 30000) {
                hrSavedAt = now;
                saveSession();
            }
            if (now - hrSentAt > 5 * 60000) {
                hrSentAt = now;
                sendOp("hr", null);
            }
        }
        if ("rest".equals(screen) && restEnd > 0) {
            long rem = restEnd - now;
            if (rem <= 0) {
                restEnd = 0;
                recordText = null;
                buzz(new long[]{0, 300, 150, 300, 150, 300});
                screen = "workout";
                render();
            } else if (restTv != null) {
                restTv.setText(clock((rem + 999) / 1000));
            }
        }
    }

    private void buzz(long[] pattern) {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) v.vibrate(VibrationEffect.createWaveform(pattern, -1));
    }

    // ---------------------------------------------------------------- formatting

    private static String clock(long sec) {
        if (sec < 0) sec = 0;
        long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s) : String.format(Locale.US, "%d:%02d", m, s);
    }

    private static String kg(double w) {
        double r = Math.round(w * 100) / 100.0;
        if (r == Math.floor(r)) return String.valueOf((long) r);
        String s = String.format(Locale.US, "%.2f", r);
        return s.endsWith("0") ? s.substring(0, s.length() - 1) : s;
    }

    private static String setsText(List<double[]> l, boolean bw) {
        StringBuilder b = new StringBuilder();
        for (double[] s : l) {
            if (s[1] <= 0) continue;
            if (b.length() > 0) b.append(" · ");
            int r = (int) s[1];
            if (s[0] > 0) b.append(bw ? "+" : "").append(kg(s[0])).append("×").append(r);
            else b.append(bw ? "BW×" + r : r + " reps");
        }
        return b.toString();
    }

    // ---------------------------------------------------------------- views

    private int dp(float v) {
        return Math.round(v * density);
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        return t;
    }

    private TextView button(String s, int bg, int fg, View.OnClickListener l) {
        TextView b = text(s, 15, fg, true);
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(24));
        b.setBackground(d);
        b.setMinHeight(dp(46));
        b.setPadding(dp(10), dp(8), dp(10), dp(8));
        b.setOnClickListener(l);
        return b;
    }

    private TextView round(String s, View.OnClickListener l, View.OnLongClickListener longL) {
        TextView b = text(s, 22, WHITE, true);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(GREY);
        b.setBackground(d);
        b.setOnClickListener(l);
        if (longL != null) b.setOnLongClickListener(longL);
        return b;
    }

    private LinearLayout.LayoutParams full(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp);
        return p;
    }

    private LinearLayout column() {
        int w = getResources().getDisplayMetrics().widthPixels;
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding((int) (w * 0.12f), (int) (w * 0.13f), (int) (w * 0.12f), (int) (w * 0.22f));
        return c;
    }

    private LinearLayout stepper(TextView value, String unit, Runnable minus, Runnable plus, Runnable minusBig, Runnable plusBig) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView m = round("−", v -> minus.run(), v -> { minusBig.run(); return true; });
        TextView p = round("+", v -> plus.run(), v -> { plusBig.run(); return true; });
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER);
        mid.addView(value);
        mid.addView(text(unit, 11, MUTED, false));
        row.addView(m, new LinearLayout.LayoutParams(dp(44), dp(44)));
        row.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(p, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return row;
    }

    private void show(LinearLayout content) {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.BLACK);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(content);
        setContentView(sv);
        sv.requestFocus();
    }

    private void render() {
        statusTv = null;
        restTv = null;
        if (isRunning()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if ("workout".equals(screen) && sid != null) showWorkout();
        else if ("rest".equals(screen) && sid != null) showRest();
        else if ("done".equals(screen) && sid != null) showDone();
        else showHome();
    }

    private void showHome() {
        screen = "home";
        LinearLayout c = column();
        c.addView(text("LIFT LOG", 18, WHITE, true));
        boolean running = isRunning();
        if (running) {
            c.addView(button("Continue " + LABELS[day], COLORS[day], ON_COLOR, v -> {
                screen = "workout";
                exIdx = firstOpenExercise();
                editKey = null;
                render();
            }), full(10));
            statusTv = text(clock((System.currentTimeMillis() - startedAt) / 1000) + hrLabel(), 13, COLORS[day], true);
            c.addView(statusTv, full(4));
        }
        c.addView(text(running ? "Or start another day" : "Start a workout", 12, MUTED, false), full(12));
        int sug = suggestedDay();
        for (int i = 0; i < 4; i++) {
            final int d = i;
            String label = LABELS[i] + (i == sug ? " · today" : "");
            c.addView(button(label, running ? GREY : COLORS[i], running ? WHITE : ON_COLOR, v -> startSession(d)), full(8));
        }
        long ts = phone.optLong("ts", 0);
        String sync = ts == 0 ? "Open Lift Log on your phone once to sync"
            : "Phone data from " + new SimpleDateFormat("d MMM HH:mm", Locale.UK).format(new Date(ts));
        c.addView(text(sync, 11, MUTED, false), full(12));
        if (hrSensor != null && !hrGranted()) {
            c.addView(text("Heart rate is off. Start a workout and allow body sensors to see it.", 11, MUTED, false), full(6));
        }
        show(c);
    }

    private void showWorkout() {
        Ex[] p = plan();
        if (exIdx < 0) exIdx = 0;
        if (exIdx >= p.length) exIdx = p.length - 1;
        Ex e = p[exIdx];
        int si = nextSet(e);
        List<double[]> ss = sets(e);
        List<double[]> last = lastSets(e);
        JSONObject sg = suggestion(e);
        String key = sid + "#" + e.id + "#" + si;
        if (!key.equals(editKey)) {
            editKey = key;
            double[] prev = si > 0 && si - 1 < ss.size() ? ss.get(si - 1) : null;
            double[] same = last.isEmpty() ? null : last.get(Math.min(si, last.size() - 1));
            JSONArray sr = sg != null ? sg.optJSONArray("r") : null;
            boolean hasSw = sg != null && !sg.isNull("w");
            if (prev != null && prev[1] > 0) curW = prev[0];
            else if (hasSw) curW = Math.max(0, sg.optDouble("w", 0));
            else curW = same != null ? same[0] : 0;
            if (sr != null && sr.length() > 0) curR = sr.optInt(Math.min(si, sr.length() - 1), e.lo);
            else curR = same != null && same[1] > 0 ? (int) same[1] : e.lo;
        }
        int col = COLORS[day];
        LinearLayout c = column();
        statusTv = text(isRunning() ? clock((System.currentTimeMillis() - startedAt) / 1000) + hrLabel() : "Finished", 13, col, true);
        c.addView(statusTv);
        c.addView(text((exIdx + 1) + "/" + p.length + " · " + e.name, 16, WHITE, true), full(4));
        String setLine = si < e.sets ? "Set " + (si + 1) + " of " + e.sets : "Extra set " + (si + 1);
        c.addView(text(setLine + " · " + e.lo + "–" + e.hi + " reps", 12, MUTED, false), full(2));
        if (sg != null && !kindLabel(sg.optString("kind")).isEmpty()) {
            c.addView(text(kindLabel(sg.optString("kind")), 12, col, true), full(2));
        }
        if (!last.isEmpty()) c.addView(text("Last: " + setsText(last, e.bw), 12, MUTED, false), full(2));
        if (allDone()) c.addView(text("All sets done. Finish when ready.", 12, col, true), full(4));

        final double step = e.inc > 0 ? e.inc : 2.5;
        weightTv = text(kg(curW), 28, WHITE, true);
        c.addView(stepper(weightTv, (e.bw ? "+kg" : "kg") + " (hold ±" + kg(step * 4) + ")",
            () -> setW(curW - step), () -> setW(curW + step), () -> setW(curW - step * 4), () -> setW(curW + step * 4)), full(8));
        repsTv = text(String.valueOf(curR), 28, WHITE, true);
        c.addView(stepper(repsTv, "reps",
            () -> setR(curR - 1), () -> setR(curR + 1), () -> setR(curR - 5), () -> setR(curR + 5)), full(6));

        c.addView(button("Save set", col, ON_COLOR, v -> saveSet()), full(10));
        String done = setsText(ss, e.bw);
        if (!done.isEmpty()) c.addView(text("Done: " + done, 12, MUTED, false), full(6));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        TextView prevB = button("‹ Prev", GREY, WHITE, v -> { if (exIdx > 0) { exIdx--; editKey = null; render(); } });
        TextView nextB = button("Next ›", GREY, WHITE, v -> { if (exIdx < plan().length - 1) { exIdx++; editKey = null; render(); } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(4);
        nav.addView(prevB, lp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rp.leftMargin = dp(4);
        nav.addView(nextB, rp);
        c.addView(nav, full(10));

        if (isRunning()) {
            c.addView(button(confirmFinish ? "Tap again to finish" : "Finish workout", GREY, confirmFinish ? col : WHITE, v -> {
                if (confirmFinish) {
                    confirmFinish = false;
                    finishSession(true);
                } else {
                    confirmFinish = true;
                    render();
                    handler.postDelayed(() -> {
                        if (confirmFinish) {
                            confirmFinish = false;
                            if ("workout".equals(screen)) render();
                        }
                    }, 3000);
                }
            }), full(10));
        }
        c.addView(button("Home", Color.BLACK, MUTED, v -> { screen = "home"; render(); }), full(4));
        show(c);
    }

    private void setW(double w) {
        curW = Math.max(0, Math.round(w * 100) / 100.0);
        if (weightTv != null) weightTv.setText(kg(curW));
    }

    private void setR(int r) {
        curR = Math.max(1, Math.min(100, r));
        if (repsTv != null) repsTv.setText(String.valueOf(curR));
    }

    private void showRest() {
        int col = COLORS[day];
        Ex[] p = plan();
        Ex next = p[Math.min(exIdx, p.length - 1)];
        int si = nextSet(next);
        LinearLayout c = column();
        if (recordText != null) c.addView(text(recordText, 13, GOOD, true));
        c.addView(text("REST", 15, col, true), full(recordText != null ? 4 : 0));
        restTv = text(clock(Math.max(0, (restEnd - System.currentTimeMillis() + 999) / 1000)), 46, WHITE, true);
        c.addView(restTv, full(2));
        c.addView(text("Next: " + next.name + " · set " + (si + 1), 12, MUTED, false), full(4));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(4);
        row.addView(button("−15s", GREY, WHITE, v -> restEnd -= 15000), lp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rp.leftMargin = dp(4);
        row.addView(button("+15s", GREY, WHITE, v -> restEnd += 15000), rp);
        c.addView(row, full(10));
        c.addView(button("Skip rest", col, ON_COLOR, v -> {
            restEnd = 0;
            recordText = null;
            screen = "workout";
            render();
        }), full(8));
        statusTv = text(clock((System.currentTimeMillis() - startedAt) / 1000) + hrLabel(), 12, MUTED, false);
        c.addView(statusTv, full(8));
        show(c);
    }

    private void showDone() {
        int col = COLORS[day];
        int sets = 0;
        double vol = 0;
        for (Ex e : plan()) {
            for (double[] s : sets(e)) {
                if (s[1] > 0) {
                    sets++;
                    vol += s[0] * s[1];
                }
            }
        }
        LinearLayout c = column();
        c.addView(text("WORKOUT DONE", 15, col, true));
        c.addView(text(clock(Math.max(0, (endedAt - startedAt) / 1000)), 40, WHITE, true), full(4));
        c.addView(text(LABELS[day] + " · " + sets + " sets · " + Math.round(vol) + " kg", 12, MUTED, false), full(4));
        if (hrCount > 0) c.addView(text("♥ avg " + hrAvg() + " · max " + hrMax + " bpm", 12, MUTED, false), full(4));
        c.addView(text("Sent to your phone. It updates next time you open Lift Log there.", 11, MUTED, false), full(8));
        c.addView(button("Done", col, ON_COLOR, v -> {
            screen = "home";
            render();
        }), full(10));
        show(c);
    }
}
