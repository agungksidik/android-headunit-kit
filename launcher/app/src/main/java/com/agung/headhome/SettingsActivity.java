package com.agung.headhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Launcher settings: default Home, Android settings, shortcut reset, restart and version. */
public class SettingsActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView launcherSubtitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.settings_title);
        setContentView(R.layout.activity_settings);
        ((TextView) findViewById(R.id.header_title)).setText(R.string.settings_title);
        findViewById(R.id.header_back).setOnClickListener(v -> finish());

        LinearLayout list = findViewById(R.id.settings_list);
        View launcher = addRow(list, R.drawable.ic_home, R.string.settings_launcher, "", true);
        launcherSubtitle = launcher.findViewById(R.id.setting_subtitle);
        launcher.setOnClickListener(v -> pickLauncher());

        addRow(list, R.drawable.ic_settings, R.string.settings_android, null, true)
                .setOnClickListener(v -> Launch.start(this, Launch.ANDROID_SETTINGS));
        View rescan = addRow(list, R.drawable.ic_refresh, R.string.settings_rescan, null, true);
        rescan.setOnClickListener(v -> {
            PlayerService p = PlayerService.get();
            if (p != null) {
                p.rescan();
                toast(this, getString(R.string.scanning_music));
            }
        });
        View resume = addRow(list, R.drawable.ic_play, R.string.settings_autoresume,
                getString(PlayerService.autoResumeEnabled(this) ? R.string.on : R.string.off), true);
        resume.setOnClickListener(v -> {
            boolean on = !PlayerService.autoResumeEnabled(this);
            PlayerService.setAutoResume(this, on);
            ((TextView) v.findViewById(R.id.setting_subtitle)).setText(on ? R.string.on : R.string.off);
        });
        addRow(list, R.drawable.ic_refresh, R.string.settings_reset, null, true)
                .setOnClickListener(v -> confirmReset());
        addRow(list, R.drawable.ic_power, R.string.settings_restart, null, true)
                .setOnClickListener(v -> RestartDialog.show(this));
        addRow(list, R.drawable.ic_info, R.string.settings_version, versionName(), false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        launcherSubtitle.setText(currentLauncherLabel());
    }

    private View addRow(LinearLayout list, int icon, int title, String subtitle, boolean clickable) {
        if (list.getChildCount() > 0) {
            View divider = new View(this);
            divider.setBackgroundColor(getColor(R.color.card_border));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (int) getResources().getDisplayMetrics().density);
            int margin = (int) (24 * getResources().getDisplayMetrics().density);
            lp.setMarginStart(margin);
            lp.setMarginEnd(margin);
            list.addView(divider, lp);
        }
        View row = LayoutInflater.from(this).inflate(R.layout.item_setting, list, false);
        ((ImageView) row.findViewById(R.id.setting_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.setting_title)).setText(title);
        TextView sub = row.findViewById(R.id.setting_subtitle);
        if (subtitle == null) {
            sub.setVisibility(View.GONE);
        } else {
            sub.setText(subtitle);
        }
        row.setClickable(clickable);
        row.setFocusable(clickable);
        if (!clickable) {
            row.setBackground(null);
        }
        list.addView(row);
        return row;
    }

    private String versionName() {
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return v != null ? v : "";
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    // ---- default launcher ----

    private CharSequence currentLauncherLabel() {
        PackageManager pm = getPackageManager();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        if (info == null || info.activityInfo == null || "android".equals(info.activityInfo.packageName)) {
            return getString(R.string.settings_launcher_none);
        }
        return info.loadLabel(pm);
    }

    private void pickLauncher() {
        AppPicker.showHome(this, this::setDefaultLauncher);
    }

    private void setDefaultLauncher(AppList.Item item) {
        final Context app = getApplicationContext();
        final ComponentName cn = item.component;
        final String label = item.label.toString();
        // Single quotes keep "$" in class names from being expanded by the shell.
        final String cmd = "cmd package set-home-activity '" + cn.flattenToString() + "'"
                + " && setprop persist.nwd.launcher.default " + cn.getPackageName();
        new Thread(() -> {
            try {
                String out = LocalAdb.exec(app.getFilesDir(), cmd);
                if (looksLikeFailure(out)) {
                    handler.post(() -> toast(app, app.getString(R.string.action_failed, out)));
                    return;
                }
                handler.post(() -> {
                    toast(app, app.getString(R.string.launcher_changed, label));
                    Intent home = new Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    app.startActivity(home);
                });
            } catch (Exception e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                handler.post(() -> toast(app, app.getString(R.string.action_failed, reason)));
            }
        }, "adb-set-home").start();
    }

    private static boolean looksLikeFailure(String output) {
        String o = output.toLowerCase(Locale.ROOT);
        return o.contains("exception") || o.contains("error") || o.contains("not found");
    }

    private static void toast(Context ctx, String text) {
        Toast.makeText(ctx, text, Toast.LENGTH_LONG).show();
    }

    // ---- shortcuts ----

    private void confirmReset() {
        new AlertDialog.Builder(this, R.style.Theme_HeadHome_Dialog)
                .setTitle(R.string.reset_title)
                .setMessage(R.string.reset_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.reset_confirm,
                        (d, w) -> getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit().clear().apply())
                .create()
                .show();
    }
}
