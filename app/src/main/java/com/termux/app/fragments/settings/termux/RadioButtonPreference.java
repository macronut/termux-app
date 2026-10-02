package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RadioButton;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;

/**
 * A Preference row with a radio button widget.
 *
 * The row remains the clickable target, while the radio button only reflects the selected state.
 */
public class RadioButtonPreference extends Preference {

    private boolean mChecked;

    public RadioButtonPreference(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWidgetLayoutResource(R.layout.preference_radio_button);
    }

    public boolean isChecked() {
        return mChecked;
    }

    public void setChecked(boolean checked) {
        if (mChecked == checked) return;
        mChecked = checked;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        RadioButton radioButton = holder.itemView.findViewById(R.id.preference_radio_button);
        if (radioButton != null) {
            radioButton.setChecked(mChecked);
        }
    }
}
