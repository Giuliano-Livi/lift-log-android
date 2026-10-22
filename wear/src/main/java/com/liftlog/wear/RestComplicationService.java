package com.liftlog.wear;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.wear.watchface.complications.data.ComplicationData;
import androidx.wear.watchface.complications.data.ComplicationText;
import androidx.wear.watchface.complications.data.ComplicationType;
import androidx.wear.watchface.complications.data.CountDownTimeReference;
import androidx.wear.watchface.complications.data.CountUpTimeReference;
import androidx.wear.watchface.complications.data.PlainComplicationText;
import androidx.wear.watchface.complications.data.ShortTextComplicationData;
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText;
import androidx.wear.watchface.complications.data.TimeDifferenceStyle;
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService;
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester;
import androidx.wear.watchface.complications.datasource.ComplicationRequest;

import java.time.Instant;
import java.util.Locale;

/**
 * Watch-face complication: the rest countdown while resting, the workout clock while a workout runs,
 * otherwise today's day. The watch face draws the countdown itself, so it stays live without updates.
 */
public class RestComplicationService extends ComplicationDataSourceService {

    static void refresh(Context c) {
        try {
            ComplicationDataSourceUpdateRequester.create(c, new ComponentName(c, RestComplicationService.class)).requestUpdateAll();
        } catch (Exception ignored) {
            // not on any watch face
        }
    }

    @Override
    public void onComplicationRequest(@NonNull ComplicationRequest request, @NonNull ComplicationRequestListener listener) {
        ComplicationData data = request.getComplicationType() == ComplicationType.SHORT_TEXT ? build(Glance.read(this)) : null;
        try {
            listener.onComplicationData(data);
        } catch (RemoteException ignored) {
            // the watch face went away
        }
    }

    @Nullable
    @Override
    public ComplicationData getPreviewData(@NonNull ComplicationType type) {
        if (type != ComplicationType.SHORT_TEXT) return null;
        return shortText(new PlainComplicationText.Builder("1:30").build(), "REST", "Rest timer");
    }

    private ComplicationData build(Glance g) {
        if (g.running && g.restEnd > 0) {
            ComplicationText t = new TimeDifferenceComplicationText.Builder(TimeDifferenceStyle.STOPWATCH,
                new CountDownTimeReference(Instant.ofEpochMilli(g.restEnd))).setDisplayAsNow(true).build();
            return shortText(t, "REST", "Rest time left");
        }
        if (g.running) {
            ComplicationText t = new TimeDifferenceComplicationText.Builder(TimeDifferenceStyle.STOPWATCH,
                new CountUpTimeReference(Instant.ofEpochMilli(g.startedAt))).build();
            return shortText(t, shortLabel(g.dayLabel), "Workout time");
        }
        String label = g.today != null ? shortLabel(g.today.label) : "LIFT";
        return shortText(new PlainComplicationText.Builder(label).build(), "LIFT", "Today's workout: " + label);
    }

    private static String shortLabel(String s) {
        String t = s == null ? "" : s.trim().toUpperCase(Locale.getDefault());
        return t.length() > 7 ? t.substring(0, 7) : t;
    }

    private ComplicationData shortText(ComplicationText text, String title, String description) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new ShortTextComplicationData.Builder(text, new PlainComplicationText.Builder(description).build())
            .setTitle(new PlainComplicationText.Builder(title).build())
            .setTapAction(open)
            .build();
    }
}
