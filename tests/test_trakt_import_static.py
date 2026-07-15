from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "morphe/extensions/trakt/src/main/java/com/tivimate/traktpatch/extension"
COORDINATOR = JAVA / "TraktImportCoordinator.java"
BRIDGE = JAVA / "TraktProgressBridge.java"
AUTH = JAVA / "TraktDeviceAuth.java"
EXTENSION = JAVA / "TraktPatchExtension.java"


class TraktImportStaticRegressionTest(unittest.TestCase):
    def test_import_calls_trakt_directly_with_required_headers_and_bounds(self):
        source = COORDINATOR.read_text()
        self.assertIn('TRAKT_API = "https://api.trakt.tv"', source)
        self.assertNotIn("workers.dev", source)
        for route in ("/sync/watched/movies", "/sync/history/episodes", "/sync/playback"):
            self.assertIn(route, source)
        self.assertIn('setRequestMethod("GET")', source)
        self.assertIn('"Bearer " + token', source)
        self.assertIn('setRequestProperty("trakt-api-key", clientId)', source)
        self.assertIn('setRequestProperty("trakt-api-version", "2")', source)
        self.assertIn("TraktDeviceAuth.clientId(context)", source)
        self.assertIn("MAX_PROVIDER_TASKS = 4096", source)
        self.assertIn("MAX_RESPONSE_CHARS = 2_000_000", source)
        self.assertIn("Executors.newSingleThreadExecutor()", source)

    def test_refresh_route_matches_worker_contract_exactly(self):
        source = AUTH.read_text()
        self.assertIn('workers.dev/v1/token"', source)
        self.assertNotIn('workers.dev/v1/device/refresh"', source)

    def test_android_json_reads_are_chunk_bounded_before_accumulation(self):
        for path in (AUTH, COORDINATOR):
            source = path.read_text()
            self.assertNotIn("readLine()", source, path.name)
            self.assertIn("char[] chunk = new char[8192]", source, path.name)
            self.assertIn("reader.read(chunk, 0, chunk.length)", source, path.name)
            self.assertIn("length + count > MAX_RESPONSE_CHARS", source, path.name)

    def test_refresh_and_client_bootstrap_share_one_bounded_transient_retry(self):
        source = AUTH.read_text()
        self.assertIn("requestWithRetry(DEVICE_REFRESH_URL, request)", source)
        self.assertIn("requestWithRetry(CLIENT_URL, null)", source)
        self.assertIn("for (int attempt = 0; attempt < 2; attempt++)", source)
        self.assertIn("status == 429 || status >= 500", source)
        self.assertIn("if (attempt == 0 && isRetryableStatus(error.status))", source)
        self.assertIn("responseErrorCode(endpoint, status, text)", source)
        self.assertIn('String code = "HTTP " + status;', source)
        self.assertIn("post(DEVICE_TOKEN_URL, request)", source)
        self.assertNotIn("requestWithRetry(DEVICE_TOKEN_URL", source)

    def test_retryable_http_status_is_classified_before_reading_response_body(self):
        source = AUTH.read_text()
        post = source[source.index("private static JSONObject post("):
                      source.index("private static String responseErrorCode(")]
        get = source[source.index("private static JSONObject get("):
                     source.index("private static String read(")]
        authoritative_check = (
            "if (isRetryableStatus(status)) {\n"
            "                throw new DeviceAuthorizationException(status, \"HTTP \" + status);\n"
            "            }"
        )
        for method in (post, get):
            self.assertIn(authoritative_check, method)
            self.assertLess(method.index(authoritative_check), method.index("getErrorStream()"))
            self.assertLess(method.index(authoritative_check), method.index("String text = read(stream)"))

    def test_existing_encrypted_tokens_bootstrap_and_store_public_client_id(self):
        source = AUTH.read_text()
        self.assertIn('workers.dev/v1/client"', source)
        self.assertIn('static synchronized String clientId(Context context)', source)
        self.assertIn('stored.optString("client_id", "")', source)
        self.assertIn('stored.put("client_id", clientId)', source)
        self.assertIn('token.optString("client_id", "")', source)
        self.assertNotIn('client_secret', source)

    def test_unchanged_match_logs_numeric_state_without_media_identity(self):
        source = COORDINATOR.read_text()
        self.assertIn('"movie unchanged watched="', source)
        self.assertIn('"episode unchanged watched="', source)
        self.assertIn('"episode unresolved provider_id"', source)
        self.assertIn('"episode matched season="', source)
        self.assertIn('" provider_episode_valid="', source)
        self.assertNotIn('"movie unchanged id="', source)
        self.assertNotIn('"episode unchanged id="', source)

    def test_direct_trakt_routes_request_full_runtime_metadata(self):
        source = COORDINATOR.read_text()
        self.assertIn('"/sync/watched/movies?extended=full"', source)
        self.assertIn('"/sync/history/episodes?extended=full"', source)
        self.assertIn('wrapper.optJSONObject("episode")', source)
        self.assertIn('"/sync/playback?extended=full"', source)
        self.assertIn('"&limit=100&page="', source)
        self.assertIn('"X-Pagination-Page-Count"', source)
        self.assertIn("MAX_TRAKT_PAGES", source)
        self.assertIn("target.traktDurationMs", source)
        self.assertEqual(source.count("TraktImportPolicy.selectWatchedDuration("), 2)

    def test_title_is_only_a_shortlist_and_provider_stable_id_confirms(self):
        source = COORDINATOR.read_text()
        self.assertIn("TraktImportPolicy.shortlist", source)
        self.assertIn("providerInfo(task.candidate", source)
        self.assertIn("TraktImportPolicy.sameStableId", source)
        self.assertIn("XtreamUrlBuilder.vodInfoUrl", source)
        self.assertIn("XtreamUrlBuilder.seriesInfoUrl", source)

    def test_import_writes_expected_native_columns_and_safely_omits_insert_id(self):
        source = COORDINATOR.read_text()
        self.assertIn('values.put("last_played_position_ms", position)', source)
        self.assertIn('values.put("duration_ms", duration)', source)
        self.assertIn('values.put("position_ms", position)', source)
        self.assertIn('values.put("series_id", series.id)', source)
        self.assertIn('values.put("episode_xc_id", episodeXcId)', source)
        self.assertIn("safeEpisodeInsertSchema", source)
        insert = source[source.index('database.insert("episode_last_played_positions"'):]
        self.assertNotIn('values.put("id"', insert)

    def test_import_does_not_echo_and_temporary_schema_logging_is_removed(self):
        coordinator = COORDINATOR.read_text()
        bridge = BRIDGE.read_text()
        self.assertIn("mergeExpectedRowsAfterImport", coordinator)
        self.assertIn("mergeExpectedRowsAfterImport", bridge)
        self.assertIn("mergeExpectedRows", bridge)
        reconcile = bridge[bridge.index("mergeExpectedRowsAfterImport"):bridge.index("rowIdentity")]
        self.assertNotIn("readRows(", reconcile)
        self.assertIn("for (String row : expectedRows)", reconcile)
        self.assertIn('return "id=" + candidate.id', coordinator)
        self.assertIn('return "id=" + rowId', coordinator)
        self.assertIn("IMPORT_WRITE.remove()", bridge)
        self.assertNotIn("IMPORT_CAPTURE", bridge)
        self.assertIn("if (commitRequested)", coordinator)
        self.assertNotIn("logSchema", bridge)
        self.assertNotIn("schema table=", bridge)

    def test_running_import_coalesces_one_pending_rerun(self):
        source = COORDINATOR.read_text()
        self.assertIn("PENDING.set(true)", source)
        self.assertIn("while (PENDING.getAndSet(false))", source)
        self.assertIn("if (PENDING.get()) requestImport()", source)

    def test_provider_metadata_resolution_is_parallel_batched_complete_and_deterministic(self):
        source = COORDINATOR.read_text()
        self.assertLess(source.index("addPlayback(playback"), source.index("addWatchedMovies(movies"))
        self.assertIn("PROVIDER_THREADS = 8", source)
        self.assertIn("PROVIDER_BATCH_SIZE = 32", source)
        self.assertIn("MAX_PROVIDER_TASKS = 4096", source)
        self.assertIn("Executors.newFixedThreadPool(PROVIDER_THREADS)", source)
        self.assertIn("for (int start = 0; start < providerTasks.size(); start += PROVIDER_BATCH_SIZE)", source)
        self.assertIn("Math.min(start + PROVIDER_BATCH_SIZE, providerTasks.size())", source)
        self.assertIn("executor.invokeAll(batch)", source)
        self.assertIn("for (Future<Resolution> future : futures)", source)
        self.assertIn('throw new IllegalStateException("provider candidate limit exceeded")', source)
        self.assertIn("TraktImportPolicy.shortlist(candidate.title, candidate.year,", source)
        self.assertIn("resolveProviderCandidates(states, matches)", source)
        self.assertIn("executor.shutdownNow()", source)
        self.assertNotIn("executor.invokeAll(tasks)", source)
        self.assertNotIn("Map<Candidate, JSONObject> providers", source)
        match = source[source.index("private static final class Match"):
                       source.index("private static final class ProviderIdentity")]
        self.assertNotIn("JSONObject", match)
        self.assertIn("providerDurationMs", match)
        self.assertNotIn("matchCategoryBatch", source)

    def test_provider_failures_propagate_before_database_writes(self):
        source = COORDINATOR.read_text()
        worker_start = source.index("new Callable<Resolution>()")
        worker_end = source.index("});", worker_start)
        worker = source[worker_start:worker_end]
        self.assertIn("return new Resolution(task.candidate, task.movie,", worker)
        self.assertIn("providerInfo(task.candidate, task.movie));", worker)
        self.assertNotIn("catch", worker)
        self.assertNotIn("database", worker)
        self.assertIn('throw new IOException("provider HTTP " + status)', source)
        self.assertNotIn("if (status < 200 || status >= 300) return null", source)
        self.assertLess(source.index("resolveProviderCandidates(states, matches)"),
                        source.index("TraktProgressBridge.beginImportWrite()"))

    def test_catalog_is_paged_and_target_driven_not_arbitrarily_truncated(self):
        source = COORDINATOR.read_text()
        self.assertIn("CATALOG_PAGE_SIZE = 500", source)
        self.assertIn("c.id>?", source)
        self.assertIn("ORDER BY c.id LIMIT", source)
        catalog = source[source.index("private static List<Candidate> catalog("):
                         source.index("private static JSONObject providerInfo(")]
        self.assertIn("TraktImportPolicy.shortlistIndex", source)
        self.assertIn("ShortlistIndex targetIndex = shortlistIndex(targets,", catalog)
        self.assertIn("targetIndex.contains(title, year)", catalog)
        self.assertLess(catalog.index("ShortlistIndex targetIndex"),
                        catalog.index("while (true)"))
        self.assertNotIn("for (Target target : targets)", catalog)
        self.assertNotIn("shortlistedByAny", catalog)
        self.assertIn("TargetScan", source)
        self.assertIn("MAX_PROVIDER_TASKS", source)
        self.assertNotIn("MAX_CATALOG", source)

    def test_partial_playback_requires_native_duration_but_watched_may_fallback(self):
        source = COORDINATOR.read_text()
        self.assertEqual(source.count("if (!target.watched && localDuration <= 0L) return null;"), 2)
        self.assertEqual(source.count(
            "TraktImportPolicy.selectWatchedDuration("), 2)

    def test_provider_identity_collects_every_valid_value_and_rejects_conflicts(self):
        source = COORDINATOR.read_text()
        start = source.index("providerIdentity(JSONObject")
        identity = source[start:source.index("durationMs(", start)]
        self.assertIn('new String[]{"tmdb_id", "tmdb"}', identity)
        self.assertIn('new String[]{"imdb_id", "imdb"}', identity)
        self.assertIn("tmdbValues.size() > 1 || imdbValues.size() > 1", identity)
        self.assertIn("boolean stableMatch = !identity.conflict && TraktImportPolicy.sameStableId", source)
        self.assertNotIn("private static String stable(", source)

    def test_episode_insert_schema_uses_pragma_metadata_and_fails_closed(self):
        source = COORDINATOR.read_text()
        self.assertIn("PRAGMA table_info('episode_last_played_positions')", source)
        self.assertIn('"INTEGER".equalsIgnoreCase(type.trim())', source)
        self.assertIn("names.equals(expected) && validId", source)
        self.assertNotIn("SELECT sql FROM sqlite_master", source)

    def test_import_is_wired_at_connected_startup_and_authorization_success(self):
        self.assertIn("TraktImportCoordinator.initialize(application)", EXTENSION.read_text())
        source = AUTH.read_text()
        save_index = source.index("new TokenStore(context).save(token);")
        initialize_index = source.index("TraktImportCoordinator.initialize(context);", save_index)
        self.assertGreater(initialize_index, save_index)

    def test_connected_scope_change_requests_import(self):
        source = AUTH.read_text()
        start = source.index("settings.set(scope)")
        scope_handler = source[start:source.index("actions.addView(button", start)]
        self.assertIn("TraktImportCoordinator.requestImport()", scope_handler)

    def test_import_logs_safe_milestones_without_payloads_or_credentials(self):
        source = COORDINATOR.read_text()
        for marker in (
            '"import start"',
            '"import skipped token"',
            '"import skipped client"',
            '"import fetched movies="',
            '"import skipped targets=0"',
            '"import skipped database_missing"',
        ):
            self.assertIn(marker, source)

    def test_logs_do_not_include_secrets_urls_or_raw_rows(self):
        source = COORDINATOR.read_text()
        self.assertNotIn("Log.i(TAG, token", source)
        self.assertNotIn("Log.i(TAG, url", source)
        self.assertNotIn("Log.i(TAG, candidate", source)
        self.assertNotIn("error.getMessage()", source)


if __name__ == "__main__":
    unittest.main(verbosity=2)
