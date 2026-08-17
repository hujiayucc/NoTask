package com.hujiayucc.notask;

import android.content.SharedPreferences;

public final class ModuleConfig {
    public static final String PREFERENCES_NAME = "config";
    public static final int MODE_BACK = 1;
    public static final int MODE_BACKGROUND = 2;

    private static final String MODE_KEY_PREFIX = "hide_mode.";

    private ModuleConfig() {
    }

    public static String modeKey(String packageName) {
        return MODE_KEY_PREFIX + packageName;
    }

    public static int normalizeMode(int mode) {
        return mode == MODE_BACKGROUND ? MODE_BACKGROUND : MODE_BACK;
    }

    public static int readMode(SharedPreferences preferences, String packageName) {
        if (preferences == null) {
            return MODE_BACK;
        }
        try {
            return normalizeMode(preferences.getInt(modeKey(packageName), MODE_BACK));
        } catch (RuntimeException ignored) {
            return MODE_BACK;
        }
    }
}