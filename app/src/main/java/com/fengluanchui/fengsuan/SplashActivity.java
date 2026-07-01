package com.fengluanchui.fengsuan;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SplashActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(7, 8, 12));
        getWindow().setNavigationBarColor(Color.rgb(7, 8, 12));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        root.setBackgroundColor(Color.rgb(7, 8, 12));
        setContentView(root);

        Typeface script = Typeface.create("serif", Typeface.ITALIC);

        TextView title = new TextView(this);
        title.setText("风算");
        title.setTextColor(Color.rgb(230, 191, 99));
        title.setTextSize(50);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(script, Typeface.BOLD_ITALIC);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(this);
        sub.setText("一款懂你的计算管家");
        sub.setTextColor(Color.rgb(236, 236, 232));
        sub.setTextSize(24);
        sub.setGravity(Gravity.CENTER);
        sub.setTypeface(script, Typeface.ITALIC);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.topMargin = dp(12);
        root.addView(sub, subParams);

        View line = new View(this);
        line.setBackgroundColor(Color.rgb(230, 191, 99));
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(dp(112), dp(1));
        lineParams.topMargin = dp(26);
        root.addView(line, lineParams);

        TextView author = new TextView(this);
        author.setText("by 风乱吹");
        author.setTextColor(Color.rgb(154, 160, 171));
        author.setTextSize(15);
        author.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams authorParams = new LinearLayout.LayoutParams(-1, -2);
        authorParams.topMargin = dp(20);
        root.addView(author, authorParams);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        }, 1500);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
