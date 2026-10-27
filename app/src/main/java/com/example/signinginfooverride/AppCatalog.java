package com.example.signinginfooverride;

import java.util.Comparator;
import java.util.Locale;

/** Shared list model and ordering for every installed-app selector. */
final class AppCatalog {
    enum Category {
        USER("User apps"), UPDATED_SYSTEM("Updated system apps"), SYSTEM("System apps"), OTHER("Other apps");
        final String label;
        Category(String label) { this.label = label; }
    }

    static final class Entry {
        final String packageName, label, versionName;
        final long version;
        final Category category;
        final boolean enabled;
        Entry(String packageName, String label, long version, String versionName, Category category, boolean enabled) {
            this.packageName = packageName; this.label = label; this.version = version;
            this.versionName = versionName; this.category = category; this.enabled = enabled;
        }
        boolean matches(String query) {
            String needle = query.toLowerCase(Locale.ROOT).trim();
            return (label + " " + packageName + " " + version + " " + (versionName == null ? "" : versionName))
                    .toLowerCase(Locale.ROOT).contains(needle);
        }
    }

    static Category category(boolean hasApplicationInfo, boolean system, boolean updatedSystem) {
        if (!hasApplicationInfo) return Category.OTHER;
        if (updatedSystem) return Category.UPDATED_SYSTEM;
        return system ? Category.SYSTEM : Category.USER;
    }

    static final Comparator<Entry> ORDER = Comparator.comparing((Entry app) -> app.category.ordinal())
            .thenComparing(app -> app.label.toLowerCase(Locale.ROOT)).thenComparing(app -> app.packageName);
}
