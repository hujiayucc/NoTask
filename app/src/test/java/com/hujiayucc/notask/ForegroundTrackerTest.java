package com.hujiayucc.notask;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ForegroundTrackerTest {
    @Test
    public void reportsBackgroundOnlyAfterLastActivityStops() {
        ForegroundTracker tracker = new ForegroundTracker();
        Object first = new Object();
        Object second = new Object();

        tracker.onStarted(first);
        tracker.onStarted(second);

        assertFalse(tracker.onStopped(first));
        assertEquals(1, tracker.getStartedCount());
        assertTrue(tracker.onStopped(second));
        assertTrue(tracker.isBackground());
    }

    @Test
    public void duplicateStartDoesNotInflateCount() {
        ForegroundTracker tracker = new ForegroundTracker();
        Object activity = new Object();

        tracker.onStarted(activity);
        tracker.onStarted(activity);

        assertEquals(1, tracker.getStartedCount());
        assertTrue(tracker.onStopped(activity));
    }
}