package com.hujiayucc.notask;

import android.app.Application;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class NoTaskApplication extends Application implements XposedServiceHelper.OnServiceListener {
    public interface ServiceStateListener {
        void onServiceStateChanged(XposedService service);
    }

    private static final Set<XposedService> SERVICES = new CopyOnWriteArraySet<>();
    private static final Set<ServiceStateListener> LISTENERS = new CopyOnWriteArraySet<>();

    private static volatile XposedService activeService;

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(this);
    }

    public static void addServiceStateListener(ServiceStateListener listener, boolean notifyImmediately) {
        LISTENERS.add(listener);
        if (notifyImmediately) {
            listener.onServiceStateChanged(activeService);
        }
    }

    public static void removeServiceStateListener(ServiceStateListener listener) {
        LISTENERS.remove(listener);
    }

    @Override
    public void onServiceBind(XposedService service) {
        SERVICES.add(service);
        activeService = service;
        dispatchServiceState(service);
    }

    @Override
    public void onServiceDied(XposedService service) {
        SERVICES.remove(service);
        if (activeService == service) {
            activeService = SERVICES.stream().findFirst().orElse(null);
            dispatchServiceState(activeService);
        }
    }

    private static void dispatchServiceState(XposedService service) {
        for (ServiceStateListener listener : LISTENERS) {
            listener.onServiceStateChanged(service);
        }
    }
}