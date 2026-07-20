from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "morphe/extensions/trakt/src/main/java/com/tivimate/traktpatch/extension"
COORDINATOR = JAVA / "TraktImportCoordinator.java"
BRIDGE = JAVA / "TraktProgressBridge.java"
ON_DEMAND_BRIDGE = JAVA / "TraktOnDemandBridge.java"
AUTH = JAVA / "TraktDeviceAuth.java"
SYNC = JAVA / "TraktSyncClient.java"
PREFERENCE = JAVA / "NativeTraktPreference.java"
EXTENSION = JAVA / "TraktPatchExtension.java"


class TraktImportStaticRegressionTest(unittest.TestCase):
    def test_import_calls_trakt_directly_with_required_headers_and_bounds(self):
        source = COORDINATOR.read_text()
        self.assertIn('TRAKT_API = "https://api.trakt.tv"', source)
        self.assertNotIn("workers.dev", source)
        for route in ("/sync/watched/movies", "/sync/watched/shows", "/sync/playback"):
            self.assertIn(route, source)
        self.assertIn('setRequestMethod("GET")', source)
        self.assertIn('"Bearer " + currentToken', source)
        self.assertIn('setRequestProperty("trakt-api-key", clientId)', source)
        self.assertIn('setRequestProperty("trakt-api-version", "2")', source)
        self.assertIn("TraktDeviceAuth.clientId(context)", source)
        self.assertIn("MAX_PROVIDER_TASKS = 4096", source)
        self.assertIn("MAX_RESPONSE_CHARS = 2_000_000", source)
        self.assertIn("new ArrayBlockingQueue<Runnable>(2)", source)

    def test_refresh_route_matches_worker_contract_exactly(self):
        source = AUTH.read_text()
        self.assertIn('workers.dev/v1/token"', source)
        self.assertNotIn('workers.dev/v1/device/refresh"', source)

    def test_auth_disconnect_revokes_remotely_but_always_clears_locally(self):
        source = AUTH.read_text()
        self.assertIn('workers.dev/v1/revoke"', source)
        self.assertIn('confirmDisconnect', source)
        self.assertIn('disconnectLocally', source)
        self.assertIn('NETWORK.execute', source)
        self.assertIn('request.put("token", refreshToken)', source)
        self.assertIn('revokeWithRetry(refreshToken)', source)
        self.assertIn('for (int attempt = 0; attempt < 3; attempt++)', source)
        self.assertIn('isRetryableRevocationError(error)', source)
        self.assertIn('Thread.sleep(1000L << attempt)', source)
        self.assertIn('error instanceof java.io.IOException', source)
        self.assertIn('isRetryableStatus(((DeviceAuthorizationException) error).status)', source)
        self.assertIn('finally', source)
        self.assertIn('stateChanged.run()', source)

    def test_disconnect_generation_prevents_stale_login_or_refresh_save(self):
        source = AUTH.read_text()
        self.assertIn('AUTH_GENERATION', source)
        self.assertIn('REFRESH_LOCK', source)
        self.assertIn('generation == AUTH_GENERATION', source)
        self.assertIn('saveIfCurrent', source)
        self.assertIn('AUTH_GENERATION++', source)
        self.assertIn('poll(context, dialog, deviceCode, intervalSeconds * 1000L, expiresAt, generation', source)

    def test_disconnect_fences_cached_and_in_flight_imports(self):
        auth = AUTH.read_text()
        source = COORDINATOR.read_text()
        self.assertIn('static long generation()', auth)
        self.assertIn('TraktImportCoordinator.invalidateAuthenticationState()', auth)
        self.assertIn('static void invalidateAuthenticationState()', source)
        invalidate = source[source.index('static void invalidateAuthenticationState()'):
                            source.index('public static void initialize')]
        self.assertIn('synchronized (DATABASE_WRITE_LOCK)', invalidate)
        self.assertIn('cacheSnapshot = null', invalidate)
        self.assertIn('PENDING_SNAPSHOT.set(null)', invalidate)
        self.assertIn('ON_DEMAND_REQUESTS.clear()', invalidate)
        self.assertIn('TraktDeviceAuth.isConnected(context)', source)
        self.assertIn('snapshot.authGeneration == TraktDeviceAuth.generation()', source)
        self.assertGreaterEqual(source.count('generation != TraktDeviceAuth.generation()'), 2)

    def test_disconnect_fences_cached_and_in_flight_outbound_scrobbles(self):
        auth = AUTH.read_text()
        sync = SYNC.read_text()
        self.assertIn('static boolean isCurrentAccessToken', auth)
        self.assertIn('TraktSyncClient.invalidateAuthenticationState()', auth)
        self.assertIn('OUTBOUND_LOCK', sync)
        invalidate = sync[sync.index('static void invalidateAuthenticationState()'):
                          sync.index('public static void initialize')]
        self.assertIn('synchronized (OUTBOUND_LOCK)', invalidate)
        self.assertGreaterEqual(sync.count('TraktDeviceAuth.isCurrentAccessToken('), 5)
        self.assertIn('synchronized (OUTBOUND_LOCK)', sync)

    def test_401_refreshes_and_retries_once_without_treating_403_as_logout(self):
        auth = AUTH.read_text()
        sync = SYNC.read_text()
        self.assertIn('forceRefresh(Context context)', auth)
        self.assertIn('isAuthoritativeInvalidRefresh', auth)
        self.assertNotIn('error.status == 400 || error.status == 401 || error.status == 403', auth)
        self.assertIn('if (status == 401 && attempt == 0)', sync)
        self.assertIn('accessToken = TraktDeviceAuth.forceRefresh(context)', sync)
        self.assertIn('if (status == 403)', sync)
        self.assertNotIn('status == 401 || status == 403', sync)
        coordinator = COORDINATOR.read_text()
        self.assertIn('status == 401 && !authRetried', coordinator)
        self.assertIn('currentToken = TraktDeviceAuth.forceRefresh(context)', coordinator)
        self.assertIn('if (status == 403)', coordinator)
        self.assertNotIn('status == 401 || status == 403', coordinator)

    def test_transient_device_poll_failures_retry_only_until_expiry(self):
        source = AUTH.read_text()
        retry = source[source.index('private static boolean isRetryableDeviceAuthorizationError'):
                       source.index('private static JSONObject post')]
        self.assertIn('error instanceof java.io.IOException', retry)
        self.assertIn('isRetryableStatus', retry)
        self.assertIn('System.currentTimeMillis() >= expiresAt', source)

    def test_native_preference_state_updates_after_auth_changes(self):
        source = PREFERENCE.read_text()
        self.assertIn('refreshState()', source)
        self.assertNotIn('setTitle(', source)
        self.assertNotIn('setSummary(', source)
        self.assertIn('setRuntimeField("ـˆ", connected', source)
        self.assertIn('setRuntimeField("ᴵʼ", connected', source)
        self.assertIn('TraktDeviceAuth.open(context, this::refreshState)', source)

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

    def test_production_import_removes_per_match_diagnostics_and_media_identity(self):
        source = COORDINATOR.read_text()
        self.assertNotIn('"movie unchanged watched="', source)
        self.assertNotIn('"episode unchanged watched="', source)
        self.assertNotIn('"episode unresolved provider_id"', source)
        self.assertNotIn('"episode matched season="', source)
        self.assertNotIn('" provider_episode_valid="', source)
        self.assertNotIn('"movie unchanged id="', source)
        self.assertNotIn('"episode unchanged id="', source)

    def test_direct_trakt_routes_request_complete_authoritative_watched_state(self):
        source = COORDINATOR.read_text()
        self.assertIn('"/sync/watched/movies?extended=full"', source)
        self.assertIn('"/sync/watched/shows?extended=full"', source)
        self.assertIn('"/shows/" + traktId', source)
        self.assertIn('"/progress/watched?hidden=false&specials=false&count_specials=false"', source)
        self.assertIn('episode.optBoolean("completed", false)', source)
        self.assertIn('localSeriesTitles(context)', source)
        self.assertIn('!localTitles.contains(normalized)', source)
        self.assertIn('throw new IOException("watched progress unavailable")', source)
        self.assertIn('DETAIL_CHECKPOINT_PREFS', source)
        self.assertIn('DETAIL_CHECKPOINT_KEY', source)
        self.assertIn('Math.floorMod(cursor, eligible.size())', source)
        self.assertIn('selected.size() < MAX_WATCHED_SHOW_DETAILS_PER_IMPORT', source)
        self.assertIn('.edit().putInt(DETAIL_CHECKPOINT_KEY, cursor).apply()', source)
        self.assertNotIn('if (futures.size() >= MAX_WATCHED_SHOW_DETAILS_PER_IMPORT) continue;', source)
        self.assertIn('wrapper.optJSONObject("episode")', source)
        self.assertIn('"/sync/playback?extended=full"', source)
        self.assertIn('"&limit=100&page="', source)
        self.assertIn('"X-Pagination-Page-Count"', source)
        self.assertIn("MAX_TRAKT_PAGES", source)
        self.assertNotIn("MAX_EPISODE_HISTORY_PAGES", source)
        self.assertIn("target.traktDurationMs", source)
        self.assertEqual(source.count("TraktImportPolicy.selectWatchedDuration("), 2)

    def test_episode_import_reconciles_only_complete_authoritative_watched_sets(self):
        source = COORDINATOR.read_text()
        self.assertIn("providerEpisodeIds", source)
        self.assertIn("reconcileEpisodes(database, matches, removedEpisodes)", source)
        self.assertIn('database.delete("episode_last_played_positions"', source)
        self.assertIn('parent.put("last_episode_xc_id", preferredEpisodeId)', source)
        self.assertIn("removedEpisodeIdentities", BRIDGE.read_text())
        self.assertIn("target.authoritativeSeriesSet = true", source)
        self.assertIn("state.authoritativeMaterialized", source)
        self.assertIn("if (!state.authoritativeMaterialized || !state.complete", source)
        playback = source[source.index("private static void addPlayback("):
                          source.index("private static Target mediaTarget(")]
        self.assertNotIn("target.authoritativeSeriesSet = true", playback)

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
        self.assertIn('values.put("last_turn_on_time", System.currentTimeMillis())', source)
        self.assertIn('values.put("position_ms", position)', source)
        self.assertIn('values.put("series_id", series.id)', source)
        self.assertIn('values.put("episode_xc_id", episodeXcId)', source)
        self.assertIn("ensureSeriesHistory(database, series.id, episodeXcId)", source)
        self.assertIn('parent.put("last_episode_xc_id", episodeXcId)', source)
        self.assertIn("safeEpisodeInsertSchema", source)
        insert = source[source.index('database.insert("episode_last_played_positions"'):]
        self.assertNotIn('values.put("id"', insert)

    def test_import_commit_and_expected_baseline_merge_are_atomic_against_capture(self):
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
        self.assertIn("ReentrantLock CAPTURE_EPOCH", bridge)
        self.assertIn("CAPTURE_EPOCH.lock()", bridge)
        self.assertIn("CAPTURE_EPOCH.unlock()", bridge)
        begin = coordinator.index("TraktProgressBridge.beginImportWrite()")
        commit = coordinator.index("database.endTransaction()", begin)
        merge = coordinator.index("TraktProgressBridge.mergeExpectedRowsAfterImport(", commit)
        end = coordinator.index("TraktProgressBridge.endImportWrite()", merge)
        self.assertLess(begin, commit)
        self.assertLess(commit, merge)
        self.assertLess(merge, end)
        self.assertNotIn("logSchema", bridge)
        self.assertNotIn("schema table=", bridge)

    def test_running_import_coalesces_one_pending_rerun(self):
        source = COORDINATOR.read_text()
        self.assertIn("PENDING.set(true)", source)
        self.assertIn("while (PENDING.getAndSet(false))", source)
        self.assertIn("if (PENDING.get()) requestImport()", source)

    def test_on_demand_requests_use_a_bounded_keyed_coalescing_scheduler(self):
        source = COORDINATOR.read_text()
        self.assertIn("MAX_ON_DEMAND_KEYS = 32", source)
        self.assertIn("new ArrayBlockingQueue<Runnable>(1)", source)
        self.assertIn('String key = media + ":" + playlistId + ":" + xcId;', source)
        self.assertIn("ON_DEMAND_REQUESTS.get(key)", source)
        self.assertIn("existing.rerun = true", source)
        self.assertIn("ON_DEMAND_REQUESTS.size() >= MAX_ON_DEMAND_KEYS", source)
        self.assertIn("drainOnDemand()", source)
        movie = source[source.index("public static void requestOpenedMovie"):source.index("public static void requestOpenedSeries")]
        series = source[source.index("public static void requestOpenedSeries"):source.index("public static void requestImport")]
        self.assertIn('requestOpened(context, "movie", playlistId, xcId)', movie)
        self.assertIn('requestOpened(context, "episode", playlistId, xcId)', series)
        self.assertNotIn("IMPORTS.execute", movie + series)

    def test_detail_open_identity_is_scoped_to_the_exact_playlist(self):
        bridge = ON_DEMAND_BRIDGE.read_text()
        coordinator = COORDINATOR.read_text()
        self.assertIn("private static long playlistId(Object params)", bridge)
        self.assertIn("field.getType() == long.class", bridge)
        self.assertIn("java.lang.reflect.Modifier.isTransient(field.getModifiers())", bridge)
        self.assertIn("if (++candidateCount > 1) return 0L", bridge)
        self.assertIn("requestOpenedMovie(playlistId, xcId)", bridge)
        self.assertIn("requestOpenedSeries(playlistId, xcId)", bridge)
        self.assertIn("if (playlistId <= 0L) {", bridge)
        self.assertIn("TraktImportCoordinator.requestImport()", bridge)
        self.assertIn("WHERE c.playlist_id=? AND c.xc_id=?", coordinator)
        self.assertIn("new String[]{String.valueOf(playlistId), String.valueOf(xcId)}", coordinator)

    def test_opened_series_can_authoritatively_clear_its_final_watched_episode(self):
        source = COORDINATOR.read_text()
        self.assertIn("authoritativeEmptySeriesSet", source)
        self.assertIn("empty.authoritativeSeriesSet = true", source)
        self.assertIn("empty.authoritativeEmptySeriesSet = true", source)
        self.assertIn('throw new IOException("watched progress unavailable")', source)
        reconcile = source[source.index("private static int reconcileEpisodes("):
                           source.index("private static int reconcileSeriesParent(")]
        self.assertIn("match.target.authoritativeEmptySeriesSet", reconcile)
        self.assertNotIn("state.keepIds.isEmpty()", reconcile)

    def test_opened_movie_absent_from_authoritative_remote_state_is_cleared(self):
        source = COORDINATOR.read_text()
        opened = source[source.index("private static void syncOpened("):
                        source.index("private static Candidate openedCandidate(")]
        self.assertIn("empty.authoritativeEmptyMovieState = true", opened)
        self.assertIn("active.isEmpty() && watchedMovies.isEmpty()", opened)
        update = source[source.index("private static String updateMovie("):
                        source.index("private static String updateEpisode(")]
        self.assertIn("target.authoritativeEmptyMovieState", update)
        self.assertIn('values.put("last_played_position_ms", 0L)', update)
        self.assertIn('values.put("last_turn_on_time", 0L)', update)
        self.assertIn("localPosition == 0L && localLastTurn == 0L", update)

    def test_cache_refresh_is_single_flight_and_failure_backed_off(self):
        source = COORDINATOR.read_text()
        self.assertIn("CACHE_REFRESH_RUNNING.compareAndSet(false, true)", source)
        self.assertIn("CACHE_REFRESH_RETRY_MS", source)
        self.assertIn("cacheRefreshRetryAt", source)
        self.assertIn("now < cacheRefreshRetryAt", source)
        self.assertIn("CACHE_REFRESH_RUNNING.set(false)", source)
        self.assertIn("refreshCacheSingleFlight(context)", source)

    def test_maintenance_enrichment_does_not_mutate_shared_cache_json(self):
        source = COORDINATOR.read_text()
        self.assertIn("JSONArray shows = new JSONArray(snapshot.shows.toString())", source)
        self.assertNotIn("JSONArray shows = snapshot.shows;", source)

    def test_open_fallback_covers_early_failures_and_reuses_fresh_cache(self):
        source = COORDINATOR.read_text()
        opened = source[source.index("private static void syncOpened("):source.index("private static Candidate openedCandidate(")]
        self.assertLess(opened.index("try {"), opened.index("freshCache(context)"))
        self.assertIn("finally", opened)
        self.assertIn("if (!providerReconciled) requestFallbackImport(context, snapshot)", opened)
        self.assertIn("providerReconciled = apply(database, categories, targetCount) > 0", opened)
        self.assertIn("requestImport(snapshot)", source)
        self.assertIn("CacheSnapshot preferredSnapshot", source)
        self.assertIn("isFresh(preferredSnapshot, System.currentTimeMillis())", source)
        self.assertNotIn("OPEN_FALLBACK_INTERVAL_MS", source)

    def test_on_demand_and_long_running_maintenance_have_separate_bounded_paths(self):
        source = COORDINATOR.read_text()
        self.assertIn("private static final ExecutorService ON_DEMAND", source)
        self.assertIn("private static final ExecutorService MAINTENANCE", source)
        self.assertIn("new ArrayBlockingQueue<Runnable>(2)", source)
        self.assertIn("synchronized (DATABASE_WRITE_LOCK)", source)
        self.assertNotIn("Executors.newSingleThreadExecutor()", source)

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

    def test_all_stable_id_catalog_duplicates_receive_native_state(self):
        source = COORDINATOR.read_text()
        self.assertIn("final List<Match> matches = new ArrayList<>()", source)
        self.assertIn("scan.matches.add(match)", source)
        self.assertIn("matches.addAll(scan.matches)", source)
        self.assertNotIn("scan.match.candidate.catalogOrder", source)
        self.assertNotIn("scan.match = match", source)

    def test_every_duplicate_requires_its_own_nonconflicting_stable_id_match(self):
        source = COORDINATOR.read_text()
        apply = source[source.index("private static void applyResolutions("):
                       source.index("private static boolean shortlisted(")]
        self.assertNotIn("confirmedTargets", apply)
        self.assertNotIn("confirmedSibling", apply)
        self.assertIn("if (!stableMatch) continue;", apply)
        self.assertIn("!identity.conflict && TraktImportPolicy.sameStableId", apply)
        self.assertIn("applyResolutions(states, resolutions)", source)
        self.assertIn("matches.addAll(scan.matches)", source)

    def test_duration_only_rows_are_never_exported_for_movies_or_episodes(self):
        bridge = BRIDGE.read_text()
        read_rows = bridge[bridge.index("private static List<String> readRows") :]
        self.assertIn("FROM movies WHERE last_played_position_ms > 0", read_rows)
        self.assertNotIn("last_played_position_ms > 0 OR duration_ms > 0", read_rows)
        self.assertIn("FROM episode_last_played_positions WHERE position_ms > 0", read_rows)
        self.assertEqual(bridge.count("if (positionMs <= 0L) continue;"), 2)

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
        save_index = source.index("saveIfCurrent(new TokenStore(context), token, generation)")
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
