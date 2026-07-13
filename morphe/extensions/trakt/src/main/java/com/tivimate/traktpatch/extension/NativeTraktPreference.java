package com.tivimate.traktpatch.extension;

import android.content.Context;
import android.widget.Toast;

import androidx.preference.Preference;

/** A real Preference whose obfuscated runtime click virtual is overridden. */
public final class NativeTraktPreference extends Preference {
    private final Context context;

    public NativeTraktPreference(Context context) {
        super(context, null);
        this.context = context;
    }

    // TiviMate's protected AndroidX Preference runtime renames Preference.onClick
    // to this same DEX method name. Virtual dispatch therefore reaches this row.
    public void ˏᴵ() {
        Toast.makeText(context, "Trakt: not connected", Toast.LENGTH_LONG).show();
    }
}
