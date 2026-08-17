package com.hujiayucc.notask;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {
    interface Listener {
        void onScopeChanged(AppEntry entry, boolean enabled);

        void onModeChanged(AppEntry entry, int mode);
    }

    private final Context context;
    private final Listener listener;
    private final Collator collator = Collator.getInstance(Locale.getDefault());
    private final Set<String> pendingPackages = new HashSet<>();
    private final List<AppEntry> entries = new ArrayList<>();
    private final List<AppEntry> visibleEntries = new ArrayList<>();

    private boolean controlsEnabled;
    private String query = "";

    AppListAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        setHasStableIds(true);
    }

    void setEntries(List<AppEntry> newEntries) {
        entries.clear();
        entries.addAll(newEntries);
        rebuildVisibleEntries();
    }

    List<AppEntry> getEntries() {
        return entries;
    }

    int getVisibleCount() {
        return visibleEntries.size();
    }

    void setControlsEnabled(boolean enabled) {
        controlsEnabled = enabled;
        notifyDataSetChanged();
    }

    void setPending(String packageName, boolean pending) {
        if (pending) {
            pendingPackages.add(packageName);
        } else {
            pendingPackages.remove(packageName);
        }
        refresh();
    }

    void clearPending() {
        pendingPackages.clear();
    }

    void filter(String value) {
        query = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        rebuildVisibleEntries();
    }

    void refresh() {
        rebuildVisibleEntries();
    }

    private void rebuildVisibleEntries() {
        entries.sort((left, right) -> {
            int scopeResult = Boolean.compare(!left.scoped, !right.scoped);
            if (scopeResult != 0) {
                return scopeResult;
            }
            return collator.compare(left.label, right.label);
        });

        visibleEntries.clear();
        for (AppEntry entry : entries) {
            if (query.isEmpty()
                    || entry.label.toLowerCase(Locale.ROOT).contains(query)
                    || entry.packageName.toLowerCase(Locale.ROOT).contains(query)) {
                visibleEntries.add(entry);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return visibleEntries.get(position).packageName.hashCode();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        AppEntry entry = visibleEntries.get(position);
        boolean pending = pendingPackages.contains(entry.packageName);
        boolean enabled = controlsEnabled && !pending;

        holder.binding = true;
        holder.entry = entry;
        holder.icon.setImageDrawable(entry.icon);
        holder.name.setText(entry.label);
        holder.packageName.setText(entry.packageName);
        holder.scopeSwitch.setContentDescription(context.getString(R.string.enable_app, entry.label));
        holder.scopeSwitch.setChecked(entry.scoped);
        holder.scopeSwitch.setEnabled(enabled);

        int selectedButton = entry.mode == ModuleConfig.MODE_BACKGROUND
                ? R.id.mode_background
                : R.id.mode_back;
        holder.modeGroup.check(selectedButton);
        holder.modeBack.setEnabled(enabled);
        holder.modeBackground.setEnabled(enabled);

        if (!entry.scoped) {
            holder.state.setText(R.string.app_disabled);
            holder.state.setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant));
        } else if (entry.mode == ModuleConfig.MODE_BACKGROUND) {
            holder.state.setText(R.string.app_enabled_background);
            holder.state.setTextColor(ContextCompat.getColor(context, R.color.primary));
        } else {
            holder.state.setText(R.string.app_enabled_back);
            holder.state.setTextColor(ContextCompat.getColor(context, R.color.primary));
        }
        holder.binding = false;
    }

    @Override
    public int getItemCount() {
        return visibleEntries.size();
    }

    final class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView packageName;
        final TextView state;
        final MaterialSwitch scopeSwitch;
        final MaterialButtonToggleGroup modeGroup;
        final MaterialButton modeBack;
        final MaterialButton modeBackground;

        boolean binding;
        AppEntry entry;

        ViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.app_icon);
            name = itemView.findViewById(R.id.app_name);
            packageName = itemView.findViewById(R.id.app_package);
            state = itemView.findViewById(R.id.app_state);
            scopeSwitch = itemView.findViewById(R.id.scope_switch);
            modeGroup = itemView.findViewById(R.id.mode_group);
            modeBack = itemView.findViewById(R.id.mode_back);
            modeBackground = itemView.findViewById(R.id.mode_background);

            scopeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!binding && entry != null) {
                    listener.onScopeChanged(entry, isChecked);
                }
            });
            modeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!binding && isChecked && entry != null) {
                    int mode = checkedId == R.id.mode_background
                            ? ModuleConfig.MODE_BACKGROUND
                            : ModuleConfig.MODE_BACK;
                    listener.onModeChanged(entry, mode);
                }
            });
        }
    }
}