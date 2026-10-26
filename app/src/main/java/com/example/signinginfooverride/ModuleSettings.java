package com.example.signinginfooverride;

import android.content.Context;
import android.content.SharedPreferences;

/** Only the companion activity writes these private settings. */
final class ModuleSettings {
    static final String VERBOSE_LOGGING = "verbose_logging";

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("ModuleSettings", Context.MODE_PRIVATE);
    }

    static boolean verboseLogging(Context context) {
        return preferences(context).getBoolean(VERBOSE_LOGGING, false);
    }
}
