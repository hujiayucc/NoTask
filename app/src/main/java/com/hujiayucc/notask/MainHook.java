package com.hujiayucc.notask;

import android.app.Activity;
import android.app.ActivityManager;
import android.util.Log;
import android.view.KeyEvent;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class MainHook extends XposedModule {
    private static final String TAG = "NoTask";
    private static final String MODULE_PACKAGE = "com.hujiayucc.notask";

    private final AtomicBoolean hooksInstalled = new AtomicBoolean(false);
    private volatile String processName = "";
    private volatile boolean systemServer;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        processName = param.getProcessName();
        systemServer = param.isSystemServer();
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
        installHooks(packageName);
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
                + " process=" + processName);
        int hookCount = registerOnKeyDown()
                + registerDispatchKeyEvent()
                + registerOnStart();
        String result = hookCount == 3 ? "ok" : (hookCount == 0 ? "failed" : "partial");
        log(hookCount == 0 ? Log.ERROR : Log.INFO, TAG,
                "event=install_finished result=" + result + " package=" + packageName
                        + " process=" + processName + " hooks=" + hookCount + "/3");
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
                            excludeFromRecent((Activity) receiver, false);
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
        if (!(receiver instanceof Activity)
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
        if (!(receiver instanceof Activity) || !(event instanceof KeyEvent)) {
            return;
        }
        KeyEvent keyEvent = (KeyEvent) event;
        if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK
                && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
            hideIfNoPreviousActivity((Activity) receiver);
        }
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
        excludeFromRecent(activity, true);
    }

    private void excludeFromRecent(Activity activity, boolean exclude) {
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
                    + " state=" + (exclude ? "hidden" : "visible") + " tasks=" + taskCount);
        } catch (RuntimeException e) {
            log(Log.ERROR, TAG, "event=recents_visibility result=failed package="
                    + activity.getPackageName(), e);
        }
    }
}
