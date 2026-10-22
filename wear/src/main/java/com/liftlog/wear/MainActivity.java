package com.liftlog.wear;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
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
import android.widget.FrameLayout;
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

    /** A training day of the plan in use on the phone: id, name and colour (index into COLORS). */
    static final class Day {
        final String id, label;
        final int tone;

        Day(String id, String label, int tone) {
            this.id = id; this.label = label; this.tone = tone;
        }
    }

    /** Built-in plan days, used until the phone app has sent the plan you use. */
    static final String[] DAYS = {"push", "pull", "legs", "upper"};
    static final String[] LABELS = {"Push", "Pull", "Legs", "Upper"};
    // same palette as the phone app: navy surfaces, one colour per training day (six colours for your own plans)
    static final int[] COLORS = {0xFF8B7CF8, 0xFF5B8DEF, 0xFFF5A524, 0xFF42B986, 0xFFE8659E, 0xFF22B5C6};
    static final int[] COLORS_HI = {0xFFB6AAFF, 0xFF93B4FA, 0xFFFFCD70, 0xFF7FDCB1, 0xFFFF9CC8, 0xFF6FDDE9};
    static final int[] ON_DAY = {0xFFFFFFFF, 0xFFFFFFFF, 0xFF1E1404, 0xFF05170E, 0xFFFFFFFF, 0xFF03181B};
    static final int BG = 0xFF0B0E1A;
    static final int GREY = 0xFF1B2034;
    static final int SURFACE2 = 0xFF262C45;
    static final int MUTED = 0xFF8B91AA;
    static final int WHITE = 0xFFF2F3F9;
    static final int GOOD = 0xFF7FE0B2;
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
    /** Circular progress ring used for the rest countdown. */
    static final class RingView extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private float progress = 1f;

        RingView(Context c, int color, float stroke) {
            super(c);
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(stroke);
            track.setColor(SURFACE2);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeWidth(stroke);
            arc.setStrokeCap(Paint.Cap.ROUND);
            arc.setColor(color);
        }

        void setProgress(float p) {
            progress = Math.max(0f, Math.min(1f, p));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float half = arc.getStrokeWidth() / 2f;
            box.set(half, half, getWidth() - half, getHeight() - half);
            canvas.drawArc(box, 0, 360, false, track);
            if (progress > 0) canvas.drawArc(box, -90, 360 * progress, false, arc);
        }
    }

    static final Map<String, Ex> BUILTIN = new HashMap<>();
    static {
        for (Ex[] day : PLAN) for (Ex e : day) BUILTIN.put(e.id, e);
    }

    private SharedPreferences prefs;
    private JSONObject phone = new JSONObject();

    // current session
    private String sid, date;
    private String dayId = DAYS[0], dayLabel = LABELS[0];
    private int tone;
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
    private RingView restRing;
    private int restTotal = 1;
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
        handleLaunch(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLaunch(intent);
    }

    /** The tile's Start button opens the app with the day to start. */
    private void handleLaunch(Intent intent) {
        String start = intent != null ? intent.getStringExtra("start") : null;
        if (start == null || start.isEmpty()) return;
        intent.removeExtra("start");
        if (isRunning() && start.equals(dayId)) {
            screen = "workout";
            exIdx = firstOpenExercise();
            editKey = null;
            render();
            return;
        }
        startSession(findDay(start, null, -1));
    }

    private String glanceSig = "";

    /** Tell the tile and the watch-face complication when what they show has changed (and only then). */
    private void publishGlance() {
        prefs.edit().putLong("restEnd", restEnd).apply();
        Day sug = suggestedDay(days());
        String sig = isRunning() + "|" + dayId + "|" + startedAt + "|" + restEnd + "|" + (sug != null ? sug.id + sug.label + sug.tone + orderOf(phone, sug.id).size() : "")
            + "|" + phone.optString("planName", "") + "|" + today();
        if (sig.equals(glanceSig)) return;
        glanceSig = sig;
        TodayTileService.refresh(this);
        RestComplicationService.refresh(this);
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
            publishGlance();
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
        publishGlance();
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
            if (s == null || s.optString("day").isEmpty()) continue;
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
        setDay(findDay(ps.optString("day"), ps.optString("label", null), ps.optInt("tone", -1)));
        date = ps.optString("date");
        startedAt = 0;
        endedAt = 0;
        entries = new HashMap<>();
        order = defaultOrder(dayId);
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
            o.put("day", dayId);
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
            Object d = o.opt("day");
            if (d instanceof Number) {
                // saved by an older version: index into the built-in days
                int i = Math.max(0, Math.min(DAYS.length - 1, ((Number) d).intValue()));
                setDay(new Day(DAYS[i], LABELS[i], i));
            } else {
                setDay(findDay(d == null ? "" : String.valueOf(d), o.optString("dayLabel", null), o.optInt("tone", -1)));
            }
            date = o.getString("date");
            startedAt = o.optLong("startedAt", 0);
            endedAt = o.optLong("endedAt", 0);
            hrSum = o.optDouble("hrSum", 0);
            hrCount = o.optLong("hrCount", 0);
            hrMax = o.optInt("hrMax", 0);
            order = readIds(o.optJSONArray("order"));
            if (order.isEmpty()) order = defaultOrder(dayId);
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
            o.put("day", dayId);
            o.put("dayLabel", dayLabel);
            o.put("tone", tone);
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

    private static int builtinIndex(String d) {
        for (int i = 0; i < DAYS.length; i++) if (DAYS[i].equals(d)) return i;
        return -1;
    }

    /** Days of the plan in use, as sent by the phone (the built-in plan before the first sync). */
    private List<Day> days() {
        return daysOf(phone);
    }

    static List<Day> daysOf(JSONObject phone) {
        List<Day> l = new ArrayList<>();
        JSONArray a = phone.optJSONArray("days");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null || o.optString("id").isEmpty()) continue;
                String id = o.optString("id");
                l.add(new Day(id, o.optString("label", id), Math.abs(o.optInt("tone", i)) % COLORS.length));
            }
        }
        if (l.isEmpty()) for (int i = 0; i < DAYS.length; i++) l.add(new Day(DAYS[i], LABELS[i], i));
        return l;
    }

    /** A day by id, with the best name and colour known for it. */
    private Day findDay(String id, String label, int tone) {
        for (Day d : days()) if (d.id.equals(id)) return d;
        int bi = builtinIndex(id);
        String l = label != null && !label.isEmpty() ? label : bi >= 0 ? LABELS[bi] : "Workout";
        int t = tone >= 0 ? tone % COLORS.length : bi >= 0 ? bi : 0;
        return new Day(id == null || id.isEmpty() ? DAYS[0] : id, l, t);
    }

    private void setDay(Day d) {
        dayId = d.id;
        dayLabel = d.label;
        tone = d.tone;
    }

    private boolean hasExercises(Day d) {
        return !orderOf(phone, d.id).isEmpty();
    }

    static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static int weekdayIndex() {
        switch (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            case Calendar.TUESDAY: return 1;
            case Calendar.WEDNESDAY:
            case Calendar.THURSDAY: return 2;
            case Calendar.FRIDAY: return 3;
            default: return 0;
        }
    }

    /**
     * Today's suggested day, as on the phone: the built-in plan follows the weekday; your own plans
     * go round in order (the day after the last one you trained, or the same day if that was today).
     */
    private Day suggestedDay(List<Day> ds) {
        return suggestedOf(phone, ds);
    }

    static Day suggestedOf(JSONObject phone, List<Day> ds) {
        if (ds.isEmpty()) return null;
        int start = 0;
        if (phone.optBoolean("rot", false)) {
            JSONObject ld = phone.optJSONObject("lastDay");
            if (ld != null) {
                for (int i = 0; i < ds.size(); i++) {
                    if (ds.get(i).id.equals(ld.optString("id"))) {
                        start = today().equals(ld.optString("date")) ? i : (i + 1) % ds.size();
                        break;
                    }
                }
            }
        } else {
            String want = DAYS[weekdayIndex()];
            for (int i = 0; i < ds.size(); i++) if (ds.get(i).id.equals(want)) start = i;
        }
        for (int k = 0; k < ds.size(); k++) {
            Day d = ds.get((start + k) % ds.size());
            if (!orderOf(phone, d.id).isEmpty()) return d;
        }
        return null;
    }

    private List<String> defaultOrder(String id) {
        return orderOf(phone, id);
    }

    static List<String> orderOf(JSONObject phone, String id) {
        JSONObject plan = phone.optJSONObject("plan");
        List<String> l = plan != null ? readIds(plan.optJSONArray(id)) : new ArrayList<>();
        int bi = builtinIndex(id);
        if (l.isEmpty() && bi >= 0 && (plan == null || !plan.has(id))) for (Ex e : PLAN[bi]) l.add(e.id);
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
        if (order.isEmpty()) order = defaultOrder(dayId);
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
        return Math.max(0, p.length - 1);
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

    private void startSession(Day d) {
        String t = today();
        String newSid = "s_" + t + "_" + d.id;
        if (isRunning() && !newSid.equals(sid)) finishSession(false);
        if (!newSid.equals(sid)) {
            sid = newSid;
            setDay(d);
            date = t;
            startedAt = 0;
            entries = new HashMap<>();
            order = defaultOrder(d.id);
            hrSum = 0;
            hrCount = 0;
            hrMax = 0;
            JSONObject ps = phoneSession(sid);
            if (ps != null) mergeFrom(ps);
        }
        if (startedAt == 0) startedAt = System.currentTimeMillis();
        endedAt = 0;
        saveSession();
        publishGlance();
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
        if (p.length == 0) return;
        if (exIdx >= p.length) exIdx = p.length - 1;
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
        restTotal = Math.max(1, rest);
        publishGlance();
        screen = "rest";
        render();
    }

    private void finishSession(boolean show) {
        endedAt = System.currentTimeMillis();
        restEnd = 0;
        publishGlance();
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
                publishGlance();
                recordText = null;
                buzz(new long[]{0, 300, 150, 300, 150, 300});
                screen = "workout";
                render();
            } else if (restTv != null) {
                restTv.setText(clock((rem + 999) / 1000));
                if (restRing != null) restRing.setProgress(rem / (restTotal * 1000f));
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
        d.setCornerRadius(dp(26));
        b.setBackground(d);
        b.setMinHeight(dp(46));
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        b.setOnClickListener(l);
        return b;
    }

    /** Gradient pill in a day's colour, like the phone app's main buttons. */
    private TextView primary(String s, int d, View.OnClickListener l) {
        TextView b = button(s, COLORS[d], ON_DAY[d], l);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{COLORS_HI[d], COLORS[d]});
        g.setCornerRadius(dp(26));
        b.setBackground(g);
        return b;
    }

    /** Rounded navy card that groups related text. */
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable d = new GradientDrawable();
        d.setColor(GREY);
        d.setCornerRadius(dp(22));
        c.setBackground(d);
        c.setPadding(dp(12), dp(10), dp(12), dp(10));
        return c;
    }

    private TextView pill(String s, int fg) {
        TextView t = text(s, 12, fg, true);
        GradientDrawable d = new GradientDrawable();
        d.setColor(GREY);
        d.setCornerRadius(dp(14));
        t.setBackground(d);
        t.setPadding(dp(12), dp(4), dp(12), dp(4));
        return t;
    }

    private LinearLayout.LayoutParams wrap(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp);
        return p;
    }

    private TextView round(String s, View.OnClickListener l, View.OnLongClickListener longL) {
        TextView b = text(s, 22, WHITE, true);
        GradientDrawable d = new GradientDrawable();
        d.setColor(SURFACE2);
        d.setCornerRadius(dp(15));
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
        row.addView(m, new LinearLayout.LayoutParams(dp(46), dp(46)));
        row.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(p, new LinearLayout.LayoutParams(dp(46), dp(46)));
        return row;
    }

    private void show(LinearLayout content) {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(content);
        setContentView(sv);
        sv.requestFocus();
    }

    private void render() {
        statusTv = null;
        restTv = null;
        restRing = null;
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
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER);
        TextView logo = text("●", 10, COLORS[0], true);
        brand.addView(logo);
        TextView name = text("  LIFT LOG", 12, MUTED, true);
        name.setLetterSpacing(0.12f);
        brand.addView(name);
        c.addView(brand);
        boolean running = isRunning();
        c.addView(text(running ? "Keep going" : "Ready to train?", 19, WHITE, true), full(4));
        if (running) {
            LinearLayout live = card();
            live.addView(text(dayLabel + " in progress", 13, COLORS_HI[tone], true));
            statusTv = text(clock((System.currentTimeMillis() - startedAt) / 1000) + hrLabel(), 22, WHITE, true);
            live.addView(statusTv, full(2));
            c.addView(live, full(10));
            c.addView(primary("Continue", tone, v -> {
                screen = "workout";
                exIdx = firstOpenExercise();
                editKey = null;
                render();
            }), full(8));
        }
        List<Day> ds = days();
        Day sug = suggestedDay(ds);
        String planName = phone.optString("planName", "");
        c.addView(text(running ? "Or start another day" : planName.isEmpty() ? "Pick today's session" : planName, 12, MUTED, false), full(12));
        int shown = 0;
        for (Day d : ds) {
            if (!hasExercises(d)) continue;
            shown++;
            if (!running && sug != null && d.id.equals(sug.id)) {
                c.addView(primary(d.label + "  ·  today", d.tone, v -> startSession(d)), full(8));
            } else {
                TextView b = button("●  " + d.label, GREY, WHITE, v -> startSession(d));
                android.text.SpannableString sp = new android.text.SpannableString("●  " + d.label);
                sp.setSpan(new android.text.style.ForegroundColorSpan(COLORS[d.tone]), 0, 1, 0);
                b.setText(sp);
                c.addView(b, full(8));
            }
        }
        if (shown == 0) c.addView(text("Your plan has no exercises yet. Add some in Lift Log on your phone.", 12, MUTED, false), full(8));
        long ts = phone.optLong("ts", 0);
        String sync = ts == 0 ? "Open Lift Log on your phone once to sync"
            : "Synced " + new SimpleDateFormat("d MMM HH:mm", Locale.UK).format(new Date(ts));
        c.addView(text(sync, 11, MUTED, false), full(12));
        if (hrSensor != null && !hrGranted()) {
            c.addView(text("Heart rate is off. Start a workout and allow body sensors to see it.", 11, MUTED, false), full(6));
        }
        show(c);
    }

    private void showWorkout() {
        Ex[] p = plan();
        if (p.length == 0) {
            LinearLayout c = column();
            c.addView(pill(dayLabel, COLORS_HI[tone]), wrap(0));
            c.addView(text("No exercises on this day. Add some in Lift Log on your phone.", 13, WHITE, false), full(10));
            if (isRunning()) c.addView(button("Finish workout", GREY, WHITE, v -> finishSession(true)), full(10));
            c.addView(button("Home", BG, MUTED, v -> { screen = "home"; render(); }), full(4));
            show(c);
            return;
        }
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
        int col = COLORS_HI[tone];
        LinearLayout c = column();
        statusTv = pill(isRunning() ? clock((System.currentTimeMillis() - startedAt) / 1000) + hrLabel() : "Finished", col);
        c.addView(statusTv, wrap(0));

        LinearLayout info = card();
        info.addView(text((exIdx + 1) + " of " + p.length, 11, col, true));
        info.addView(text(e.name, 16, WHITE, true), full(2));
        String setLine = si < e.sets ? "Set " + (si + 1) + "/" + e.sets : "Extra set " + (si + 1);
        info.addView(text(setLine + "  ·  " + e.lo + "–" + e.hi + " reps", 12, MUTED, false), full(2));
        if (sg != null && !kindLabel(sg.optString("kind")).isEmpty()) {
            info.addView(text(kindLabel(sg.optString("kind")), 12, col, true), full(2));
        }
        if (!last.isEmpty()) info.addView(text("Last: " + setsText(last, e.bw), 11, MUTED, false), full(2));
        c.addView(info, full(8));
        if (allDone()) c.addView(text("All sets done. Finish when ready.", 12, GOOD, true), full(6));

        final double step = e.inc > 0 ? e.inc : 2.5;
        weightTv = text(kg(curW), 28, WHITE, true);
        c.addView(stepper(weightTv, (e.bw ? "+kg" : "kg") + "  ·  hold ±" + kg(step * 4),
            () -> setW(curW - step), () -> setW(curW + step), () -> setW(curW - step * 4), () -> setW(curW + step * 4)), full(10));
        repsTv = text(String.valueOf(curR), 28, WHITE, true);
        c.addView(stepper(repsTv, "reps",
            () -> setR(curR - 1), () -> setR(curR + 1), () -> setR(curR - 5), () -> setR(curR + 5)), full(6));

        c.addView(primary("Save set", tone, v -> saveSet()), full(12));
        String done = setsText(ss, e.bw);
        if (!done.isEmpty()) c.addView(text("Done: " + done, 12, MUTED, false), full(8));

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
            }), full(8));
        }
        c.addView(button("Home", BG, MUTED, v -> { screen = "home"; render(); }), full(4));
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
        int col = COLORS_HI[tone];
        Ex[] p = plan();
        if (p.length == 0) {
            screen = "workout";
            showWorkout();
            return;
        }
        Ex next = p[Math.min(exIdx, p.length - 1)];
        int si = nextSet(next);
        LinearLayout c = column();
        if (recordText != null) c.addView(pill(recordText, GOOD), wrap(0));
        FrameLayout dial = new FrameLayout(this);
        long rem = Math.max(0, restEnd - System.currentTimeMillis());
        restRing = new RingView(this, col, dp(9));
        restRing.setProgress(rem / (restTotal * 1000f));
        dial.addView(restRing, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER);
        TextView lbl = text("REST", 11, col, true);
        lbl.setLetterSpacing(0.15f);
        mid.addView(lbl);
        restTv = text(clock((rem + 999) / 1000), 38, WHITE, true);
        mid.addView(restTv);
        statusTv = text(clock((System.currentTimeMillis() - startedAt) / 1000) + hrLabel(), 11, MUTED, false);
        mid.addView(statusTv);
        dial.addView(mid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams dp0 = new LinearLayout.LayoutParams(dp(150), dp(150));
        dp0.topMargin = dp(recordText != null ? 8 : 0);
        c.addView(dial, dp0);
        c.addView(text("Next: " + next.name + " · set " + (si + 1), 12, MUTED, false), full(8));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(4);
        row.addView(button("−15s", GREY, WHITE, v -> { restEnd -= 15000; restTotal = Math.max(1, restTotal - 15); publishGlance(); }), lp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rp.leftMargin = dp(4);
        row.addView(button("+15s", GREY, WHITE, v -> { restEnd += 15000; restTotal += 15; publishGlance(); }), rp);
        c.addView(row, full(10));
        c.addView(primary("Skip rest", tone, v -> {
            restEnd = 0;
            publishGlance();
            recordText = null;
            screen = "workout";
            render();
        }), full(8));
        show(c);
    }

    private void showDone() {
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
        c.addView(pill("WORKOUT DONE", COLORS_HI[tone]), wrap(0));
        LinearLayout sum = card();
        sum.addView(text(clock(Math.max(0, (endedAt - startedAt) / 1000)), 36, WHITE, true));
        sum.addView(text(dayLabel + " · " + sets + " sets · " + Math.round(vol) + " kg", 12, MUTED, false), full(2));
        if (hrCount > 0) sum.addView(text("♥ avg " + hrAvg() + " · max " + hrMax + " bpm", 12, COLORS_HI[tone], true), full(4));
        c.addView(sum, full(10));
        c.addView(text("Sent to your phone. It updates next time you open Lift Log there.", 11, MUTED, false), full(10));
        c.addView(primary("Done", tone, v -> {
            screen = "home";
            render();
        }), full(10));
        show(c);
    }
}
