package com.agung.headhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;

import java.util.List;
import java.util.function.Consumer;

/** Dark dialog listing every launchable activity (from the shared cache) or every HOME activity. */
final class AppPicker {

    private AppPicker() {
    }

    static void show(Activity activity, Consumer<ComponentName> onPicked) {
        List<AppList.Item> cached = AppList.cache;
        if (cached != null) {
            showDialog(activity, cached, R.string.choose_app, onPicked);
            return;
        }
        new Thread(() -> {
            List<AppList.Item> items = AppList.get(activity);
            activity.runOnUiThread(() -> showDialog(activity, items, R.string.choose_app, onPicked));
        }, "app-picker").start();
    }

    /** Lists the HOME activities for the default launcher setting. */
    static void showHome(Activity activity, Consumer<AppList.Item> onPicked) {
        new Thread(() -> {
            List<AppList.Item> items = AppList.loadHome(activity.getPackageManager());
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                build(activity, items, R.string.settings_launcher, onPicked).show();
            });
        }, "home-picker").start();
    }

    private static void showDialog(Activity activity, List<AppList.Item> items, int title,
                                   Consumer<ComponentName> onPicked) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        build(activity, items, title, item -> onPicked.accept(item.component)).show();
    }

    private static AlertDialog build(Activity activity, List<AppList.Item> items, int title,
                                     Consumer<AppList.Item> onPicked) {
        return new AlertDialog.Builder(activity, R.style.Theme_HeadHome_Dialog)
                .setTitle(title)
                .setAdapter(new AppAdapter(activity, items, R.layout.item_app_row),
                        (dialog, which) -> onPicked.accept(items.get(which)))
                .setNegativeButton(R.string.cancel, null)
                .create();
    }
}
