package com.hujiayucc.notask;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.view.KeyEvent;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        String packageName = lpparam.packageName;
        String mainActivity = Packages.find(packageName);
        Class<?> cls = (mainActivity != null) ? XposedHelpers.findClass(mainActivity, lpparam.classLoader) : Activity.class;
        try {
        	XposedHelpers.findAndHookMethod(cls, "onKeyDown", int.class, KeyEvent.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    int code = (int) param.args[0];
                    int action = ((KeyEvent) param.args[1]).getAction();
                    if (code == KeyEvent.KEYCODE_BACK && action == KeyEvent.ACTION_DOWN) {
                        Activity activity = (Activity) param.thisObject;
                        excludeFromRecent(activity, true);
                        XposedBridge.log("NoTask: " + packageName + "." + activity.getClass().getSimpleName());
                    }
                }
            });
        } catch(NoSuchMethodError e) {}
        
        try {
            XposedHelpers.findAndHookMethod(cls, "dispatchKeyEvent", KeyEvent.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    KeyEvent event = (KeyEvent) param.args[0];
                    if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
                        Activity activity = (Activity) param.thisObject;
                        excludeFromRecent(activity, true);
                    }
                }
            });
        } catch(NoSuchMethodError e) {}

        XposedHelpers.findAndHookMethod(Activity.class, "onStart", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                excludeFromRecent((Activity) param.thisObject, false);
            }
        });
    }

    /** 隐藏最近任务列表视图 */
    private void excludeFromRecent(Activity activity, boolean exclude) {
        try {
            ActivityManager manager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            for (ActivityManager.AppTask appTask : manager.getAppTasks()) {
                appTask.setExcludeFromRecents(exclude);
            }
            XposedBridge.log("NoTask: [" + ((exclude) ? "Hide" : "Display") + "] " + activity.getPackageName() + "." + activity.getClass().getSimpleName());
        } catch (Exception e) {
            XposedBridge.log(e.getMessage());
        }
    }
}
