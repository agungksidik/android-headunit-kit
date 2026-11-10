package com.agung.headhome;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.GridView;

import java.util.List;

/** Dark all-apps grid. Tap launches the app, the back arrow or Back returns to Home. */
public class AllAppsActivity extends Activity {

    private GridView grid;
    private View loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.apps_title);
        setContentView(R.layout.activity_apps);
        ((android.widget.TextView) findViewById(R.id.header_title)).setText(R.string.apps_title);
        findViewById(R.id.header_back).setOnClickListener(v -> finish());
        grid = findViewById(R.id.apps_grid);
        loading = findViewById(R.id.apps_loading);

        List<AppList.Item> cached = AppList.cache;
        if (cached != null) {
            show(cached);
            return;
        }
        grid.setVisibility(View.INVISIBLE);
        new Thread(() -> {
            List<AppList.Item> items = AppList.get(getApplicationContext());
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    show(items);
                }
            });
        }, "all-apps").start();
    }

    private void show(List<AppList.Item> items) {
        loading.setVisibility(View.GONE);
        grid.setVisibility(View.VISIBLE);
        grid.setAdapter(new AppAdapter(this, items, R.layout.item_app_cell));
        grid.setOnItemClickListener((parent, view, position, id) -> {
            if (Launch.start(this, items.get(position).component)) {
                finish();
            }
        });
    }
}
