package com.tivimate.traktpatch.extension;

import android.content.Context;

import androidx.preference.Preference;

/** A real Preference whose obfuscated runtime click virtual is overridden. */
public final class NativeTraktPreference extends Preference {
    private final Context context;

    public NativeTraktPreference(Context context) {
        super(context, null);
        this.context = context;
    }

    // Keep the proven native click path that opens the authorization dialog.
    public void ˏᴵ() {
        TraktDeviceAuth.open(context);
    }
}
