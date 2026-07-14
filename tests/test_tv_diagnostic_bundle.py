"""Static guardrails for TiviMate's native Android-TV Trakt settings entry."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
EXTENSION = ROOT / "morphe/extensions/trakt/src/main/java/com/tivimate/traktpatch/extension/TraktPatchExtension.java"
PATCH = ROOT / "morphe/patches/src/main/kotlin/com/tivimate/traktpatch/patches/TraktSettingsPatch.kt"
RUNTIME_PATCH = ROOT / "morphe/patches/src/main/kotlin/com/tivimate/traktpatch/patches/TraktRuntimeSyncPatch.kt"
DEVICE_AUTH = ROOT / "morphe/extensions/trakt/src/main/java/com/tivimate/traktpatch/extension/TraktDeviceAuth.java"


class TvTraktSettingsBundleTest(unittest.TestCase):
    def test_settings_extension_uses_native_androidx_preference(self):
        source = EXTENSION.read_text()
        self.assertIn('androidx.preference.Preference', source)
        self.assertIn('getPreferenceScreen', source)
        self.assertIn('setOnPreferenceClickListener', source)
        self.assertIn('"Other"', source)
        self.assertIn('"Trakt"', source)

    def test_settings_patch_uses_exact_callable_application_bootstrap(self):
        source = PATCH.read_text()
        self.assertIn('extendWith("extensions/trakt.mpe")', source)
        self.assertIn('classDefBy(EXTENSION_CLASS)', source)
        self.assertIn('ProtectedTvPlayerApplication', source)
        self.assertIn('ProtectedApplicationOnCreateFingerprint', source)
        self.assertIn('name = "onCreate"', source)
        self.assertNotIn('attachBaseContext', source)
        self.assertIn('invoke-static { p0 }, $EXTENSION_CLASS->initialize', source)
        self.assertNotIn('activityOnCreateExtensionHook', source)
        self.assertIn('Settings > Other', source)

    def test_native_row_clones_vod_preference_style(self):
        source = EXTENSION.read_text()
        # A generic Preference renders with a different row layout and does not
        # receive TiviMate's native selected/focused treatment. The injected row
        # must be made from the live VOD preference's concrete class/style.
        self.assertIn('findPreferenceByTitle(screen, "VOD")', source)
        self.assertIn('copyPreferencePresentation(vodPreference, preference)', source)
        self.assertIn('field.getDeclaringClass().isInstance(target)', source)

    def test_device_auth_uses_worker_and_never_embeds_client_secret(self):
        source = DEVICE_AUTH.read_text()
        self.assertIn('https://tivimate-trakt-oauth.andreclemente.dev/v1/device/code', source)
        self.assertIn('/v1/device/token', source)
        self.assertIn('KeyGenParameterSpec', source)
        self.assertIn('new Dialog(context)', source)
        self.assertNotIn('new AlertDialog.Builder', source)
        self.assertIn('setRequestProperty("User-Agent", "TiviMate-Trakt-Patch/1.0")', source)
        self.assertNotIn('CLIENT_SECRET', source)
        self.assertNotIn('client_secret', source)

    def test_runtime_sync_has_no_diagnostic_or_sync_dependency(self):
        source = RUNTIME_PATCH.read_text()
        self.assertNotIn('frida', source.lower())
        self.assertNotIn('dependsOn', source)
        self.assertIn('disabled', source)


if __name__ == "__main__":
    unittest.main(verbosity=2)
