package com.example.signinginfooverride;

import java.util.*;

public final class AppCatalogTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    private static AppCatalog.Entry entry(String id, String label, AppCatalog.Category category, boolean enabled) {
        return new AppCatalog.Entry(id, label, 4294967297L, "2.7-beta", category, enabled);
    }
    public static void main(String[] args) {
        categories(); usersFirst(); stableLabels(); search();
        System.out.println("4 shared app catalog checks passed");
    }
    private static void categories() {
        check(AppCatalog.category(true, false, false) == AppCatalog.Category.USER);
        check(AppCatalog.category(true, true, true) == AppCatalog.Category.UPDATED_SYSTEM);
        check(AppCatalog.category(true, true, false) == AppCatalog.Category.SYSTEM);
        check(AppCatalog.category(false, false, false) == AppCatalog.Category.OTHER);
    }
    private static void usersFirst() {
        List<AppCatalog.Entry> apps = new ArrayList<>(Arrays.asList(
                entry("org.example.other", "A", AppCatalog.Category.OTHER, true),
                entry("org.example.system", "A", AppCatalog.Category.SYSTEM, true),
                entry("org.example.updated", "A", AppCatalog.Category.UPDATED_SYSTEM, true),
                entry("org.example.user", "Z", AppCatalog.Category.USER, true),
                entry("org.example.disabled", "Disabled user", AppCatalog.Category.USER, false)));
        apps.sort(AppCatalog.ORDER);
        check(apps.get(0).category == AppCatalog.Category.USER && apps.get(1).category == AppCatalog.Category.USER);
        check(apps.get(2).category == AppCatalog.Category.UPDATED_SYSTEM);
        check(apps.get(3).category == AppCatalog.Category.SYSTEM);
        check(apps.get(4).category == AppCatalog.Category.OTHER);
    }
    private static void stableLabels() {
        List<AppCatalog.Entry> apps = new ArrayList<>(Arrays.asList(
                entry("org.example.z", "alpha", AppCatalog.Category.USER, true),
                entry("org.example.b", "Beta", AppCatalog.Category.USER, true),
                entry("org.example.a", "Alpha", AppCatalog.Category.USER, true)));
        apps.sort(AppCatalog.ORDER);
        check(apps.get(0).packageName.equals("org.example.a"));
        check(apps.get(1).packageName.equals("org.example.z"));
        check(apps.get(2).packageName.equals("org.example.b"));
    }
    private static void search() {
        AppCatalog.Entry app = entry("org.example.system", "Camera App", AppCatalog.Category.SYSTEM, true);
        check(app.matches(" CAMERA ")); check(app.matches("EXAMPLE.SYSTEM"));
        check(app.matches("4294967297")); check(app.matches("2.7-beta")); check(!app.matches("missing"));
    }
}
