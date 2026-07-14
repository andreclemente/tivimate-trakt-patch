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
        # The target runtime dispatches the row's observed onClick virtual.
        native = (ROOT / 'morphe/extensions/trakt/src/main/java/com/tivimate/traktpatch/extension/NativeTraktPreference.java').read_text()
        self.assertIn('public void ˏᴵ()', native)
        self.assertNotIn('public void ʿˏ(View view)', native)
        self.assertIn('TraktDeviceAuth.open(context)', native)
        auth = (ROOT / 'morphe/extensions/trakt/src/main/java/com/tivimate/traktpatch/extension/TraktDeviceAuth.java').read_text()
        self.assertIn('void clear()', auth)
        self.assertIn('Disconnect Trakt', auth)
        self.assertIn('Button action', auth)
        self.assertIn('action.requestFocus()', auth)
        self.assertIn('Movies only', auth)
        self.assertIn('TV shows only', auth)
        self.assertIn('Movies and TV shows', auth)
        self.assertIn('new Button(action.getContext())', auth)
        self.assertIn('new SyncSettings(context).clear()', auth)
        # Every newly created Other PreferenceScreen must get a row. A process-wide
        # boolean makes the row disappear after leaving and re-entering Other.
        self.assertNotIn('nativePreferenceInstalled', source)
        self.assertIn('INSTALLED_SCREENS', source)

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

    def test_settings_patch_metadata_does_not_claim_device_authorization_is_disabled(self):
        source = PATCH.read_text()
        self.assertIn('Trakt Device Authorization', source)
        self.assertNotIn('OAuth and sync remain disabled', source)

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
        self.assertIn('https://tivimate-trakt-oauth.andreclemente.workers.dev/v1/device/code', source)
        self.assertIn('https://tivimate-trakt-oauth.andreclemente.workers.dev/v1/device/token', source)
        self.assertIn('KeyGenParameterSpec', source)
        self.assertIn('new Dialog(uiContext)', source)
        self.assertIn('if (isConnected(context))', source)
        self.assertIn('public static boolean isConnected(Context context)', source)
        self.assertIn('return new TokenStore(context).hasValidToken();', source)
        self.assertIn('boolean hasValidToken()', source)
        self.assertIn('private static String decrypt(String ciphertext)', source)
        self.assertIn('stored.getString("access_token")', source)
        self.assertIn('stored.getString("refresh_token")', source)
        self.assertIn('isRetryableDeviceAuthorizationError(error)', source)
        self.assertIn('"authorization_pending".equals(code)', source)
        self.assertIn('"slow_down".equals(code)', source)
        self.assertIn('DeviceAuthorizationException', source)
        # The Worker can return an empty 400 while Trakt authorization is still
        # pending. Keep polling rather than treating JSON parsing as fatal.
        self.assertIn('DEVICE_TOKEN_URL.equals(endpoint) && status == 400 && text.length() == 0', source)
        self.assertIn('"authorization_pending"', source)
        self.assertNotIn('new AlertDialog.Builder', source)
        self.assertIn('setRequestProperty("User-Agent", "TiviMate-Trakt-Patch/1.0")', source)
        self.assertNotIn('CLIENT_SECRET', source)
        self.assertNotIn('client_secret', source)

    def test_native_row_uses_one_connection_snapshot_for_title_and_summary(self):
        source = EXTENSION.read_text()
        self.assertIn('final boolean connected = TraktDeviceAuth.isConnected(context);', source)
        self.assertIn('preference, "ـˆ", connected', source)
        self.assertIn('preference, "ᴵʼ", connected', source)
        self.assertEqual(source.count('TraktDeviceAuth.isConnected(context)'), 1)

    def test_runtime_sync_has_no_diagnostic_or_sync_dependency(self):
        source = RUNTIME_PATCH.read_text()
        self.assertNotIn('frida', source.lower())
        self.assertNotIn('dependsOn', source)
        self.assertIn('disabled', source)


if __name__ == "__main__":
    unittest.main(verbosity=2)
