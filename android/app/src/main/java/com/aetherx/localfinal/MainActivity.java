package com.aetherx.localfinal;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView text = new TextView(this);
        text.setText("AETHERX APP LOCAL FUNCIONANDO");
        text.setTextColor(Color.WHITE);
        text.setTextSize(32);
        text.setGravity(Gravity.CENTER);
        text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        root.setPadding(padding, padding, padding, padding);

        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        );
        root.addView(text, textParams);

        setContentView(root);
    }
}
