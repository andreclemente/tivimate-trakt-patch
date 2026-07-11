package com.tivimate.traktpatch.extension;

/** Runtime placeholder merged into the patched APK by Morphe. */
@SuppressWarnings("unused")
public final class TraktPatchExtension {
    private TraktPatchExtension() {}

    /** @return If this patch was included during patching. Morphe patch will flip this later. */
    public static boolean isPatchIncluded() {
        return false;
    }

    public static void initialize() {
        // Cross-device constraint: UI must support TV D-pad and phone/tablet touch.
    }

    public static void onPlaybackProgress(String mediaType, String title, long positionMs, long durationMs) {
        // Placeholder: future bridge into durable sync queue.
    }
}
