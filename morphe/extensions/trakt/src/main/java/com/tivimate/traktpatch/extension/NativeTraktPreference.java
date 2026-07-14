package com.tivimate.traktpatch.extension;

import android.content.Context;
import android.view.View;

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
        TraktDeviceAuth.open(context);
    }

    // The Leanback adapter dispatches clicks through Preference.performClick.
    // Override the observed runtime entry point as well as onClick.
    public void ʿˏ(View view) {
        TraktDeviceAuth.open(context);
    }
}
