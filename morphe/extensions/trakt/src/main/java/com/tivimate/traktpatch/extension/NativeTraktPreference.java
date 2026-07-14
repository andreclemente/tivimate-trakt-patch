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

    // Directly bind the native adapter-owned row. The compile-time AndroidX
    // library does not expose the protected runtime's renamed super method.
    public void ʿˏ(View view) {
        view.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View clicked) {
                TraktDeviceAuth.open(context);
            }
        });
    }
}
