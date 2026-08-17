package com.hujiayucc.notask;

import android.graphics.drawable.Drawable;

final class AppEntry {
    final String label;
    final String packageName;
    final Drawable icon;

    boolean scoped;
    int mode = ModuleConfig.MODE_BACK;

    AppEntry(String label, String packageName, Drawable icon) {
        this.label = label;
        this.packageName = packageName;
        this.icon = icon;
    }
}