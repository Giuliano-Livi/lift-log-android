package com.liftlog.app;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

/** Receives sets logged on the watch, even when the phone app is closed. */
public class WatchListenerService extends WearableListenerService {
    @Override
    public void onDataChanged(DataEventBuffer events) {
        boolean any = false;
        for (DataEvent ev : events) {
            if (ev.getType() != DataEvent.TYPE_CHANGED) continue;
            DataItem item = ev.getDataItem();
            String path = item.getUri().getPath();
            if (path == null || !path.startsWith("/liftlog/op/")) continue;
            String json = DataMapItem.fromDataItem(item).getDataMap().getString("json");
            if (json != null) {
                WatchSync.addOp(this, json);
                any = true;
            }
            Wearable.getDataClient(this).deleteDataItems(item.getUri());
        }
        MainActivity a = WatchSync.active;
        if (any && a != null) a.notifyWatchOps();
    }
}
