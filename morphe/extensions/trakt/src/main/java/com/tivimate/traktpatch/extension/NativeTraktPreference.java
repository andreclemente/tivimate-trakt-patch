package com.tivimate.traktpatch.extension;

import android.content.Context;

import androidx.preference.Preference;

/** A real Preference whose obfuscated runtime click virtual is overridden. */
public final class NativeTraktPreference extends Preference {
    private final Context context;

    public NativeTraktPreference(Context context) {
        super(context, null);
        this.context = context;
        refreshState();
    }

    private void refreshState() {
        boolean connected = TraktDeviceAuth.isConnected(context);
        setTitle(connected ? "Trakt (Connected)" : "Trakt");
        setSummary(connected ? "Account connected — watched-progress sync active"
                : "Connect your Trakt account");
    }

    // Keep the proven native click path that opens the authorization dialog.
    public void ˏᴵ() {
        TraktDeviceAuth.open(context, this::refreshState);
    }
}
