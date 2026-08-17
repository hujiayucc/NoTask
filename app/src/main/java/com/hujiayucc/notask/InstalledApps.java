package com.hujiayucc.notask;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class InstalledApps {
    private InstalledApps() {
    }

    static List<AppEntry> load(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launchers;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launchers = packageManager.queryIntentActivities(
                    launcherIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL)
            );
        } else {
            launchers = packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL);
        }

        Map<String, AppEntry> entries = new LinkedHashMap<>();
        for (ResolveInfo resolveInfo : launchers) {
            if (resolveInfo.activityInfo == null || resolveInfo.activityInfo.applicationInfo == null) {
                continue;
            }
            String packageName = resolveInfo.activityInfo.packageName;
            if (context.getPackageName().equals(packageName) || entries.containsKey(packageName)) {
                continue;
            }

            CharSequence rawLabel = resolveInfo.loadLabel(packageManager);
            String label = rawLabel == null ? packageName : rawLabel.toString().trim();
            if (label.isEmpty()) {
                label = packageName;
            }
            Drawable icon = resolveInfo.loadIcon(packageManager);
            if (icon == null) {
                icon = packageManager.getDefaultActivityIcon();
            }
            entries.put(packageName, new AppEntry(label, packageName, icon));
        }

        List<AppEntry> result = new ArrayList<>(entries.values());
        Collator collator = Collator.getInstance(Locale.getDefault());
        result.sort((left, right) -> collator.compare(left.label, right.label));
        return result;
    }
}