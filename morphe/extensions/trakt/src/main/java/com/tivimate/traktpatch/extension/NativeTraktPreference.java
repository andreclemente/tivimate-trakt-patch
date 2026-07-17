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
        setRuntimeField("ـˆ", connected ? "Trakt (Connected)" : "Trakt");
        setRuntimeField("ᴵʼ", connected ? "Account connected — watched-progress sync active"
                : "Connect your Trakt account");
    }

    /** TiviMate's bundled AndroidX Preference API is name-obfuscated at runtime. */
    private void setRuntimeField(String name, CharSequence value) {
        try {
            java.lang.reflect.Field field = Preference.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(this, value);
        } catch (ReflectiveOperationException ignored) {
            // The settings bridge also initializes these fields before insertion.
        }
    }

    // Keep the proven native click path that opens the authorization dialog.
    public void ˏᴵ() {
        TraktDeviceAuth.open(context, this::refreshState);
    }
}
