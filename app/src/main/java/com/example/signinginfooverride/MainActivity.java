package com.example.signinginfooverride;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.ByteArrayInputStream;
import java.security.cert.*;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

/** The manager and Xposed entry point ship in one APK. */
public final class MainActivity extends Activity {
    private static final int BG = 0xff101722, PANEL = 0xff1a2635, TEXT = 0xffedf4ff, MUTED = 0xffa7b9ce, ACCENT = 0xff74d9cb;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Item> items = new ArrayList<>();
    private OverrideStore store;
    private LinearLayout rows, details;
    private TextView status, count;
    private EditText search;
    private Button overridesButton, appsButton;
    private boolean installedTab, busy, storagePromptShown;
    private int generation;
    private Item selected;

    private static final class Item {
        String title, packageName, filename, error, versionName;
        long version;
        List<X509Certificate> certificates = new ArrayList<>();
        int signerCount;
        boolean installed;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new OverrideStore(this);
        if (state != null) {
            installedTab = state.getBoolean("installedTab");
            storagePromptShown = state.getBoolean("storagePromptShown");
        }
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        if (store == null) return;
        load();
        if (!StorageAccess.isGranted(this) && !storagePromptShown) {
            storagePromptShown = true;
            new AlertDialog.Builder(this).setTitle("Allow signature file access")
                    .setMessage("Allow read and write access to shared storage so Signature Overrides can read and save PEM files in "
                            + BuildConfig.OVERRIDE_DIRECTORY + ". You can still inspect installed app signers without this permission.")
                    .setNegativeButton("Later", null)
                    .setPositiveButton("Grant access", (d, w) -> fileAccess()).show();
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("installedTab", installedTab);
        state.putBoolean("storagePromptShown", storagePromptShown);
        super.onSaveInstanceState(state);
    }

    private void buildUi() {
        LinearLayout page = column(); page.setPadding(dp(28), dp(22), dp(28), dp(20)); page.setBackgroundColor(BG);
        TextView title = text("Signature Overrides", 28, TEXT); title.setTypeface(null, Typeface.BOLD); page.addView(title);
        page.addView(text("Public signing certificates  /  exact package + version matches", 15, MUTED));
        LinearLayout toolbar = row(); toolbar.setPadding(0, dp(14), 0, dp(12));
        overridesButton = button("Available overrides", () -> switchTab(false)); toolbar.addView(overridesButton);
        appsButton = button("Capture installed app", () -> switchTab(true)); toolbar.addView(appsButton);
        toolbar.addView(button("Refresh", this::load));
        toolbar.addView(button("File access", this::fileAccess));
        Switch logging = new Switch(this); logging.setText("Verbose module logging");
        logging.setTextColor(TEXT); logging.setMinHeight(dp(56)); logging.setPadding(dp(14), 0, 0, 0);
        logging.setChecked(ModuleSettings.verboseLogging(this));
        logging.setOnCheckedChangeListener((b, checked) -> {
            ModuleSettings.preferences(this).edit().putBoolean(ModuleSettings.VERBOSE_LOGGING, checked).apply();
            status.setText(checked ? "Verbose module logging enabled for subsequent package queries" : "Module logging disabled");
        });
        toolbar.addView(logging);
        HorizontalScrollView toolbarScroll = new HorizontalScrollView(this); toolbarScroll.addView(toolbar);
        page.addView(toolbarScroll);
        LinearLayout body = row();
        LinearLayout sidebar = column(); sidebar.setPadding(0, 0, dp(20), 0);
        search = new EditText(this); search.setSingleLine(true); search.setTextColor(TEXT); search.setHintTextColor(MUTED);
        search.setHint("Search app, package or version"); search.setTextSize(17); search.setMinHeight(dp(56)); sidebar.addView(search);
        count = text("", 14, MUTED); count.setPadding(dp(8), dp(8), 0, dp(8)); sidebar.addView(count);
        ScrollView list = new ScrollView(this); list.setFillViewport(true); rows = column(); list.addView(rows);
        sidebar.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        body.addView(sidebar, new LinearLayout.LayoutParams(0, -1, 0.43f));
        ScrollView detailScroll = new ScrollView(this); detailScroll.setBackground(card(PANEL)); detailScroll.setFillViewport(true);
        details = column(); details.setPadding(dp(28), dp(24), dp(28), dp(24)); detailScroll.addView(details);
        body.addView(detailScroll, new LinearLayout.LayoutParams(0, -1, 0.57f));
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        status = text("", 14, MUTED); status.setPadding(0, dp(12), 0, 0); page.addView(status);
        setContentView(page);
        page.setOnApplyWindowInsetsListener((v, insets) -> {
            page.setPadding(dp(28) + insets.getSystemWindowInsetLeft(), dp(22) + insets.getSystemWindowInsetTop(),
                    dp(28) + insets.getSystemWindowInsetRight(), dp(20) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { renderRows(); }
            public void afterTextChanged(Editable s) {}
        });
        emptyDetails();
    }

    private void switchTab(boolean installed) { installedTab = installed; selected = null; search.setText(""); load(); }

    private void load() {
        if (isFinishing() || busy) return;
        final int request = ++generation;
        final boolean apps = installedTab;
        busy = true; status.setText("Loading…");
        overridesButton.setEnabled(apps); appsButton.setEnabled(!apps);
        worker.execute(() -> {
            List<Item> loaded = new ArrayList<>(); String failure = null;
            try {
                if (apps) {
                    for (PackageInfo info : getPackageManager().getInstalledPackages(0)) {
                        Item item = new Item(); item.installed = true; item.packageName = info.packageName;
                        item.title = info.applicationInfo == null ? info.packageName : info.applicationInfo.loadLabel(getPackageManager()).toString();
                        item.version = info.getLongVersionCode(); item.versionName = info.versionName;
                        loaded.add(item);
                    }
                    loaded.sort(Comparator.comparing(i -> i.title.toLowerCase(Locale.ROOT)));
                } else {
                    for (String filename : store.names()) {
                        Item item = new Item(); item.filename = filename; item.title = filename;
                        try {
                            OverrideFiles.Key key = OverrideFiles.parse(filename);
                            item.packageName = key.packageName; item.title = key.packageName; item.version = key.version;
                            item.certificates = OverrideFiles.certificates(store.read(filename)); item.signerCount = item.certificates.size();
                        } catch (Exception e) { item.error = message(e); }
                        loaded.add(item);
                    }
                }
            } catch (Exception e) { failure = message(e); }
            final String error = failure;
            runOnUiThread(() -> {
                busy = false;
                if (request != generation || isDestroyed()) return;
                if (apps != installedTab) { load(); return; }
                items.clear(); items.addAll(loaded); selected = null; renderRows(); emptyDetails();
                status.setText(error == null ? BuildConfig.OVERRIDE_DIRECTORY + "  •  " + (apps ? "Choose an app to inspect its signer" : "Changes apply on the next uncached package query")
                        : "Folder unavailable: " + error + "  •  Use File access to grant storage permission, then refresh.");
            });
        });
    }

    private void renderRows() {
        if (rows == null) return;
        rows.removeAllViews(); int shown = 0;
        String query = search.getText().toString().toLowerCase(Locale.ROOT).trim();
        for (Item item : items) {
            String searchable = item.title + " " + item.packageName + " " + item.version;
            if (!searchable.toLowerCase(Locale.ROOT).contains(query)) continue;
            shown++;
            Button entry = button(item.title + "\n" + (item.installed ? item.packageName + "  •  " : "")
                    + "Version " + item.version + (item.error == null ? "" : "  •  Invalid PEM"), () -> select(item));
            entry.setAllCaps(false); entry.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); entry.setTextSize(16);
            entry.setPadding(dp(16), dp(14), dp(16), dp(14)); entry.setBackground(card(selected == item ? 0xff294e59 : PANEL));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(8); rows.addView(entry, params);
        }
        count.setText(shown + (installedTab ? " installed apps" : " overrides"));
        if (shown == 0) rows.addView(text(query.isEmpty() ? (installedTab ? "No visible installed apps." : "No overrides yet. Capture an installed app to get started.") : "No matches.", 17, MUTED));
    }

