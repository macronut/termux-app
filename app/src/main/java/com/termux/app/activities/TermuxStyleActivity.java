package com.termux.app.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.AtomicFile;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Built-in color and font picker, based on Termux:Styling.
 * Writes {@code ~/.termux/colors.properties} and {@code ~/.termux/font.ttf}.
 */
public final class TermuxStyleActivity extends Activity {

    private static final String LOG_TAG = "TermuxStyleActivity";
    private static final String DEFAULT_FILENAME = "Default";

    static final class Selectable {
        final String fileName;
        final String displayName;

        Selectable(@NonNull String fileName) {
            this.fileName = fileName;
            String name = fileName.replace('-', ' ');
            int dotIndex = name.lastIndexOf('.');
            if (dotIndex != -1) {
                name = name.substring(0, dotIndex);
            }
            this.displayName = capitalize(name);
        }

        @NonNull
        @Override
        public String toString() {
            return displayName;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        setContentView(R.layout.activity_termux_styling);

        Button colorButton = findViewById(R.id.color_button);
        Button fontButton = findViewById(R.id.font_button);

        ArrayAdapter<Selectable> colorAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item);
        ArrayAdapter<Selectable> fontAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item);

        colorButton.setOnClickListener(v -> showChooser(colorAdapter, true));
        fontButton.setOnClickListener(v -> showChooser(fontAdapter, false));

        colorAdapter.addAll(loadAssets("colors", ".properties"));
        fontAdapter.addAll(loadAssets("fonts", ".ttf"));
    }

    private void showChooser(@NonNull ArrayAdapter<Selectable> adapter, boolean colors) {
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setAdapter(adapter, (d, which) -> copyFile(adapter.getItem(which), colors))
            .create();
        dialog.setOnShowListener(d -> dialog.getListView().setOnItemLongClickListener((parent, view, position, id) -> {
            showLicense(adapter.getItem(position), colors);
            return true;
        }));
        dialog.show();
    }

    @NonNull
    private List<Selectable> loadAssets(@NonNull String assetType, @NonNull String extension) {
        List<Selectable> list = new ArrayList<>();
        list.add(new Selectable(DEFAULT_FILENAME));
        try {
            String[] names = getAssets().list(assetType);
            if (names != null) {
                for (String name : names) {
                    if (name.endsWith(extension)) {
                        list.add(new Selectable(name));
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (list.size() > 1) {
            Collections.sort(list.subList(1, list.size()), (a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
        }
        return list;
    }

    private void showLicense(@Nullable Selectable selectable, boolean colors) {
        if (selectable == null || DEFAULT_FILENAME.equals(selectable.fileName)) {
            return;
        }
        try {
            String assetsFolder = colors ? "colors" : "fonts";
            String fileName = selectable.fileName;
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex != -1) {
                fileName = fileName.substring(0, dotIndex);
            }
            fileName += ".txt";

            try (InputStream in = getAssets().open(assetsFolder + "/" + fileName)) {
                byte[] buffer = new byte[in.available()];
                int read = in.read(buffer);
                SpannableString license = new SpannableString(new String(buffer, 0, Math.max(read, 0), StandardCharsets.UTF_8));
                Linkify.addLinks(license, Linkify.ALL);
                AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(selectable.displayName)
                    .setMessage(license)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
                TextView messageView = dialog.findViewById(android.R.id.message);
                if (messageView != null) {
                    messageView.setMovementMethod(LinkMovementMethod.getInstance());
                }
            }
        } catch (IOException ignored) {
            // License file is optional.
        }
    }

    private void copyFile(@Nullable Selectable selectable, boolean colors) {
        if (selectable == null) {
            return;
        }

        File destinationFile = colors ? TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE : TermuxConstants.TERMUX_FONT_FILE;
        FileOutputStream out = null;
        AtomicFile atomicFile = null;
        try {
            File termuxDir = TermuxConstants.TERMUX_DATA_HOME_DIR;
            if (!(termuxDir.isDirectory() || termuxDir.mkdirs())) {
                throw new IOException("Cannot create termux dir=" + termuxDir.getAbsolutePath());
            }

            destinationFile = destinationFile.getCanonicalFile();
            //noinspection ResultOfMethodCallIgnored
            destinationFile.setWritable(true);
            File parent = destinationFile.getParentFile();
            if (parent != null) {
                //noinspection ResultOfMethodCallIgnored
                parent.setWritable(true);
                //noinspection ResultOfMethodCallIgnored
                parent.setExecutable(true);
            }

            atomicFile = new AtomicFile(destinationFile);
            out = atomicFile.startWrite();
            if (DEFAULT_FILENAME.equals(selectable.fileName)) {
                if (colors) {
                    out.write("# Using default color theme.".getBytes(StandardCharsets.UTF_8));
                }
            } else {
                String assetsFolder = colors ? "colors" : "fonts";
                try (InputStream in = getAssets().open(assetsFolder + "/" + selectable.fileName)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                }
            }
            atomicFile.finishWrite(out);
            out = null;
            TermuxActivity.updateTermuxActivityStyling(this, false);
        } catch (Exception e) {
            if (atomicFile != null && out != null) {
                atomicFile.failWrite(out);
            }
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to write " + destinationFile.getName(), e);
            Toast.makeText(this, getString(R.string.writing_failed) + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @NonNull
    static String capitalize(@NonNull String str) {
        boolean lastWhitespace = true;
        char[] chars = str.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (Character.isLetter(chars[i])) {
                if (lastWhitespace) {
                    chars[i] = Character.toUpperCase(chars[i]);
                }
                lastWhitespace = false;
            } else {
                lastWhitespace = Character.isWhitespace(chars[i]);
            }
        }
        return new String(chars);
    }

}
