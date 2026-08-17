package com.hujiayucc.notask;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.libxposed.service.XposedService;

public final class MainActivity extends AppCompatActivity implements
        NoTaskApplication.ServiceStateListener,
        AppListAdapter.Listener {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService appLoader = Executors.newSingleThreadExecutor();
    private final Set<String> scopedPackages = new HashSet<>();

    private MaterialCardView statusPanel;
    private ImageView statusIcon;
    private TextView statusTitle;
    private TextView statusDetail;
    private TextView appCount;
    private TextView emptyState;
    private ProgressBar loadingIndicator;
    private AppListAdapter adapter;

    private XposedService service;
    private SharedPreferences preferences;
    private boolean appsLoaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureEdgeToEdgeWindow();
        setContentView(R.layout.activity_main);
        initializeViews();
        loadApps();
    }

    @Override
    protected void onStart() {
        super.onStart();
        NoTaskApplication.addServiceStateListener(this, true);
    }

    @Override
    protected void onStop() {
        NoTaskApplication.removeServiceStateListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        appLoader.shutdownNow();
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    private void configureEdgeToEdgeWindow() {
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.setNavigationBarDividerColor(Color.TRANSPARENT);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        boolean lightSystemBars = getResources().getBoolean(R.bool.window_light_system_bars);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                window,
                window.getDecorView()
        );
        controller.setAppearanceLightStatusBars(lightSystemBars);
        controller.setAppearanceLightNavigationBars(lightSystemBars);
    }

    private void applySystemBarInsets(
            View root,
            MaterialToolbar toolbar,
            RecyclerView appList
    ) {
        int rootPaddingLeft = root.getPaddingLeft();
        int rootPaddingTop = root.getPaddingTop();
        int rootPaddingRight = root.getPaddingRight();
        int rootPaddingBottom = root.getPaddingBottom();
        int toolbarHeight = toolbar.getLayoutParams().height;
        int toolbarPaddingTop = toolbar.getPaddingTop();
        int listPaddingBottom = appList.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(
                    rootPaddingLeft + systemBars.left,
                    rootPaddingTop,
                    rootPaddingRight + systemBars.right,
                    rootPaddingBottom
            );

            ViewGroup.LayoutParams layoutParams = toolbar.getLayoutParams();
            int insetToolbarHeight = toolbarHeight + systemBars.top;
            if (layoutParams.height != insetToolbarHeight) {
                layoutParams.height = insetToolbarHeight;
                toolbar.setLayoutParams(layoutParams);
            }
            toolbar.setPadding(
                    toolbar.getPaddingLeft(),
                    toolbarPaddingTop + systemBars.top,
                    toolbar.getPaddingRight(),
                    toolbar.getPaddingBottom()
            );
            appList.setPadding(
                    appList.getPaddingLeft(),
                    appList.getPaddingTop(),
                    appList.getPaddingRight(),
                    listPaddingBottom + systemBars.bottom
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void initializeViews() {
        View root = findViewById(R.id.root);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        statusPanel = findViewById(R.id.status_panel);
        statusIcon = findViewById(R.id.status_icon);
        statusTitle = findViewById(R.id.status_title);
        statusDetail = findViewById(R.id.status_detail);
        TextView statusVersion = findViewById(R.id.status_version);
        appCount = findViewById(R.id.app_count);
        emptyState = findViewById(R.id.empty_state);
        loadingIndicator = findViewById(R.id.loading_indicator);

        statusVersion.setText(getString(
                R.string.module_version,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE
        ));

        RecyclerView appList = findViewById(R.id.app_list);
        appList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppListAdapter(this, this);
        appList.setAdapter(adapter);
        applySystemBarInsets(root, toolbar, appList);

        TextInputEditText searchInput = findViewById(R.id.search_input);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                adapter.filter(text == null ? "" : text.toString());
                updateEmptyState();
            }
        });

        showInactiveStatus();
        updateAppSummary();
    }

    private void loadApps() {
        loadingIndicator.setVisibility(View.VISIBLE);
        appLoader.execute(() -> {
            try {
                List<AppEntry> loaded = InstalledApps.load(getApplicationContext());
                mainHandler.post(() -> {
                    if (isDestroyed()) {
                        return;
                    }
                    appsLoaded = true;
                    loadingIndicator.setVisibility(View.GONE);
                    adapter.setEntries(loaded);
                    applyConfigurationToEntries();
                });
            } catch (RuntimeException error) {
                mainHandler.post(() -> {
                    if (isDestroyed()) {
                        return;
                    }
                    appsLoaded = true;
                    loadingIndicator.setVisibility(View.GONE);
                    emptyState.setText(R.string.app_load_failed);
                    emptyState.setVisibility(View.VISIBLE);
                    Toast.makeText(this, R.string.app_load_failed, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @Override
    public void onServiceStateChanged(XposedService newService) {
        mainHandler.post(() -> bindService(newService));
    }

    private void bindService(XposedService newService) {
        if (isDestroyed()) {
            return;
        }

        service = newService;
        preferences = null;
        scopedPackages.clear();
        adapter.clearPending();

        if (newService == null) {
            showInactiveStatus();
            applyConfigurationToEntries();
            return;
        }

        try {
            String frameworkName = newService.getFrameworkName();
            int apiVersion = newService.getApiVersion();
            long properties = newService.getFrameworkProperties();
            boolean supportsRemote = (properties & XposedService.PROP_CAP_REMOTE) != 0L;
            if (supportsRemote) {
                preferences = newService.getRemotePreferences(ModuleConfig.PREFERENCES_NAME);
                scopedPackages.addAll(nonNullPackages(newService.getScope()));
            }
            showActiveStatus(frameworkName, apiVersion, supportsRemote);
        } catch (RuntimeException error) {
            service = null;
            preferences = null;
            scopedPackages.clear();
            showInactiveStatus();
        }
        applyConfigurationToEntries();
    }

    private Set<String> nonNullPackages(List<String> packages) {
        Set<String> result = new HashSet<>();
        if (packages != null) {
            for (String packageName : packages) {
                if (packageName != null && !packageName.isBlank()) {
                    result.add(packageName);
                }
            }
        }
        return result;
    }

    private void showActiveStatus(String frameworkName, int apiVersion, boolean supportsRemote) {
        statusPanel.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_active_container));
        statusIcon.setImageResource(R.drawable.ic_status_active);
        statusTitle.setText(R.string.module_active);
        if (supportsRemote) {
            statusDetail.setText(getString(R.string.module_active_detail, frameworkName, apiVersion));
        } else {
            statusDetail.setText(R.string.module_remote_unavailable);
        }
    }

    private void showInactiveStatus() {
        statusPanel.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_inactive_container));
        statusIcon.setImageResource(R.drawable.ic_status_inactive);
        statusTitle.setText(R.string.module_inactive);
        statusDetail.setText(R.string.module_inactive_detail);
    }

    private void applyConfigurationToEntries() {
        for (AppEntry entry : adapter.getEntries()) {
            entry.scoped = scopedPackages.contains(entry.packageName);
            entry.mode = ModuleConfig.readMode(preferences, entry.packageName);
        }
        adapter.setControlsEnabled(service != null && preferences != null);
        adapter.refresh();
        updateAppSummary();
        updateEmptyState();
    }

    private void updateAppSummary() {
        int enabledCount = 0;
        for (AppEntry entry : adapter.getEntries()) {
            if (entry.scoped) {
                enabledCount++;
            }
        }
        appCount.setText(getString(R.string.app_count, enabledCount, adapter.getEntries().size()));
    }

    private void updateEmptyState() {
        boolean empty = appsLoaded && adapter.getVisibleCount() == 0;
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onModeChanged(AppEntry entry, int mode) {
        SharedPreferences currentPreferences = preferences;
        if (currentPreferences == null) {
            adapter.refresh();
            Toast.makeText(this, R.string.scope_service_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        int oldMode = entry.mode;
        int newMode = ModuleConfig.normalizeMode(mode);
        entry.mode = newMode;
        try {
            currentPreferences.edit()
                    .putInt(ModuleConfig.modeKey(entry.packageName), newMode)
                    .apply();
        } catch (RuntimeException error) {
            entry.mode = oldMode;
            Toast.makeText(this, R.string.config_write_failed, Toast.LENGTH_SHORT).show();
        }
        adapter.refresh();
    }

    @Override
    public void onScopeChanged(AppEntry entry, boolean enabled) {
        XposedService currentService = service;
        SharedPreferences currentPreferences = preferences;
        if (currentService == null || currentPreferences == null) {
            adapter.refresh();
            Toast.makeText(this, R.string.scope_service_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        adapter.setPending(entry.packageName, true);
        if (enabled) {
            try {
                currentPreferences.edit()
                        .putInt(ModuleConfig.modeKey(entry.packageName), entry.mode)
                        .apply();
                currentService.requestScope(
                        Collections.singletonList(entry.packageName),
                        new XposedService.OnScopeEventListener() {
                            @Override
                            public void onScopeRequestApproved(List<String> approved) {
                                mainHandler.post(() -> completeScopeRequest(
                                        currentService,
                                        entry.packageName,
                                        approved
                                ));
                            }

                            @Override
                            public void onScopeRequestFailed(String message) {
                                mainHandler.post(() -> failScopeRequest(
                                        currentService,
                                        entry.packageName,
                                        message
                                ));
                            }
                        }
                );
            } catch (RuntimeException error) {
                failScopeRequest(currentService, entry.packageName, error.getLocalizedMessage());
            }
        } else {
            try {
                currentService.removeScope(Collections.singletonList(entry.packageName));
                scopedPackages.remove(entry.packageName);
                adapter.setPending(entry.packageName, false);
                applyConfigurationToEntries();
            } catch (RuntimeException error) {
                adapter.setPending(entry.packageName, false);
                adapter.refresh();
                Toast.makeText(this, R.string.scope_remove_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void completeScopeRequest(
            XposedService requestService,
            String packageName,
            List<String> approved
    ) {
        if (isDestroyed() || service != requestService) {
            return;
        }

        try {
            scopedPackages.clear();
            scopedPackages.addAll(nonNullPackages(requestService.getScope()));
        } catch (RuntimeException error) {
            scopedPackages.addAll(nonNullPackages(approved));
        }
        adapter.setPending(packageName, false);
        applyConfigurationToEntries();
    }

    private void failScopeRequest(
            XposedService requestService,
            String packageName,
            String message
    ) {
        if (isDestroyed() || service != requestService) {
            return;
        }
        adapter.setPending(packageName, false);
        applyConfigurationToEntries();
        String detail = message == null || message.isBlank()
                ? getString(R.string.scope_request_no_detail)
                : message;
        Toast.makeText(
                this,
                getString(R.string.scope_request_failed, detail),
                Toast.LENGTH_SHORT
        ).show();
    }
}