    private void emptyDetails() {
        details.removeAllViews(); details.addView(text(installedTab ? "Capture a signing certificate" : "Your signature library", 25, TEXT));
        detail("", installedTab ? "Select an installed app on the left. Its public signing certificate can be saved without backing up the app or reading its private data."
                : "Select an override to see its signer, certificate validity and SHA-256 fingerprint.");
        detail("File format", "[bundle id]+[numeric versionCode].pem");
        detail("Capture the original", "Capture before replacing or re-signing an app. A modified installation only exposes its current signer.");
    }

    private void select(Item item) {
        selected = item; renderRows();
        if (!item.installed) { showDetails(item); return; }
        details.removeAllViews(); details.addView(text("Reading signer…", 24, TEXT));
        worker.execute(() -> {
            try {
                PackageInfo info = getPackageManager().getPackageInfo(item.packageName, PackageManager.GET_SIGNING_CERTIFICATES);
                item.version = info.getLongVersionCode(); item.versionName = info.versionName;
                Signature[] signers = info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
                item.signerCount = signers == null ? 0 : signers.length;
                if (item.signerCount == 0) throw new IllegalArgumentException("No current signers returned by Android.");
                item.certificates = new ArrayList<>();
                for (Signature signer : signers) item.certificates.add((X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(signer.toByteArray())));
                item.filename = OverrideFiles.filename(item.packageName, item.version); item.error = null;
            } catch (Exception e) { item.error = message(e); }
            runOnUiThread(() -> { if (!isDestroyed() && selected == item) showDetails(item); });
        });
    }

