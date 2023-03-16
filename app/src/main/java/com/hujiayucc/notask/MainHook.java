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
        XposedHelpers.findAndHookMethod(Activity.class, "onKeyDown", int.class, KeyEvent.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                int code = (int) param.args[0];
                int action = ((KeyEvent) param.args[1]).getAction();
                if (code == KeyEvent.KEYCODE_BACK && action == KeyEvent.ACTION_DOWN) {
                    Activity activity = (Activity) param.thisObject;
                    excludeFromRecent(activity, true);
                    XposedBridge.log("NoTask:" + activity.getPackageName());
                    param.setResult(false);
                    activity.finish();
                }
            }
        });

        XposedHelpers.findAndHookMethod(Activity.class, "onStart", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
            }

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
        } catch (Exception e) {
            e.printStackTrace();
            XposedBridge.log(e.getMessage());
        }
    }
}
