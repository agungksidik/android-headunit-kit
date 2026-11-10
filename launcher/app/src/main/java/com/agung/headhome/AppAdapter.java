package com.agung.headhome;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/** Icon and label rows for the app picker and the all-apps grid. Layouts must define app_icon and app_label. */
final class AppAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private final List<AppList.Item> items;
    private final int layout;

    AppAdapter(Context ctx, List<AppList.Item> items, int layout) {
        this.inflater = LayoutInflater.from(ctx);
        this.items = items;
        this.layout = layout;
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public AppList.Item getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView != null ? convertView : inflater.inflate(layout, parent, false);
        AppList.Item item = items.get(position);
        ((ImageView) v.findViewById(R.id.app_icon)).setImageDrawable(item.icon);
        ((TextView) v.findViewById(R.id.app_label)).setText(item.label);
        return v;
    }
}
