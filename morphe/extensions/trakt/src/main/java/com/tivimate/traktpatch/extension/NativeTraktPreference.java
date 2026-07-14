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

    // This is the exact v0.1.20 click path that opened the native dialog.
    public void ˏᴵ() {
        TraktDeviceAuth.open(context);
    }
}
