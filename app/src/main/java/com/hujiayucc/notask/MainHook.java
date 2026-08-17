package com.hujiayucc.notask;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class MainHook extends XposedModule {
    private static final String TAG = "NoTask";
    private static final String MODULE_PACKAGE = "com.hujiayucc.notask";
    private static final long BACKGROUND_CONFIRM_DELAY_MS = 180L;

    private final AtomicBoolean hooksInstalled = new AtomicBoolean(false);
    private final ForegroundTracker foregroundTracker = new ForegroundTracker();

    private volatile String processName = "";
    private volatile String targetPackageName = "";
    private volatile boolean systemServer;
    private volatile int hideMode = ModuleConfig.MODE_BACK;

    private SharedPreferences preferences;
    private SharedPreferences.OnSharedPreferenceChangeListener preferencesListener;
    private Handler mainHandler;
    private WeakReference<Activity> lastActivity = new WeakReference<>(null);
    private Runnable pendingBackgroundHide;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        processName = param.getProcessName();
        systemServer = param.isSystemServer();
        initializeRemotePreferences();
        log(Log.INFO, TAG, "event=module_loaded result=ok process=" + processName
                + " api=" + getApiVersion()
                + " framework=" + getFrameworkName()
                + " version=" + getFrameworkVersion());
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        String packageName = param.getPackageName();
        if (systemServer) {
            logRouteSkip(packageName, "system_server");
            return;
        }
        if (!param.isFirstPackage()) {
            logRouteSkip(packageName, "not_first_package");
            return;
        }
        if (MODULE_PACKAGE.equals(packageName)) {
            logRouteSkip(packageName, "module_package");
            return;
        }
        if (!isPackageProcess(packageName, processName)) {
            logRouteSkip(packageName, "process_mismatch");
            return;
        }

        targetPackageName = packageName;
        mainHandler = new Handler(Looper.getMainLooper());
        refreshHideMode("package_ready");
        installHooks(packageName);
    }

    private void initializeRemotePreferences() {
        try {
            preferences = getRemotePreferences(ModuleConfig.PREFERENCES_NAME);
            preferencesListener = (sharedPreferences, key) -> {
                String packageName = targetPackageName;
                if (packageName.isEmpty()
                        || key == null
                        || ModuleConfig.modeKey(packageName).equals(key)) {
                    refreshHideMode("preferences_changed");
                }
            };
            preferences.registerOnSharedPreferenceChangeListener(preferencesListener);
            log(Log.INFO, TAG, "event=config_init result=ok process=" + processName);
        } catch (RuntimeException error) {
            preferences = null;
            preferencesListener = null;
            hideMode = ModuleConfig.MODE_BACK;
            log(Log.WARN, TAG, "event=config_init result=fallback process=" + processName
                    + " mode=back", error);
        }
    }

    private void refreshHideMode(String reason) {
        String packageName = targetPackageName;
        if (packageName.isEmpty()) {
            return;
        }
        int newMode = ModuleConfig.readMode(preferences, packageName);
        int oldMode = hideMode;
        hideMode = newMode;
        if (oldMode != newMode) {
            log(Log.INFO, TAG, "event=config_changed result=ok package=" + packageName
                    + " process=" + processName + " mode=" + modeName(newMode)
                    + " reason=" + reason);
            Handler handler = mainHandler;
            if (handler != null) {
                handler.post(() -> reconcileMode(newMode));
            }
        } else {
            log(Log.DEBUG, TAG, "event=config_read result=ok package=" + packageName
                    + " process=" + processName + " mode=" + modeName(newMode)
                    + " reason=" + reason);
        }
    }

    private void reconcileMode(int mode) {
        cancelPendingBackgroundHide();
        Activity activity = lastActivity.get();
        if (activity == null) {
            return;
        }
        if (mode == ModuleConfig.MODE_BACKGROUND && foregroundTracker.isBackground()) {
            scheduleBackgroundHide(activity, "mode_changed_in_background");
        } else if (mode == ModuleConfig.MODE_BACK) {
            excludeFromRecent(activity, false, "mode_changed_to_back");
        }
    }

    private void logRouteSkip(String packageName, String reason) {
        log(Log.INFO, TAG, "event=route_skip result=skip package=" + packageName
                + " process=" + processName + " reason=" + reason);
    }

    private static boolean isPackageProcess(String packageName, String currentProcess) {
        return currentProcess.equals(packageName) || currentProcess.startsWith(packageName + ":");
    }

    private void installHooks(String packageName) {
        if (!hooksInstalled.compareAndSet(false, true)) {
            log(Log.INFO, TAG, "event=install_skipped result=skip package=" + packageName
                    + " process=" + processName + " reason=already_installed");
            return;
        }

        log(Log.INFO, TAG, "event=install_started result=ok package=" + packageName
                + " process=" + processName + " mode=" + modeName(hideMode));
        int hookCount = registerOnKeyDown()
                + registerDispatchKeyEvent()
                + registerOnStart()
                + registerOnStop();
        String result = hookCount == 4 ? "ok" : (hookCount == 0 ? "failed" : "partial");
        log(hookCount == 0 ? Log.ERROR : Log.INFO, TAG,
                "event=install_finished result=" + result + " package=" + packageName
                        + " process=" + processName + " hooks=" + hookCount + "/4");
    }

    private int registerOnKeyDown() {
        try {
            Method method = Activity.class.getMethod("onKeyDown", int.class, KeyEvent.class);
            hook(method)
                    .setPriority(XposedInterface.PRIORITY_DEFAULT)
                    .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                    .intercept(chain -> {
                        handleBack(chain.getThisObject(), chain.getArg(0), chain.getArg(1));
                        return chain.proceed();
                    });
            logHookRegistered(method);
            return 1;
        } catch (NoSuchMethodException e) {
            logMethodMissing(Activity.class, "onKeyDown(int, KeyEvent)", e);
        } catch (Throwable t) {
            logHookFailure(Activity.class, "onKeyDown(int, KeyEvent)", t);
        }
        return 0;
    }

    private int registerDispatchKeyEvent() {
        try {
            Method method = Activity.class.getMethod("dispatchKeyEvent", KeyEvent.class);
            hook(method)
                    .setPriority(XposedInterface.PRIORITY_DEFAULT)
                    .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                    .intercept(chain -> {
                        handleBack(chain.getThisObject(), chain.getArg(0));
                        return chain.proceed();
                    });
            logHookRegistered(method);
            return 1;
        } catch (NoSuchMethodException e) {
            logMethodMissing(Activity.class, "dispatchKeyEvent(KeyEvent)", e);
        } catch (Throwable t) {
            logHookFailure(Activity.class, "dispatchKeyEvent(KeyEvent)", t);
        }
        return 0;
    }

    private int registerOnStart() {
        try {
            Method method = Activity.class.getDeclaredMethod("onStart");
            hook(method)
                    .setPriority(XposedInterface.PRIORITY_DEFAULT)
                    .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                    .intercept(chain -> {
                        Object receiver = chain.getThisObject();
                        Object result = chain.proceed();
                        if (receiver instanceof Activity) {
                            handleActivityStarted((Activity) receiver);
                        }
                        return result;
                    });
            logHookRegistered(method);
            return 1;
        } catch (NoSuchMethodException e) {
            logMethodMissing(Activity.class, "onStart()", e);
        } catch (Throwable t) {
            logHookFailure(Activity.class, "onStart()", t);
        }
        return 0;
    }

    private int registerOnStop() {
        try {
            Method method = Activity.class.getDeclaredMethod("onStop");
            hook(method)
                    .setPriority(XposedInterface.PRIORITY_DEFAULT)
                    .setExceptionMode(XposedInterface.ExceptionMode.DEFAULT)
                    .intercept(chain -> {
                        Object receiver = chain.getThisObject();
                        Object result = chain.proceed();
                        if (receiver instanceof Activity) {
                            handleActivityStopped((Activity) receiver);
                        }
                        return result;
                    });
            logHookRegistered(method);
            return 1;
        } catch (NoSuchMethodException e) {
            logMethodMissing(Activity.class, "onStop()", e);
        } catch (Throwable t) {
            logHookFailure(Activity.class, "onStop()", t);
        }
        return 0;
    }

    private void logHookRegistered(Method method) {
        log(Log.INFO, TAG, "event=hook_registered result=ok target="
                + method.getDeclaringClass().getName() + "." + method.getName());
    }

    private void logMethodMissing(Class<?> targetClass, String signature, Throwable throwable) {
        log(Log.WARN, TAG, "event=method_not_found result=skip target="
                + targetClass.getName() + "." + signature, throwable);
    }

    private void logHookFailure(Class<?> targetClass, String signature, Throwable throwable) {
        log(Log.ERROR, TAG, "event=hook_register_failed result=skip target="
                + targetClass.getName() + "." + signature, throwable);
    }

    private void handleBack(Object receiver, Object keyCode, Object event) {
        if (hideMode != ModuleConfig.MODE_BACK
                || !(receiver instanceof Activity)
                || !(keyCode instanceof Integer)
                || !(event instanceof KeyEvent)) {
            return;
        }
        KeyEvent keyEvent = (KeyEvent) event;
        if ((Integer) keyCode == KeyEvent.KEYCODE_BACK
                && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
            hideIfNoPreviousActivity((Activity) receiver);
        }
    }

    private void handleBack(Object receiver, Object event) {
        if (hideMode != ModuleConfig.MODE_BACK
                || !(receiver instanceof Activity)
                || !(event instanceof KeyEvent)) {
            return;
        }
        KeyEvent keyEvent = (KeyEvent) event;
        if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK
                && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
            hideIfNoPreviousActivity((Activity) receiver);
        }
    }

    private void handleActivityStarted(Activity activity) {
        foregroundTracker.onStarted(activity);
        lastActivity = new WeakReference<>(activity);
        cancelPendingBackgroundHide();
        excludeFromRecent(activity, false, "activity_started");
        log(Log.DEBUG, TAG, "event=foreground_state result=foreground package="
                + activity.getPackageName() + " started=" + foregroundTracker.getStartedCount());
    }

    private void handleActivityStopped(Activity activity) {
        lastActivity = new WeakReference<>(activity);
        boolean background = foregroundTracker.onStopped(activity);
        log(Log.DEBUG, TAG, "event=foreground_state result="
                + (background ? "background_candidate" : "foreground")
                + " package=" + activity.getPackageName()
                + " started=" + foregroundTracker.getStartedCount());
        if (background
                && hideMode == ModuleConfig.MODE_BACKGROUND
                && !isChangingConfigurations(activity)) {
            scheduleBackgroundHide(activity, "all_activities_stopped");
        }
    }

    private boolean isChangingConfigurations(Activity activity) {
        try {
            return activity.isChangingConfigurations();
        } catch (RuntimeException error) {
            log(Log.WARN, TAG, "event=configuration_check result=fallback package="
                    + activity.getPackageName(), error);
            return false;
        }
    }

    private void scheduleBackgroundHide(Activity activity, String reason) {
        Handler handler = mainHandler;
        if (handler == null) {
            return;
        }
        cancelPendingBackgroundHide();
        WeakReference<Activity> activityReference = new WeakReference<>(activity);
        pendingBackgroundHide = () -> {
            pendingBackgroundHide = null;
            if (hideMode != ModuleConfig.MODE_BACKGROUND || !foregroundTracker.isBackground()) {
                return;
            }
            Activity target = activityReference.get();
            if (target == null) {
                target = lastActivity.get();
            }
            if (target != null) {
                excludeFromRecent(target, true, reason);
            }
        };
        handler.postDelayed(pendingBackgroundHide, BACKGROUND_CONFIRM_DELAY_MS);
    }

    private void cancelPendingBackgroundHide() {
        Handler handler = mainHandler;
        Runnable pending = pendingBackgroundHide;
        if (handler != null && pending != null) {
            handler.removeCallbacks(pending);
        }
        pendingBackgroundHide = null;
    }

    private void hideIfNoPreviousActivity(Activity activity) {
        try {
            if (!activity.isTaskRoot()) {
                log(Log.DEBUG, TAG, "event=back_decision result=normal_return package="
                        + activity.getPackageName() + " activity=" + activity.getClass().getName()
                        + " reason=previous_activity");
                return;
            }
        } catch (RuntimeException e) {
            log(Log.WARN, TAG, "event=back_decision result=normal_return package="
                    + activity.getPackageName() + " activity=" + activity.getClass().getName()
                    + " reason=task_root_check_failed", e);
            return;
        }

        log(Log.INFO, TAG, "event=back_decision result=hide package="
                + activity.getPackageName() + " activity=" + activity.getClass().getName()
                + " reason=no_previous_activity");
        excludeFromRecent(activity, true, "root_back_pressed");
    }

    private void excludeFromRecent(Activity activity, boolean exclude, String reason) {
        try {
            ActivityManager manager = activity.getSystemService(ActivityManager.class);
            if (manager == null) {
                log(Log.WARN, TAG, "event=recents_visibility result=skip package="
                        + activity.getPackageName() + " reason=activity_manager_unavailable");
                return;
            }

            int taskCount = 0;
            for (ActivityManager.AppTask appTask : manager.getAppTasks()) {
                appTask.setExcludeFromRecents(exclude);
                taskCount++;
            }
            log(Log.INFO, TAG, "event=recents_visibility result=ok package="
                    + activity.getPackageName() + " activity=" + activity.getClass().getName()
                    + " state=" + (exclude ? "hidden" : "visible")
                    + " mode=" + modeName(hideMode)
                    + " reason=" + reason + " tasks=" + taskCount);
        } catch (RuntimeException e) {
            log(Log.ERROR, TAG, "event=recents_visibility result=failed package="
                    + activity.getPackageName() + " reason=" + reason, e);
        }
    }

    private static String modeName(int mode) {
        return mode == ModuleConfig.MODE_BACKGROUND ? "background" : "back";
    }
}
