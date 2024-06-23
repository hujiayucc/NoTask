package com.hujiayucc.notask;

public enum Packages {
    WECHAT("com.tencent.mm", "com.tencent.mm.ui.LauncherUI"),
    QQ("com.tencent.mobileqq", "com.tencent.mobileqq.activity.SplashActivity");
    
    private final String packageName;
    private final String mainActivity;
    
    Packages(String packageName, String mainActivity) {
        this.packageName = packageName;
        this.mainActivity = mainActivity;
    }
    
    public String getPackageName() {
        return packageName;
    }
    
    public String getMainActivity() {
        return mainActivity;
    }
    
    public static String find(String packageName) {
        for(Packages p : values()) {
            if(p.getPackageName().equals(packageName)) return p.getMainActivity();
        }
        return null;
    }
}
