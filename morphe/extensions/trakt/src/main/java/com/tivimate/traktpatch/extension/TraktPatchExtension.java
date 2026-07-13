package com.tivimate.traktpatch.extension;

import android.content.Context;
import android.util.Log;

/**
 * Runtime bridge merged into a diagnostic patched APK by Morphe.
 *
 * <p>The diagnostic build is deliberately x86_64 Android-TV-only. It starts a
 * Frida Gadget listener bound to loopback so that the desktop hosting the AVD
 * can collect evidence without {@code adb root}. It contains no Trakt tokens,
 * IPTV credentials, network sync, or production behavior.</p>
 */
@SuppressWarnings("unused")
public final class TraktPatchExtension {
    private static final String TAG = "TiviMateTraktDiag";
    private static volatile boolean diagnosticGadgetInitialized;

    private TraktPatchExtension() {}

    /** @return Whether this diagnostic extension was merged into the APK. */
    public static boolean isPatchIncluded() {
        return true;
    }

    /** Called by the injected launch-activity hook. */
    public static void initialize(Context context) {
        initializeDiagnosticGadget(context);
    }

    /**
     * Loads libfrida-gadget.so exactly once. The native configuration is
     * packaged adjacent to the library and binds only to 127.0.0.1:27042.
     */
    public static synchronized void initializeDiagnosticGadget(Context context) {
        if (diagnosticGadgetInitialized) return;
        try {
            System.loadLibrary("frida-gadget");
            diagnosticGadgetInitialized = true;
            Log.i(TAG, "Diagnostic gadget loaded on loopback only");
        } catch (UnsatisfiedLinkError error) {
            Log.e(TAG, "Unable to load diagnostic gadget", error);
        }
    }

    public static void onPlaybackProgress(String mediaType, String title, long positionMs, long durationMs) {
        // Production sync is intentionally not implemented in the diagnostic bundle.
    }
}
