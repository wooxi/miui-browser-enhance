package com.hermes.browserenhance;

import android.app.Activity;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class MainActivity extends Activity {

    public static final String PREF = "browser_enhance_prefs";
    public static final String KEY_RULES = "rules_txt";
    public static final String DEFAULT_RULES = "nopopup:missav\nallow:fourhoi.com\nallow:surrit.com\nallow:missav.ai";

    private SharedPreferences sp;
    private LinearLayout logContainer;
    private TextView logCount;
    private TextView logEmpty;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void bindSwitch(int id, final String key, boolean def) {
        Switch sw = (Switch) findViewById(id);
        if (sw == null) return;
        sw.setChecked(sp.getBoolean(key, def));
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                sp.edit().putBoolean(key, checked).apply();
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sp = getSharedPreferences(PREF, MODE_PRIVATE);
        if (!sp.contains(KEY_RULES)) {
            sp.edit().putString(KEY_RULES, DEFAULT_RULES).apply();
        }

        bindSwitch(R.id.sw_guard_popup, "guard_popup", true);
        bindSwitch(R.id.sw_disable_onetrack, "disable_onetrack", true);
        bindSwitch(R.id.sw_disable_promo_dialog, "disable_promo_dialog", true);
        bindSwitch(R.id.sw_disable_hotword, "disable_hotword", true);

        final EditText rules = (EditText) findViewById(R.id.rules);

        final View panel = findViewById(R.id.rules_panel);
        final ImageView toggle = (ImageView) findViewById(R.id.rules_toggle);
        View.OnClickListener expand = new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean show = panel.getVisibility() != View.VISIBLE;
                if (!show) {
                    try { sp.edit().putString(KEY_RULES, rules.getText().toString()).apply(); } catch (Throwable t) { }
                }
                panel.setVisibility(show ? View.VISIBLE : View.GONE);
                if (toggle != null) {
                    toggle.animate().rotation(show ? 180f : 0f)
                          .setInterpolator(new DecelerateInterpolator())
                          .setDuration(180).start();
                }
            }
        };
        if (toggle != null) toggle.setOnClickListener(expand);
        View guardText = findViewById(R.id.guard_text);
        if (guardText != null) guardText.setOnClickListener(expand);

        String cur = sp.getString(KEY_RULES, DEFAULT_RULES);
        if (cur == null || cur.length() == 0) cur = DEFAULT_RULES;
        rules.setText(cur);

        Button save = (Button) findViewById(R.id.save);
        if (save != null) {
            save.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    sp.edit().putString(KEY_RULES, rules.getText().toString()).apply();
                    Toast.makeText(MainActivity.this, R.string.saved, Toast.LENGTH_SHORT).show();
                }
            });
        }

        logContainer = (LinearLayout) findViewById(R.id.log_container);
        logCount = (TextView) findViewById(R.id.log_count);
        logEmpty = (TextView) findViewById(R.id.log_empty);

        View btnRefresh = findViewById(R.id.btn_refresh_log);
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    loadLogs();
                    Toast.makeText(MainActivity.this, "已刷新", Toast.LENGTH_SHORT).show();
                }
            });
        }

        View btnClear = findViewById(R.id.btn_clear_log);
        if (btnClear != null) {
            btnClear.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try {
                        getContentResolver().call(Uri.parse("content://com.hermes.browserenhance.log"), "clear", null, null);
                    } catch (Throwable t) {}
                    loadLogs();
                    Toast.makeText(MainActivity.this, "已清空", Toast.LENGTH_SHORT).show();
                }
            });
        }

        loadLogs();
    }

    private void loadLogs() {
        if (logContainer == null) return;
        ArrayList<String> lines = new ArrayList<String>();
        try {
            Bundle res = getContentResolver().call(Uri.parse("content://com.hermes.browserenhance.log"), "get", null, null);
            if (res != null && res.containsKey("lines")) {
                lines = res.getStringArrayList("lines");
            }
        } catch (Throwable t) {}

        // Fallback: direct read from internal filesDir
        if (lines == null || lines.isEmpty()) {
            try {
                File f = new File(getFilesDir(), "behavior.log");
                if (f.exists()) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
                    String l;
                    while ((l = br.readLine()) != null) {
                        if (l.trim().length() > 0) lines.add(l);
                    }
                    br.close();
                }
            } catch (Throwable t) {}
        }

        logContainer.removeAllViews();
        if (lines == null || lines.isEmpty()) {
            if (logEmpty != null) logEmpty.setVisibility(View.VISIBLE);
            if (logCount != null) logCount.setText(getString(R.string.log_recent));
            return;
        }

        if (logEmpty != null) logEmpty.setVisibility(View.GONE);
        if (logCount != null) logCount.setText(getString(R.string.log_recent) + " (" + lines.size() + "/100)");

        int max = Math.min(lines.size(), 40);
        for (int i = lines.size() - 1, count = 0; i >= 0 && count < max; i--, count++) {
            String raw = lines.get(i);
            String[] parts = raw.split("\t", 3);
            String time = parts.length > 0 ? parts[0] : "";
            String tag = parts.length > 1 ? parts[1] : "事件";
            String detail = parts.length > 2 ? parts[2] : "";

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(5), 0, dp(5));

            TextView tvTime = new TextView(this);
            tvTime.setText(time);
            tvTime.setTextSize(11);
            tvTime.setTextColor(getColor(R.color.text_tertiary));
            tvTime.setTypeface(android.graphics.Typeface.MONOSPACE);
            row.addView(tvTime);

            TextView tvTag = new TextView(this);
            tvTag.setText(tag);
            tvTag.setTextSize(10);
            tvTag.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            tvTag.setPadding(dp(6), dp(1), dp(6), dp(1));
            LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            tagLp.setMargins(dp(8), 0, dp(8), 0);
            tvTag.setLayoutParams(tagLp);

            applyBadgeStyle(tvTag, tag);
            row.addView(tvTag);

            TextView tvDetail = new TextView(this);
            tvDetail.setText(detail);
            tvDetail.setTextSize(12);
            tvDetail.setTextColor(getColor(R.color.text_primary));
            tvDetail.setMaxLines(2);
            tvDetail.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams detLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            tvDetail.setLayoutParams(detLp);
            row.addView(tvDetail);

            logContainer.addView(row);

            if (count < max - 1) {
                View div = new View(this);
                div.setBackgroundColor(getColor(R.color.divider));
                LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                );
                divLp.setMargins(0, dp(2), 0, dp(2));
                div.setLayoutParams(divLp);
                logContainer.addView(div);
            }
        }
    }

    private void applyBadgeStyle(TextView tv, String tag) {
        if ("弹窗".equals(tag)) {
            tv.setBackgroundResource(R.drawable.bg_badge_popup);
            tv.setTextColor(getColor(R.color.badge_popup_text));
        } else if ("广告".equals(tag)) {
            tv.setBackgroundResource(R.drawable.bg_badge_ad);
            tv.setTextColor(getColor(R.color.badge_ad_text));
        } else if ("统计".equals(tag)) {
            tv.setBackgroundResource(R.drawable.bg_badge_stat);
            tv.setTextColor(getColor(R.color.badge_stat_text));
        } else if ("推广".equals(tag)) {
            tv.setBackgroundResource(R.drawable.bg_badge_promo);
            tv.setTextColor(getColor(R.color.badge_promo_text));
        } else if ("热搜".equals(tag)) {
            tv.setBackgroundResource(R.drawable.bg_badge_hotword);
            tv.setTextColor(getColor(R.color.badge_hotword_text));
        } else if ("白名单".equals(tag)) {
            tv.setBackgroundResource(R.drawable.bg_badge_white);
            tv.setTextColor(getColor(R.color.badge_white_text));
        } else {
            tv.setBackgroundResource(R.drawable.bg_chip);
            tv.setTextColor(getColor(R.color.accent));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLogs();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            EditText rules = (EditText) findViewById(R.id.rules);
            if (rules != null) sp.edit().putString(KEY_RULES, rules.getText().toString()).apply();
        } catch (Throwable t) { }
    }
}