    private void showDetails(Item item) {
        details.removeAllViews(); details.addView(text(item.title, 25, TEXT));
        detail("Package", item.packageName == null ? "Invalid filename" : item.packageName);
        detail("Version", Long.toString(item.version) + (item.versionName == null ? "" : "  (" + item.versionName + ")"));
        detail("Override file", item.filename == null ? "Not available" : item.filename);
        if (item.error != null) { detail("Cannot use this entry", item.error); return; }
        detail("Current signers", Integer.toString(item.signerCount));
        for (X509Certificate cert : item.certificates) {
            detail("Signer / subject", cert.getSubjectX500Principal().getName());
            detail("Issuer", cert.getIssuerX500Principal().getName());
            DateFormat date = DateFormat.getDateInstance(DateFormat.MEDIUM);
            detail("Certificate validity", date.format(cert.getNotBefore()) + " — " + date.format(cert.getNotAfter()));
            detail("Serial / public key", cert.getSerialNumber().toString(16) + "  /  " + cert.getPublicKey().getAlgorithm());
            try { detail("SHA-256 fingerprint", OverrideFiles.fingerprint(cert)); } catch (Exception e) { detail("Fingerprint error", message(e)); }
        }
        if (item.installed) {
            Button capture = button("Capture to SignatureOverrides", () -> prepareCapture(item)); capture.setTextColor(ACCENT); details.addView(capture);
            detail("Access", "Signer details use Android's package API. Storage permission is required to save the public certificate PEM files.");
        }
    }

    private void prepareCapture(Item item) {
        if (!StorageAccess.isGranted(this)) { fileAccess(); return; }
        if (busy) return;
        busy = true; status.setText("Checking destination…");
        worker.execute(() -> {
            try {
                boolean exists = store.names().contains(item.filename);
                runOnUiThread(() -> {
                    busy = false;
                    if (isDestroyed()) return;
                    if (exists) new AlertDialog.Builder(this).setTitle("Replace existing override?")
                            .setMessage(item.filename + " already exists. Replace it with this installed app's current signer?")
                            .setNegativeButton("Cancel", (d, w) -> status.setText("Capture cancelled"))
                            .setPositiveButton("Replace", (d, w) -> capture(item, true)).show();
                    else capture(item, false);
                });
            } catch (Exception e) { runOnUiThread(() -> { busy = false; status.setText("Cannot access destination: " + message(e)); }); }
        });
    }

    private void capture(Item item, boolean replace) {
        if (busy) return;
        busy = true; status.setText("Saving certificate…");
        worker.execute(() -> {
            try {
                // Refuse a stale selection if the installed app changed while the details were open.
                PackageInfo current = getPackageManager().getPackageInfo(item.packageName, PackageManager.GET_SIGNING_CERTIFICATES);
                Signature[] signers = current.signingInfo == null ? null : current.signingInfo.getApkContentsSigners();
                if (current.getLongVersionCode() != item.version || signers == null || signers.length != item.certificates.size()) {
                    throw new IllegalStateException("App changed. Select it again before capturing.");
                }
                for (int i = 0; i < signers.length; i++) {
                    if (!Arrays.equals(signers[i].toByteArray(), item.certificates.get(i).getEncoded()))
                        throw new IllegalStateException("Signer changed. Select the app again before capturing.");
                }
                store.save(item.filename, OverrideFiles.pem(item.certificates), replace);
                runOnUiThread(() -> { busy = false; status.setText("Saved " + item.filename); });
            } catch (Exception e) { runOnUiThread(() -> { busy = false; status.setText("Capture failed: " + message(e)); }); }
        });
    }

    private void fileAccess() {
        storagePromptShown = true;
        if (Build.VERSION.SDK_INT >= 30) {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))); }
            catch (ActivityNotFoundException e) {
                try { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
                catch (ActivityNotFoundException unavailable) { openAppSettings(); }
            }
        } else {
            boolean asked = getPreferences(MODE_PRIVATE).getBoolean("storagePermissionsRequested", false);
            if (asked && !StorageAccess.isGranted(this)
                    && !shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                openAppSettings();
                return;
            }
            getPreferences(MODE_PRIVATE).edit().putBoolean("storagePermissionsRequested", true).apply();
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    private void openAppSettings() {
        try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
        catch (ActivityNotFoundException unavailable) { status.setText("Open system app settings and grant Signature Overrides storage access to read and save PEM files."); }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 1) {
            if (StorageAccess.isGranted(this)) load();
            else status.setText("Storage access was not granted. Use File access to enable reading and saving PEM files. Installed app inspection is still available.");
        }
    }
    @Override protected void onDestroy() { generation++; worker.shutdownNow(); super.onDestroy(); }
    private void detail(String label, String value) {
        if (!label.isEmpty()) { TextView heading = text(label.toUpperCase(Locale.ROOT), 12, ACCENT); heading.setPadding(0, dp(19), 0, dp(5)); details.addView(heading); }
        TextView content = text(value, 17, TEXT); content.setTextIsSelectable(true); details.addView(content);
    }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private TextView text(String value, int size, int color) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); return v; }
    private Button button(String label, Runnable action) { Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(TEXT); b.setMinHeight(dp(56)); b.setOnClickListener(v -> action.run()); return b; }
    private GradientDrawable card(int color) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(14)); return d; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static String message(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
}
