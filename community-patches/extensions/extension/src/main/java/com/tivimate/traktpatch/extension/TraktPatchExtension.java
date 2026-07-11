package com.tivimate.traktpatch.extension;

/**
 * Runtime extension placeholder merged into the patched APK by the patch bundle.
 *
 * Keep this class dependency-light. Real implementation will be split into
 * settings/auth/sync/hooks classes after TiviMate patch points are mapped.
 */
public final class TraktPatchExtension {
    private TraktPatchExtension() {}

    public static void initialize() {
        // Placeholder: future entry point for settings/auth/sync initialization.
        // TV-first constraint: all UI launched from this extension must be D-pad navigable,
        // must not require touchscreen hardware, and must show visible status feedback.
    }

    public static void onPlaybackProgress(
            String mediaType,
            String title,
            long positionMs,
            long durationMs
    ) {
        // Placeholder: future hook bridge into the durable sync queue.
    }
}
