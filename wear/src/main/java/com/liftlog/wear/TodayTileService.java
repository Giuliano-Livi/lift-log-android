package com.liftlog.wear;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.wear.protolayout.ActionBuilders;
import androidx.wear.protolayout.ColorBuilders;
import androidx.wear.protolayout.DimensionBuilders;
import androidx.wear.protolayout.LayoutElementBuilders;
import androidx.wear.protolayout.ModifiersBuilders;
import androidx.wear.protolayout.ResourceBuilders;
import androidx.wear.protolayout.TimelineBuilders;
import androidx.wear.tiles.RequestBuilders;
import androidx.wear.tiles.TileBuilders;
import androidx.wear.tiles.TileService;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Tile: today's workout with a Start button, or the running workout with Continue. */
public class TodayTileService extends TileService {
    private static final String RES = "1";

    static void refresh(Context c) {
        try {
            TileService.getUpdater(c).requestUpdate(TodayTileService.class);
        } catch (Exception ignored) {
            // no tile added: nothing to update
        }
    }

    @NonNull
    @Override
    protected ListenableFuture<TileBuilders.Tile> onTileRequest(@NonNull RequestBuilders.TileRequest requestParams) {
        Glance g = Glance.read(this);
        LayoutElementBuilders.Column.Builder col = new LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER);
        col.addContent(text("LIFT LOG", 11, MainActivity.MUTED, true));
        col.addContent(space(6));
        if (g.running) {
            col.addContent(text(g.dayLabel + " in progress", 15, MainActivity.COLORS_HI[g.tone], true));
            col.addContent(space(2));
            String t = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(g.restEnd > 0 ? g.restEnd : g.startedAt));
            col.addContent(text(g.restEnd > 0 ? "Rest until " + t : "Started at " + t, 12, MainActivity.MUTED, false));
            col.addContent(space(10));
            col.addContent(button("Continue", MainActivity.COLORS[g.tone], MainActivity.ON_DAY[g.tone], null));
        } else if (g.today != null) {
            col.addContent(text(g.planName.isEmpty() ? "Today" : "Today · " + g.planName, 11, MainActivity.MUTED, false));
            col.addContent(space(2));
            col.addContent(text(g.today.label, 22, MainActivity.WHITE, true));
            col.addContent(text(g.exercises + (g.exercises == 1 ? " exercise" : " exercises"), 12, MainActivity.MUTED, false));
            col.addContent(space(10));
            col.addContent(button("Start", MainActivity.COLORS[g.today.tone], MainActivity.ON_DAY[g.today.tone], g.today.id));
        } else {
            col.addContent(text(g.synced ? "No exercises in your plan yet" : "Open Lift Log on your phone to sync", 13, MainActivity.WHITE, false));
            col.addContent(space(10));
            col.addContent(button("Open", MainActivity.COLORS[0], MainActivity.ON_DAY[0], null));
        }
        LayoutElementBuilders.Box root = new LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(new ModifiersBuilders.Modifiers.Builder()
                .setPadding(new ModifiersBuilders.Padding.Builder().setStart(DimensionBuilders.dp(14)).setEnd(DimensionBuilders.dp(14)).build())
                .build())
            .addContent(col.build())
            .build();
        TileBuilders.Tile tile = new TileBuilders.Tile.Builder()
            .setResourcesVersion(RES)
            .setFreshnessIntervalMillis(30 * 60 * 1000L)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
            .build();
        return Futures.immediateFuture(tile);
    }

    @NonNull
    @Override
    protected ListenableFuture<ResourceBuilders.Resources> onTileResourcesRequest(@NonNull RequestBuilders.ResourcesRequest requestParams) {
        return Futures.immediateFuture(new ResourceBuilders.Resources.Builder().setVersion(RES).build());
    }

    private static LayoutElementBuilders.LayoutElement text(String s, float sp, int color, boolean bold) {
        return new LayoutElementBuilders.Text.Builder()
            .setText(s)
            .setMaxLines(2)
            .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
            .setFontStyle(new LayoutElementBuilders.FontStyle.Builder()
                .setSize(DimensionBuilders.sp(sp))
                .setColor(ColorBuilders.argb(color))
                .setWeight(bold ? LayoutElementBuilders.FONT_WEIGHT_BOLD : LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                .build())
            .build();
    }

    private static LayoutElementBuilders.LayoutElement space(float dp) {
        return new LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(dp)).build();
    }

    /** A rounded button that opens the watch app; with a day id it starts that day's workout. */
    private LayoutElementBuilders.LayoutElement button(String label, int bg, int fg, String startDay) {
        ActionBuilders.AndroidActivity.Builder act = new ActionBuilders.AndroidActivity.Builder()
            .setPackageName(getPackageName())
            .setClassName(MainActivity.class.getName());
        if (startDay != null) act.addKeyToExtraMapping("start", new ActionBuilders.AndroidStringExtra.Builder().setValue(startDay).build());
        ModifiersBuilders.Clickable click = new ModifiersBuilders.Clickable.Builder()
            .setId(startDay != null ? "start" : "open")
            .setOnClick(new ActionBuilders.LaunchAction.Builder().setAndroidActivity(act.build()).build())
            .build();
        return new LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.dp(124))
            .setHeight(DimensionBuilders.dp(44))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(new ModifiersBuilders.Modifiers.Builder()
                .setBackground(new ModifiersBuilders.Background.Builder()
                    .setColor(ColorBuilders.argb(bg))
                    .setCorner(new ModifiersBuilders.Corner.Builder().setRadius(DimensionBuilders.dp(22)).build())
                    .build())
                .setClickable(click)
                .build())
            .addContent(text(label, 15, fg, true))
            .build();
    }
}
