"""Static guardrails for TiviMate's native Android-TV Trakt settings entry."""
from pathlib import Path
import json
import unittest

ROOT = Path(__file__).resolve().parents[1]
EXTENSION = ROOT / "morphe/extensions/trakt/src/main/java/com/tivimate/traktpatch/extension/TraktPatchExtension.java"
PATCH = ROOT / "morphe/patches/src/main/kotlin/com/tivimate/traktpatch/patches/TraktSettingsPatch.kt"
RUNTIME_PATCH = ROOT / "morphe/patches/src/main/kotlin/com/tivimate/traktpatch/patches/TraktRuntimeSyncPatch.kt"
PATCH_BUILD = ROOT / "morphe/patches/build.gradle.kts"
DEVICE_AUTH = ROOT / "morphe/extensions/trakt/src/main/java/com/tivimate/traktpatch/extension/TraktDeviceAuth.java"
PROGRESS_BRIDGE = ROOT / "morphe/extensions/trakt/src/main/java/com/tivimate/traktpatch/extension/TraktProgressBridge.java"
METADATA_RESOLVER = ROOT / "morphe/extensions/trakt/src/main/java/com/tivimate/traktpatch/extension/XtreamMetadataResolver.java"
SYNC_CLIENT = ROOT / "morphe/extensions/trakt/src/main/java/com/tivimate/traktpatch/extension/TraktSyncClient.java"
ON_DEMAND_BRIDGE = ROOT / "morphe/extensions/trakt/src/main/java/com/tivimate/traktpatch/extension/TraktOnDemandBridge.java"
PATCH_BUNDLE_DESCRIPTOR = ROOT / "patches-bundle.json"


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
        self.assertIn('https://tivimate-trakt-oauth.andreclemente.workers.dev/v1/token', source)
        self.assertIn('storedTokenStillValid', source)
        self.assertIn('refreshAccessToken', source)
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

    def test_runtime_sync_hooks_committed_tvplayer_transactions_for_schema_capture(self):
        source = RUNTIME_PATCH.read_text()
        self.assertNotIn('frida', source.lower())
        self.assertNotIn('dependsOn', source)
        self.assertIn('endTransaction', source)
        self.assertIn('TraktProgressBridge', source)
        self.assertIn('onTransactionEnded', source)
        self.assertIn('extendWith("extensions/trakt.mpe")', source)

        bridge = PROGRESS_BRIDGE.read_text()
        self.assertIn('TvPlayer.db', bridge)
        self.assertIn('episode_last_played_positions', bridge)
        self.assertNotIn('"last_played_positions"', bridge)
        self.assertNotIn('PRAGMA table_info', bridge)
        self.assertIn('"movies"', bridge)
        self.assertIn('last_played_position_ms', bridge)
        self.assertIn('duration_ms', bridge)
        self.assertNotIn('IMPORT_CAPTURE', bridge)
        self.assertIn('database.inTransaction()', bridge)
        self.assertIn('public static void initialize(Context context)', bridge)
        self.assertIn('SQLiteDatabase.OPEN_READONLY', bridge)
        self.assertIn('TraktProgressBridge.initialize(application)', EXTENSION.read_text())
        self.assertIn('Executors.newSingleThreadExecutor()', bridge)
        self.assertNotIn('logChanges(', bridge)
        self.assertIn('default = true', source)
        self.assertNotIn('HttpURLConnection', bridge)

    def test_on_demand_sync_hooks_exact_native_vod_and_series_metadata_returns(self):
        patch = RUNTIME_PATCH.read_text()
        self.assertIn('XtreamVodInfoFingerprint', patch)
        self.assertIn('name = "ˈٴ"', patch)
        self.assertIn('XtreamSeriesInfoFingerprint', patch)
        self.assertIn('name = "ﾞˊ"', patch)
        self.assertIn('TraktOnDemandBridge', patch)
        self.assertIn('onVodInfoRequested', patch)
        self.assertIn('onSeriesInfoRequested', patch)
        self.assertTrue(ON_DEMAND_BRIDGE.exists(), "On-demand bridge is missing")
        bridge = ON_DEMAND_BRIDGE.read_text()
        self.assertIn('TraktImportCoordinator.requestOpenedMovie(xcId);', bridge)
        self.assertIn('TraktImportCoordinator.requestOpenedSeries(xcId);', bridge)
        coordinator = (ROOT / 'morphe/extensions/trakt/src/main/java/com/tivimate/traktpatch/extension/TraktImportCoordinator.java').read_text()
        self.assertIn('if (TraktDeviceAuth.isConnected(applicationContext)) requestCacheRefresh();', coordinator)
        self.assertIn('public static void requestOpenedMovie(int xcId)', coordinator)
        self.assertIn('public static void requestOpenedSeries(int xcId)', coordinator)
        self.assertIn('CACHE_MAX_AGE_MS', coordinator)
        self.assertIn('syncOpened(context, "movie", openedId)', coordinator)
        self.assertIn('syncOpened(context, "episode", openedId)', coordinator)
        self.assertIn('WHERE c.xc_id=?', coordinator)
        self.assertIn('OPEN_FALLBACK_CHECKPOINT_KEY', coordinator)
        self.assertIn('requestFallbackImport', coordinator)
        self.assertNotIn('params.toString()', bridge)
        self.assertNotIn('info.toString()', bridge)

    def test_xtream_identity_resolution_runs_off_the_database_hook_and_redacts_credentials(self):
        bridge = PROGRESS_BRIDGE.read_text()
        self.assertTrue(METADATA_RESOLVER.exists(), "Xtream metadata resolver is missing")
        resolver = METADATA_RESOLVER.read_text()
        self.assertIn('XtreamMetadataResolver.resolveAsync', bridge)
        self.assertNotIn('HttpURLConnection', bridge)
        self.assertIn('Executors.newSingleThreadExecutor', resolver)
        self.assertIn('MAX_PENDING = 64', resolver)
        self.assertIn('enqueue("movie:"', resolver)
        self.assertIn('setInstanceFollowRedirects(false)', resolver)
        self.assertIn('XtreamUrlBuilder.vodInfoUrl', resolver)
        self.assertIn('HttpURLConnection', resolver)
        self.assertIn('resolved movie metadata', resolver)
        self.assertNotIn('Log.i(TAG, playlistUrl', resolver)
        self.assertNotIn('Log.i(TAG, infoUrl', resolver)
        self.assertNotIn('error.getMessage()', resolver)

    def test_movie_progress_resolves_identity_then_posts_direct_authenticated_scrobble(self):
        bridge = PROGRESS_BRIDGE.read_text()
        resolver = METADATA_RESOLVER.read_text()
        self.assertTrue(SYNC_CLIENT.exists(), "Trakt sync client is missing")
        client = SYNC_CLIENT.read_text()
        self.assertIn('last_played_position_ms', bridge)
        self.assertIn('duration_ms', bridge)
        self.assertIn('TraktSyncClient.submitMovie', resolver)
        self.assertIn('TRAKT_API = "https://api.trakt.tv"', client)
        self.assertNotIn('workers.dev', client)
        self.assertIn('/scrobble/pause', client)
        self.assertIn('/scrobble/stop', client)
        self.assertIn('Authorization', client)
        self.assertIn('Bearer ', client)
        self.assertIn('setRequestProperty("trakt-api-key", clientId)', client)
        self.assertIn('setRequestProperty("trakt-api-version", "2")', client)
        self.assertIn('TraktDeviceAuth.clientId(context)', client)
        self.assertIn('TraktDeviceAuth.accessToken', client)
        self.assertNotIn('Log.i(TAG, accessToken', client)
        self.assertNotIn('error.getMessage()', client)

    def test_episode_progress_resolves_series_identity_then_posts_scrobble(self):
        bridge = PROGRESS_BRIDGE.read_text()
        resolver = METADATA_RESOLVER.read_text()
        client = SYNC_CLIENT.read_text()
        self.assertIn('episode_last_played_positions', bridge)
        self.assertIn('resolveEpisodeAsync', resolver)
        self.assertIn('XtreamUrlBuilder.seriesInfoUrl', resolver)
        self.assertIn('episode_xc_id', bridge)
        self.assertIn('TraktSyncClient.submitEpisode', resolver)
        self.assertIn('"show"', client)
        self.assertIn('"episode"', client)
        self.assertIn('first(info, null, "tmdb")', client)
        self.assertIn('first(info, null, "imdb")', client)

    def test_bundle_manifest_describes_runtime_sync_release(self):
        source = PATCH_BUILD.read_text()
        self.assertIn('movie and episode playback progress sync', source)
        self.assertNotIn('diagnostic patch', source)

    def test_public_repository_text_uses_tivimate_product_name_only(self):
        forbidden = (("8" + "k").casefold(), ("tivi" + "8").casefold())
        text_suffixes = {".md", ".json", ".kt", ".java", ".py", ".properties", ".yml", ".yaml"}
        violations = []
        for path in ROOT.rglob("*"):
            if not path.is_file() or ".git" in path.parts or path.suffix.casefold() not in text_suffixes:
                continue
            text = path.read_text(errors="ignore").casefold()
            if any(term in text for term in forbidden):
                violations.append(str(path.relative_to(ROOT)))
        self.assertEqual([], violations, f"non-TiviMate product references: {violations}")

    def test_manager_descriptor_uses_local_datetime_without_timezone_suffix(self):
        descriptor = json.loads(PATCH_BUNDLE_DESCRIPTOR.read_text())
        self.assertRegex(
            descriptor["created_at"],
            r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$",
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
