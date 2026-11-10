package com.agung.headhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Every launchable activity on the unit, sorted by label, with a process-wide cache. */
final class AppList {

    static final class Item {
        final CharSequence label;
        final Drawable icon;
        final ComponentName component;

        Item(CharSequence label, Drawable icon, ComponentName component) {
            this.label = label;
            this.icon = icon;
            this.component = component;
        }
    }

    /** The loaded launcher list, or null when it has not been loaded or was invalidated. */
    static volatile List<Item> cache;

    private static final Object LOAD_LOCK = new Object();

    private AppList() {
    }

    /** Returns the cached list, loading it (blocking) when missing. Call off the UI thread. */
    static List<Item> get(Context ctx) {
        List<Item> c = cache;
        if (c != null) {
            return c;
        }
        synchronized (LOAD_LOCK) {
            if (cache == null) {
                cache = load(ctx.getPackageManager());
            }
            return cache;
        }
    }

    /** Fills the cache on a background thread when it is empty. */
    static void preload(Context ctx) {
        if (cache != null) {
            return;
        }
        final Context app = ctx.getApplicationContext();
        new Thread(() -> get(app), "app-list").start();
    }

    /** Drops the cache and rebuilds it in the background. */
    static void invalidate(Context ctx) {
        cache = null;
        preload(ctx);
    }

    static List<Item> load(PackageManager pm) {
        return query(pm, Intent.CATEGORY_LAUNCHER);
    }

    /** Every HOME activity, for the default launcher picker. */
    static List<Item> loadHome(PackageManager pm) {
        return query(pm, Intent.CATEGORY_HOME);
    }

    private static List<Item> query(PackageManager pm, String category) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> infos = pm.queryIntentActivities(intent, 0);
        List<Item> items = new ArrayList<>(infos.size());
        for (ResolveInfo info : infos) {
            ComponentName cn = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
            items.add(new Item(info.loadLabel(pm), info.loadIcon(pm), cn));
        }
        Collections.sort(items, (a, b) ->
                a.label.toString().toLowerCase(Locale.ROOT).compareTo(b.label.toString().toLowerCase(Locale.ROOT)));
        return items;
    }
}
