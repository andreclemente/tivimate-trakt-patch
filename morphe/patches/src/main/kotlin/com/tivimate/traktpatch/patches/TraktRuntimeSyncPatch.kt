package com.tivimate.traktpatch.patches

import app.morphe.patcher.patch.ResourcePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.rawResourcePatch
import app.morphe.patches.all.misc.extension.activityOnCreateExtensionHook
import com.tivimate.traktpatch.patches.Constants.COMPATIBILITY_TIVIMATE

private const val DIAGNOSTIC_EXTENSION_CLASS =
    "Lcom/tivimate/traktpatch/extension/TraktPatchExtension;"
private const val TIVIMATE_LAUNCH_ACTIVITY = "Lcom/andyhax/haxsplash/LaunchActivity;"
private const val GADGET_RESOURCE = "diagnostic/x86_64/libfrida-gadget.so"
private const val GADGET_CONFIG_RESOURCE = "diagnostic/x86_64/libfrida-gadget.config.so"
private const val GADGET_APK_PATH = "lib/x86_64/libfrida-gadget.so"
private const val GADGET_CONFIG_APK_PATH = "lib/x86_64/libfrida-gadget.config.so"

private fun ResourcePatchContext.copyDiagnosticResource(resource: String, destination: String) {
    val input = object {}.javaClass.classLoader.getResourceAsStream(resource)
        ?: error("Diagnostic resource missing: $resource")
    input.use { source ->
        this[destination].apply { parentFile.mkdirs() }.outputStream().use(source::copyTo)
    }
}

/** Adds the two native files required by the local-only x86_64 diagnostic gadget. */
@Suppress("unused")
val tvDiagnosticNativeLibrariesPatch = rawResourcePatch(
    name = "TiviMate TV diagnostic native libraries (x86_64)",
    description = "Temporary Frida Gadget payload for local Android TV runtime discovery.",
    default = false
) {
    compatibleWith(COMPATIBILITY_TIVIMATE)
    execute {
        copyDiagnosticResource(GADGET_RESOURCE, GADGET_APK_PATH)
        copyDiagnosticResource(GADGET_CONFIG_RESOURCE, GADGET_CONFIG_APK_PATH)
    }
}

/**
 * Temporary, opt-in discovery patch for the user's x86_64 Android TV emulator.
 * It merges the diagnostic extension and invokes its initializer from TiviMate's
 * launch activity. The extension starts a Frida Gadget listener on loopback only.
 */
@Suppress("unused")
val tvDiagnosticGadgetPatch = bytecodePatch(
    name = "TiviMate TV runtime diagnostics (x86_64)",
    description = "Temporary local-only Frida diagnostics for mapping playback state on Android TV.",
    default = false
) {
    compatibleWith(COMPATIBILITY_TIVIMATE)
    dependsOn(tvDiagnosticNativeLibrariesPatch)
    extendWith("trakt.mpe")

    finalize {
        activityOnCreateExtensionHook(TIVIMATE_LAUNCH_ACTIVITY)
            .invoke(DIAGNOSTIC_EXTENSION_CLASS)
    }
}

@Suppress("unused")
val traktRuntimeSyncPatch = bytecodePatch(
    name = "TiviMate Trakt runtime sync",
    description = "Reserved production patch; no runtime sync is enabled until evidence identifies stable hooks.",
    default = false
) {
    compatibleWith(COMPATIBILITY_TIVIMATE)
    dependsOn(tvDiagnosticGadgetPatch)
}
