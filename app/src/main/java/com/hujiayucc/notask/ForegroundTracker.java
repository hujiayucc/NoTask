package com.hujiayucc.notask;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

final class ForegroundTracker {
    private final Set<Object> startedActivities = Collections.newSetFromMap(new IdentityHashMap<>());

    synchronized void onStarted(Object activity) {
        startedActivities.add(activity);
    }

    synchronized boolean onStopped(Object activity) {
        startedActivities.remove(activity);
        return startedActivities.isEmpty();
    }

    synchronized boolean isBackground() {
        return startedActivities.isEmpty();
    }

    synchronized int getStartedCount() {
        return startedActivities.size();
    }
}