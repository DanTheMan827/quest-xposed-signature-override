package com.example.signinginfooverride;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** One searchable, categorized selector shared by capture and both swap ID pickers. */
final class AppSelector extends LinearLayout {
    private static final int TEXT = 0xffedf4ff, MUTED = 0xffa7b9ce, ACCENT = 0xff74d9cb;
    private final List<AppCatalog.Entry> apps = new ArrayList<>();
    private final EnumSet<AppCatalog.Category> expanded = EnumSet.of(AppCatalog.Category.USER);
    private final EnumSet<AppCatalog.Category> searchCollapsed = EnumSet.noneOf(AppCatalog.Category.class);
    private final EditText search;
    private final TextView count;
    private final LinearLayout rows;
    private Consumer<AppCatalog.Entry> onSelected;
    private String selectedPackage;

    AppSelector(Context context) {
        super(context); setOrientation(VERTICAL);
        search = new EditText(context); search.setSingleLine(true); search.setTextColor(TEXT);
        search.setHintTextColor(MUTED); search.setTextSize(17); search.setMinHeight(dp(56));
        search.setHint("Search app, package or version"); addView(search);
        count = new TextView(context); count.setTextColor(MUTED); count.setPadding(dp(8), dp(8), 0, dp(8)); addView(count);
        ScrollView scroll = new ScrollView(context); scroll.setFillViewport(true);
        rows = new LinearLayout(context); rows.setOrientation(VERTICAL); scroll.addView(rows);
        addView(scroll, new LayoutParams(-1, 0, 1));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { searchCollapsed.clear(); render(); }
            public void afterTextChanged(Editable s) {}
        });
        render();
    }

    static List<AppCatalog.Entry> query(PackageManager manager) {
        List<AppCatalog.Entry> result = new ArrayList<>();
        for (PackageInfo info : manager.getInstalledPackages(0)) {
            ApplicationInfo app = info.applicationInfo;
            String label = app == null ? info.packageName : app.loadLabel(manager).toString();
            AppCatalog.Category category = AppCatalog.category(app != null,
                    app != null && (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0,
                    app != null && (app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0);
            result.add(new AppCatalog.Entry(info.packageName, label, info.getLongVersionCode(), info.versionName,
                    category, app == null || app.enabled));
        }
        result.sort(AppCatalog.ORDER); return result;
    }

    void setApps(List<AppCatalog.Entry> entries) {
        apps.clear(); apps.addAll(entries); apps.sort(AppCatalog.ORDER);
        if (selectedPackage != null) for (AppCatalog.Entry app : apps)
            if (selectedPackage.equals(app.packageName)) expanded.add(app.category);
        render();
    }
    void setOnSelected(Consumer<AppCatalog.Entry> listener) { onSelected = listener; }
    void setSelectedPackage(String packageName) { selectedPackage = packageName; render(); }
    void showMessage(String message) { count.setText(message); rows.removeAllViews(); }

    private void render() {
        rows.removeAllViews(); String query = search.getText().toString().trim(); int matches = 0;
        for (AppCatalog.Category category : AppCatalog.Category.values()) {
            List<AppCatalog.Entry> group = new ArrayList<>();
            for (AppCatalog.Entry app : apps) if (app.category == category && app.matches(query)) group.add(app);
            if (group.isEmpty()) continue;
            matches += group.size();
            boolean open = query.isEmpty() ? expanded.contains(category) : !searchCollapsed.contains(category);
            Button header = button((open ? "▾  " : "▸  ") + category.label + "  (" + group.size() + ")");
            header.setTextColor(ACCENT); header.setContentDescription((open ? "Collapse " : "Expand ") + category.label);
            header.setOnClickListener(v -> {
                if (query.isEmpty()) { if (!expanded.remove(category)) expanded.add(category); }
                else { if (!searchCollapsed.remove(category)) searchCollapsed.add(category); }
                render();
            });
            rows.addView(header, new LayoutParams(-1, -2));
            if (!open) continue;
            for (AppCatalog.Entry app : group) {
                Button entry = button(app.label + "\n" + app.packageName + "  •  " + app.version + (app.enabled ? "" : "  •  Disabled"));
                GradientDrawable background = new GradientDrawable();
                background.setColor(app.packageName.equals(selectedPackage) ? 0xff294e59 : 0xff1a2635); background.setCornerRadius(dp(14));
                entry.setBackground(background); entry.setOnClickListener(v -> { if (onSelected != null) onSelected.accept(app); });
                LayoutParams params = new LayoutParams(-1, -2); params.bottomMargin = dp(8); rows.addView(entry, params);
            }
        }
        count.setText(matches + " installed apps");
        if (matches == 0) { TextView empty = new TextView(getContext()); empty.setTextColor(MUTED);
            empty.setText(query.isEmpty() ? "No installed apps available." : "No matching apps."); rows.addView(empty); }
    }

    static void showPicker(Activity activity, String title, String selectedPackage, Executor worker, Consumer<AppCatalog.Entry> selected) {
        AppSelector selector = new AppSelector(activity); selector.setSelectedPackage(selectedPackage); selector.setPadding(selector.dp(16), 0, selector.dp(16), 0);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(title).setView(selector).setNegativeButton("Cancel", null).create();
        selector.setOnSelected(app -> { dialog.dismiss(); selected.accept(app); });
        dialog.setOnShowListener(d -> {
            int width = Math.min(activity.getResources().getDisplayMetrics().widthPixels * 9 / 10, selector.dp(1000));
            int height = activity.getResources().getDisplayMetrics().heightPixels * 85 / 100;
            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(width, height);
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            }
        });
        dialog.show(); selector.showMessage("Loading installed apps…");
        worker.execute(() -> {
            try {
                List<AppCatalog.Entry> entries = query(activity.getPackageManager());
                activity.runOnUiThread(() -> { if (!activity.isDestroyed() && dialog.isShowing()) selector.setApps(entries); });
            } catch (Exception error) {
                activity.runOnUiThread(() -> { if (!activity.isDestroyed() && dialog.isShowing()) selector.showMessage("Cannot load apps: " + error.getClass().getSimpleName()); });
            }
        });
    }

    private Button button(String label) {
        Button button = new Button(getContext()); button.setText(label); button.setAllCaps(false); button.setTextColor(TEXT);
        button.setTextSize(16); button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); button.setMinHeight(dp(56));
        button.setPadding(dp(16), dp(12), dp(16), dp(12)); return button;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
