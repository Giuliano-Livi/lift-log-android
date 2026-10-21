package com.liftlog.app;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shown by Health Connect when you ask why Lift Log wants access to your data. */
public class PrivacyActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView t = new TextView(this);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        t.setPadding(pad, pad, pad, pad);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setText("Lift Log reads your weight and body fat from Health Connect to show them in the Body tab "
            + "next to your training.\n\nThe data stays on this phone, inside Lift Log. It is never uploaded or "
            + "shared. Lift Log does not write anything to Health Connect.\n\nYou can remove access at any time "
            + "in Health Connect > App permissions > Lift Log.");
        ScrollView sv = new ScrollView(this);
        sv.addView(t);
        setContentView(sv);
    }
}
