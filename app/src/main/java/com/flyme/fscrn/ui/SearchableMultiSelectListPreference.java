package com.flyme.fscrn.ui;

import android.app.AlertDialog;
import android.content.Context;
import com.flyme.fscrn.R;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.MultiSelectListPreference;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SearchableMultiSelectListPreference extends MultiSelectListPreference {
    public SearchableMultiSelectListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SearchableMultiSelectListPreference(Context context) {
        super(context);
    }

    @Override
    protected void onClick() {
        showDialog();
    }

    private void showDialog() {
        Context context = getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.app_list_dialog, null);

        EditText searchInput = view.findViewById(R.id.search_input);
        ListView listView = view.findViewById(R.id.app_list);

        CharSequence[] entries = getEntries();
        CharSequence[] entryValues = getEntryValues();
        Set<String> values = getValues();

        String currentPackage = context.getPackageName();
        boolean isFullscreenAppsList = "fullscreen_apps".equals(getKey());

        List<AppItem> allItems = new ArrayList<>();
        if (entries != null && entryValues != null) {
            for (int i = 0; i < entries.length; i++) {
                String value = entryValues[i].toString();
                boolean isChecked = values.contains(value);

                // Force check current app if this is the fullscreen apps list
                if (isFullscreenAppsList && currentPackage.equals(value)) {
                    isChecked = true;
                }

                allItems.add(new AppItem(entries[i].toString(), value, isChecked));
            }
        }

        AppAdapter adapter = new AppAdapter(context, allItems, isFullscreenAppsList, currentPackage);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, v, position, id) -> {
            AppItem item = adapter.getItem(position);
            if (item != null) {
                // Prevent unchecking the current app in fullscreen list
                if (isFullscreenAppsList && currentPackage.equals(item.value)) {
                    Toast.makeText(context, "Приложение Flyme Tweak обязательно для работы авто-оверлея", Toast.LENGTH_SHORT).show();
                    return;
                }
                item.isChecked = !item.isChecked;
                adapter.notifyDataSetChanged();
            }
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.getFilter().filter(s);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        new AlertDialog.Builder(context)
                .setTitle(getTitle())
                .setView(view)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    Set<String> newValues = new HashSet<>();
                    for (AppItem item : allItems) {
                        if (item.isChecked) {
                            newValues.add(item.value);
                        }
                    }

                    // Double ensure the current app is included before saving
                    if (isFullscreenAppsList) {
                        newValues.add(currentPackage);
                    }

                    if (callChangeListener(newValues)) {
                        setValues(newValues);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static class AppItem {
        String label;
        String value;
        boolean isChecked;

        AppItem(String label, String value, boolean isChecked) {
            this.label = label;
            this.value = value;
            this.isChecked = isChecked;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static class AppAdapter extends ArrayAdapter<AppItem> {
        private final boolean isFullscreenList;
        private final String targetPackage;

        AppAdapter(Context context, List<AppItem> items, boolean isFullscreenList, String targetPackage) {
            super(context, R.layout.app_list_item, items);
            this.isFullscreenList = isFullscreenList;
            this.targetPackage = targetPackage;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.app_list_item, parent, false);
            }
            CheckedTextView checkedTextView = convertView.findViewById(android.R.id.text1);
            AppItem item = getItem(position);
            if (item != null) {
                checkedTextView.setText(item.label);
                checkedTextView.setChecked(item.isChecked);

                if (isFullscreenList && targetPackage.equals(item.value)) {
                    // Make it look disabled but checked
                    checkedTextView.setAlpha(0.5f);
                } else {
                    checkedTextView.setAlpha(1.0f);
                }
            }
            return convertView;
        }
    }
}
