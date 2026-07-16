package com.tivimate.traktpatch.extension;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded, fail-closed Trakt -> TiviMate importer. Import orchestration and DB writes are serialized; provider metadata requests use a bounded pool. */
public final class TraktImportCoordinator {
    private static final String TAG = "TiviMateTraktImport";
    private static final String TRAKT_API = "https://api.trakt.tv";
    private static final String DATABASE_NAME = "TvPlayer.db";
    private static final String[] ROUTES = {"/sync/watched/movies?extended=full",
            "/sync/watched/shows?extended=full", "/sync/playback?extended=full"};
    private static final int CATALOG_PAGE_SIZE = 500;
    private static final int PROVIDER_THREADS = 8;
    private static final int PROVIDER_BATCH_SIZE = 32;
    private static final int MAX_PROVIDER_TASKS = 4096;
    private static final int MAX_RESPONSE_CHARS = 2_000_000;
    private static final int MAX_TRAKT_PAGES = 100;
    private static final int MAX_WATCHED_SHOW_DETAILS_PER_IMPORT = 8;
    private static final int RECENT_WATCHED_SHOW_DETAILS_PER_IMPORT = 4;
    private static final String DETAIL_CHECKPOINT_PREFS = "trakt_import_checkpoint";
    private static final String DETAIL_CHECKPOINT_KEY = "watched_show_cursor";
    private static final int TRAKT_DETAIL_THREADS = 2;
    private static final ExecutorService IMPORTS = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final AtomicBoolean PENDING = new AtomicBoolean();
    private static volatile Context applicationContext;

    private TraktImportCoordinator() { }

    public static void initialize(Context context) {
        if (context == null) return;
        applicationContext = context.getApplicationContext();
        if (TraktDeviceAuth.isConnected(applicationContext)) requestImport();
    }

    /** Safe to call from startup or authorization UI; this method only enqueues. */
    public static void requestImport() {
        final Context context = applicationContext;
        if (context == null) return;
        PENDING.set(true);
        if (!RUNNING.compareAndSet(false, true)) return;
        IMPORTS.execute(new Runnable() {
            @Override public void run() {
                try {
                    while (PENDING.getAndSet(false)) {
                        try { importNow(context); }
                        catch (Exception error) {
                            Throwable root = error;
                            while (root.getCause() != null) root = root.getCause();
                            Log.w(TAG, "import failed type=" + error.getClass().getSimpleName()
                                    + " root=" + root.getClass().getSimpleName());
                        }
                    }
                } finally {
                    RUNNING.set(false);
                    // Close the request-vs-finally race without queuing more than one rerun.
                    if (PENDING.get()) requestImport();
                }
            }
        });
    }

    private static void importNow(Context context) throws Exception {
        Log.i(TAG, "import start");
        String token = TraktDeviceAuth.accessToken(context);
        if (token == null) { Log.i(TAG, "import skipped token"); return; }
        String clientId = TraktDeviceAuth.clientId(context);
        if (clientId == null) { Log.i(TAG, "import skipped client"); return; }
        JSONArray movies = fetch(ROUTES[0], token, clientId, context);
        JSONArray shows = fetch(ROUTES[1], token, clientId, context);
        JSONArray playback = fetch(ROUTES[2], token, clientId, context);
        if (movies == null || shows == null || playback == null) return;
        if (TraktDeviceAuth.showsEnabled(context)) {
            shows = enrichWatchedShows(shows, token, clientId, context,
                    localSeriesTitles(context));
        }
        Log.i(TAG, "import fetched movies=" + movies.length() + " shows=" + shows.length()
                + " playback=" + playback.length());
        LinkedHashMap<String, Target> active = new LinkedHashMap<>();
        LinkedHashMap<String, Target> watchedMovies = new LinkedHashMap<>();
        LinkedHashMap<String, Target> watchedShows = new LinkedHashMap<>();
        addPlayback(playback, active, context);
        if (TraktDeviceAuth.moviesEnabled(context)) addWatchedMovies(movies, watchedMovies);
        if (TraktDeviceAuth.showsEnabled(context)) addWatchedShows(shows, watchedShows);
        // Playback is processed first. Remove overlaps from watched categories while
        // retaining watched dominance on the playback target.
        mergeOverlaps(active, watchedMovies);
        mergeOverlaps(active, watchedShows);
        List<List<Target>> categories = new ArrayList<>();
        categories.add(new ArrayList<>(active.values()));
        categories.add(new ArrayList<>(watchedMovies.values()));
        categories.add(new ArrayList<>(watchedShows.values()));
        int targetCount = active.size() + watchedMovies.size() + watchedShows.size();
        if (targetCount == 0) { Log.i(TAG, "import skipped targets=0"); return; }

        java.io.File file = context.getDatabasePath(DATABASE_NAME);
        if (!file.isFile()) { Log.i(TAG, "import skipped database_missing"); return; }
        SQLiteDatabase database = null;
        try {
            database = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
            apply(database, categories, targetCount);
        } finally {
            if (database != null) database.close();
        }
    }

    private static JSONArray fetch(String route, String token, String clientId, Context context) throws Exception {
        JSONArray result = new JSONArray();
        int pageCount = 1;
        for (int page = 1; page <= MAX_TRAKT_PAGES; page++) {
            boolean loaded = false;
            for (int attempt = 0; attempt < 2; attempt++) {
                HttpURLConnection connection = (HttpURLConnection) new URL(TRAKT_API + route
                        + "&limit=100&page=" + page).openConnection();
                try {
                    connection.setInstanceFollowRedirects(false);
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(15_000);
                    connection.setReadTimeout(20_000);
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                    connection.setRequestProperty("trakt-api-key", clientId);
                    connection.setRequestProperty("trakt-api-version", "2");
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setRequestProperty("User-Agent", "TiviMate-Trakt-Patch/1.0");
                    int status = connection.getResponseCode();
                    if (status == 401 || status == 403) {
                        TraktDeviceAuth.invalidateAccessToken(context);
                        Log.w(TAG, "import authorization rejected");
                        return null;
                    }
                    if (attempt == 0 && (status == 429 || status >= 500)) {
                        Thread.sleep(1000L);
                        continue;
                    }
                    if (status < 200 || status >= 300) {
                        Log.w(TAG, "import route rejected status=" + status);
                        return null;
                    }
                    JSONArray values = new JSONArray(readText(connection.getInputStream()));
                    for (int index = 0; index < values.length(); index++) {
                        result.put(values.get(index));
                    }
                    String pages = connection.getHeaderField("X-Pagination-Page-Count");
                    if (pages != null && pages.matches("[0-9]+")) {
                        pageCount = Math.max(1, Integer.parseInt(pages));
                    }
                    loaded = true;
                    break;
                } finally { connection.disconnect(); }
            }
            if (!loaded) return null;
            if (page >= pageCount) return result;
        }
        throw new IllegalStateException("Trakt pagination limit exceeded");
    }

    private static Set<String> localSeriesTitles(Context context) throws Exception {
        java.io.File file = context.getDatabasePath(DATABASE_NAME);
        if (!file.isFile()) throw new IOException("database missing");
        Set<String> result = new HashSet<>();
        SQLiteDatabase database = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                SQLiteDatabase.OPEN_READONLY);
        try {
            Cursor cursor = database.rawQuery("SELECT name FROM series", null);
            try {
                while (cursor.moveToNext()) {
                    String title = cursor.isNull(0) ? "" :
                            TraktImportPolicy.normalizedTitle(cursor.getString(0));
                    if (!title.isEmpty()) result.add(title);
                }
            } finally {
                cursor.close();
            }
        } finally {
            database.close();
        }
        return result;
    }

    /**
     * Trakt can return watched-show aggregates without nested seasons. Resolve those
     * aggregates through the authoritative progress resource before reconciliation.
     */
    private static JSONArray enrichWatchedShows(JSONArray values, final String token,
                                                final String clientId, final Context context,
                                                Set<String> localTitles)
            throws Exception {
        List<JSONObject> eligible = new ArrayList<>();
        List<JSONObject> wrappers = new ArrayList<>();
        List<Future<JSONObject>> futures = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(TRAKT_DETAIL_THREADS);
        try {
            for (int i = 0; i < values.length(); i++) {
                JSONObject wrapper = values.optJSONObject(i);
                if (wrapper == null || wrapper.optJSONArray("seasons") != null) continue;
                JSONObject show = wrapper.optJSONObject("show");
                String normalized = show == null ? "" :
                        TraktImportPolicy.normalizedTitle(show.optString("title", ""));
                if (normalized.isEmpty() || !localTitles.contains(normalized)) continue;
                eligible.add(wrapper);
            }
            if (eligible.isEmpty()) return values;

            java.util.LinkedHashSet<JSONObject> selected = new java.util.LinkedHashSet<>();
            int recent = Math.min(RECENT_WATCHED_SHOW_DETAILS_PER_IMPORT, eligible.size());
            for (int i = 0; i < recent; i++) selected.add(eligible.get(i));
            int cursor = context.getSharedPreferences(DETAIL_CHECKPOINT_PREFS,
                    Context.MODE_PRIVATE).getInt(DETAIL_CHECKPOINT_KEY, 0);
            cursor = Math.floorMod(cursor, eligible.size());
            int scanned = 0;
            while (selected.size() < MAX_WATCHED_SHOW_DETAILS_PER_IMPORT
                    && scanned < eligible.size()) {
                selected.add(eligible.get(cursor));
                cursor = (cursor + 1) % eligible.size();
                scanned++;
            }

            for (final JSONObject wrapper : selected) {
                JSONObject show = wrapper.optJSONObject("show");
                JSONObject ids = show.optJSONObject("ids");
                final long traktId = ids == null ? 0L : ids.optLong("trakt", 0L);
                if (traktId <= 0L) {
                    throw new IllegalStateException("watched show missing Trakt id");
                }
                wrappers.add(wrapper);
                futures.add(pool.submit(new Callable<JSONObject>() {
                    @Override public JSONObject call() throws Exception {
                        return fetchObject("/shows/" + traktId
                                + "/progress/watched?hidden=false&specials=false&count_specials=false",
                                token, clientId, context);
                    }
                }));
            }
            for (int i = 0; i < futures.size(); i++) {
                JSONObject progress = futures.get(i).get();
                if (progress == null) throw new IOException("watched progress unavailable");
                wrappers.get(i).put("seasons", completedSeasons(progress));
            }
            context.getSharedPreferences(DETAIL_CHECKPOINT_PREFS, Context.MODE_PRIVATE)
                    .edit().putInt(DETAIL_CHECKPOINT_KEY, cursor).apply();
            if (!futures.isEmpty()) {
                Log.i(TAG, "import enriched watched_shows=" + futures.size());
            }
            return values;
        } finally {
            pool.shutdownNow();
        }
    }

    private static JSONArray completedSeasons(JSONObject progress) throws Exception {
        JSONArray result = new JSONArray();
        JSONArray seasons = progress == null ? null : progress.optJSONArray("seasons");
        if (seasons == null) return result;
        for (int s = 0; s < seasons.length(); s++) {
            JSONObject sourceSeason = seasons.optJSONObject(s);
            int seasonNumber = sourceSeason == null ? 0 : sourceSeason.optInt("number", 0);
            JSONArray sourceEpisodes = sourceSeason == null
                    ? null : sourceSeason.optJSONArray("episodes");
            if (seasonNumber <= 0 || sourceEpisodes == null) continue;
            JSONArray completed = new JSONArray();
            for (int e = 0; e < sourceEpisodes.length(); e++) {
                JSONObject episode = sourceEpisodes.optJSONObject(e);
                int episodeNumber = episode == null ? 0 : episode.optInt("number", 0);
                if (episodeNumber > 0 && episode.optBoolean("completed", false)) {
                    JSONObject value = new JSONObject();
                    value.put("number", episodeNumber);
                    completed.put(value);
                }
            }
            if (completed.length() > 0) {
                JSONObject season = new JSONObject();
                season.put("number", seasonNumber);
                season.put("episodes", completed);
                result.put(season);
            }
        }
        return result;
    }

    private static JSONObject fetchObject(String route, String token, String clientId,
                                          Context context) throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(TRAKT_API + route)
                    .openConnection();
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15_000);
                connection.setReadTimeout(20_000);
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setRequestProperty("trakt-api-key", clientId);
                connection.setRequestProperty("trakt-api-version", "2");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "TiviMate-Trakt-Patch/1.0");
                int status = connection.getResponseCode();
                if (status == 401 || status == 403) {
                    TraktDeviceAuth.invalidateAccessToken(context);
                    Log.w(TAG, "import authorization rejected");
                    return null;
                }
                if (attempt < 2 && (status == 429 || status >= 500)) {
                    Thread.sleep((attempt + 1L) * 1000L);
                    continue;
                }
                if (status < 200 || status >= 300) {
                    throw new IOException("watched progress rejected status=" + status);
                }
                return new JSONObject(readText(connection.getInputStream()));
            } catch (IOException error) {
                if (attempt < 2) {
                    Thread.sleep((attempt + 1L) * 1000L);
                    continue;
                }
                throw error;
            } finally {
                connection.disconnect();
            }
        }
        return null;
    }

    private static void addWatchedMovies(JSONArray values, Map<String, Target> targets) {
        for (int i = 0; i < values.length(); i++) {
            JSONObject wrapper = values.optJSONObject(i);
            JSONObject movie = wrapper == null ? null : wrapper.optJSONObject("movie");
            Target target = mediaTarget("movie", movie, true, 100.0d);
            put(targets, target);
        }
    }

    private static void addWatchedShows(JSONArray values, Map<String, Target> targets) {
        for (int i = 0; i < values.length(); i++) {
            JSONObject wrapper = values.optJSONObject(i);
            JSONObject show = wrapper == null ? null : wrapper.optJSONObject("show");
            JSONObject flatEpisode = wrapper == null ? null : wrapper.optJSONObject("episode");
            if (show != null && flatEpisode != null) {
                Target target = mediaTarget("episode", show, true, 100.0d);
                if (target != null) {
                    target.season = flatEpisode.optInt("season", 0);
                    target.episode = flatEpisode.optInt("number", 0);
                    long runtimeMinutes = flatEpisode.optLong("runtime", 0L);
                    if (runtimeMinutes > 0L && runtimeMinutes < 1440L) {
                        target.traktDurationMs = runtimeMinutes * 60_000L;
                    }
                    if (target.season > 0 && target.episode > 0) put(targets, target);
                }
                continue;
            }
            JSONArray seasons = wrapper == null ? null : wrapper.optJSONArray("seasons");
            if (show == null || seasons == null) continue;
            for (int s = 0; s < seasons.length(); s++) {
                JSONObject season = seasons.optJSONObject(s);
                JSONArray episodes = season == null ? null : season.optJSONArray("episodes");
                int seasonNumber = season == null ? 0 : season.optInt("number", 0);
                if (episodes == null || seasonNumber <= 0) continue;
                for (int e = 0; e < episodes.length(); e++) {
                    JSONObject episode = episodes.optJSONObject(e);
                    int episodeNumber = episode == null ? 0 : episode.optInt("number", 0);
                    Target target = mediaTarget("episode", show, true, 100.0d);
                    if (target != null && episodeNumber > 0) {
                        target.season = seasonNumber;
                        target.episode = episodeNumber;
                        put(targets, target);
                    }
                }
            }
        }
    }

    private static void addPlayback(JSONArray values, Map<String, Target> targets, Context context) {
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.optJSONObject(i);
            if (value == null) continue;
            String type = value.optString("type", "");
            double progress = value.optDouble("progress", 0.0d);
            if ("movie".equals(type) && TraktDeviceAuth.moviesEnabled(context)) {
                put(targets, mediaTarget("movie", value.optJSONObject("movie"), false, progress));
            } else if ("episode".equals(type) && TraktDeviceAuth.showsEnabled(context)) {
                Target target = mediaTarget("episode", value.optJSONObject("show"), false, progress);
                JSONObject episode = value.optJSONObject("episode");
                if (target != null && episode != null) {
                    target.season = episode.optInt("season", 0);
                    target.episode = episode.optInt("number", 0);
                    long runtimeMinutes = episode.optLong("runtime", 0L);
                    if (runtimeMinutes > 0L && runtimeMinutes < 1440L) {
                        target.traktDurationMs = runtimeMinutes * 60_000L;
                    }
                    if (target.season > 0 && target.episode > 0) put(targets, target);
                }
            }
        }
    }

    private static Target mediaTarget(String type, JSONObject media, boolean watched, double progress) {
        if (media == null) return null;
        JSONObject ids = media.optJSONObject("ids");
        String tmdb = ids == null ? "" : TraktImportPolicy.stableTmdb(ids.opt("tmdb"));
        String imdb = ids == null ? "" : TraktImportPolicy.stableImdb(ids.opt("imdb"));
        if (tmdb.isEmpty() && imdb.isEmpty()) return null;
        Target value = new Target();
        value.type = type;
        value.tmdb = tmdb;
        value.imdb = imdb;
        value.title = media.optString("title", "");
        value.year = media.optInt("year", 0);
        value.watched = watched;
        value.progress = progress;
        long runtimeMinutes = media.optLong("runtime", 0L);
        if (runtimeMinutes > 0L && runtimeMinutes < 1440L) {
            value.traktDurationMs = runtimeMinutes * 60_000L;
        }
        return value;
    }

    private static void put(Map<String, Target> targets, Target value) {
        if (value == null) return;
        String key = value.key();
        Target old = targets.get(key);
        if (old == null || value.watched || (!old.watched && value.progress > old.progress)) targets.put(key, value);
    }

    private static void mergeOverlaps(Map<String, Target> priority, Map<String, Target> watched) {
        for (Map.Entry<String, Target> entry : new ArrayList<>(watched.entrySet())) {
            Target existing = priority.get(entry.getKey());
            if (existing == null) continue;
            if (entry.getValue().watched) existing.watched = true;
            existing.progress = Math.max(existing.progress, entry.getValue().progress);
            existing.traktDurationMs = Math.max(existing.traktDurationMs,
                    entry.getValue().traktDurationMs);
            watched.remove(entry.getKey());
        }
    }

    private static void apply(SQLiteDatabase database, List<List<Target>> categories,
                              int targetCount) throws Exception {
        List<Target> movieTargets = new ArrayList<>();
        List<Target> showTargets = new ArrayList<>();
        for (List<Target> category : categories) for (Target target : category) {
            ("movie".equals(target.type) ? movieTargets : showTargets).add(target);
        }
        List<Candidate> movies = catalog(database, "movies", movieTargets, MAX_PROVIDER_TASKS);
        List<Candidate> series = catalog(database, "series", showTargets,
                MAX_PROVIDER_TASKS - movies.size());
        List<Match> matches = new ArrayList<>();
        List<CategoryState> states = new ArrayList<>();
        states.add(new CategoryState(categories.get(0), movies, series));
        states.add(new CategoryState(categories.get(1), movies, series));
        states.add(new CategoryState(categories.get(2), movies, series));
        int requests = resolveProviderCandidates(states, matches);

        int changed = 0;
        if (matches.isEmpty()) {
            Log.i(TAG, "import complete targets=" + targetCount + " provider_requests=" + requests + " matches=0 changed=0");
            return;
        }
        Set<String> expectedMovies = new HashSet<>();
        Set<String> expectedEpisodes = new HashSet<>();
        Set<String> removedEpisodes = new HashSet<>();
        boolean transactionStarted = false;
        boolean commitRequested = false;
        TraktProgressBridge.beginImportWrite();
        try {
            database.beginTransaction();
            transactionStarted = true;
            for (Match match : matches) {
                if ("movie".equals(match.target.type)) {
                    String expected = updateMovie(database, match.candidate, match.target,
                            match.providerDurationMs);
                    if (expected != null) {
                        changed++;
                        expectedMovies.add(expected);
                    }
                } else {
                    String expected = updateEpisode(database, match.candidate, match.target,
                            match.episodeXcId, match.providerDurationMs);
                    if (expected != null) {
                        changed++;
                        expectedEpisodes.add(expected);
                    }
                }
            }
            changed += reconcileEpisodes(database, matches, removedEpisodes);
            database.setTransactionSuccessful();
            commitRequested = true;
        } finally {
            try {
                if (transactionStarted) {
                    database.endTransaction();
                    if (commitRequested) {
                        TraktProgressBridge.mergeExpectedRowsAfterImport(
                                expectedMovies, expectedEpisodes, removedEpisodes);
                    }
                }
            } finally {
                TraktProgressBridge.endImportWrite();
            }
        }
        Log.i(TAG, "import complete targets=" + targetCount + " provider_requests=" + requests
                + " matches=" + matches.size() + " changed=" + changed);
    }

    private static int resolveProviderCandidates(List<CategoryState> states,
                                                 List<Match> matches) throws Exception {
        final List<ProviderTask> providerTasks = new ArrayList<>();
        Set<Candidate> submitted = java.util.Collections.newSetFromMap(
                new IdentityHashMap<Candidate, Boolean>());
        // Build the complete finite shortlist before opening the pool. Exceeding the
        // bound aborts before any database write rather than producing a partial import.
        for (CategoryState state : states) for (TargetScan scan : state.scans) {
            for (Candidate candidate : scan.candidates) {
                if (!shortlisted(candidate, scan.target) || submitted.contains(candidate)) continue;
                if (providerTasks.size() >= MAX_PROVIDER_TASKS) {
                    throw new IllegalStateException("provider candidate limit exceeded");
                }
                submitted.add(candidate);
                providerTasks.add(new ProviderTask(candidate, "movie".equals(scan.target.type)));
            }
        }

        final List<Resolution> resolutions = new ArrayList<>(providerTasks.size());
        ExecutorService executor = Executors.newFixedThreadPool(PROVIDER_THREADS);
        try {
            for (int start = 0; start < providerTasks.size(); start += PROVIDER_BATCH_SIZE) {
                int end = Math.min(start + PROVIDER_BATCH_SIZE, providerTasks.size());
                List<Callable<Resolution>> batch = new ArrayList<>(end - start);
                for (int index = start; index < end; index++) {
                    final ProviderTask task = providerTasks.get(index);
                    batch.add(new Callable<Resolution>() {
                        @Override public Resolution call() throws Exception {
                            return new Resolution(task.candidate, task.movie,
                                    providerInfo(task.candidate, task.movie));
                        }
                    });
                }
                // invokeAll and Future iteration preserve task-list order regardless of
                // completion order. Any network, HTTP, or JSON failure reaches get() and
                // aborts before beginImportWrite/database.beginTransaction.
                List<Future<Resolution>> futures = executor.invokeAll(batch);
                for (Future<Resolution> future : futures) {
                    resolutions.add(future.get());
                }
            }
        } finally {
            executor.shutdownNow();
        }

        applyResolutions(states, resolutions);
        // Category, target, and catalog insertion order determine write order. A target
        // first requires one provider stable-ID confirmation. Exact title/year provider
        // siblings then receive native state with their own movie/episode IDs.
        for (CategoryState state : states) for (TargetScan scan : state.scans) {
            matches.addAll(scan.matches);
        }
        return providerTasks.size();
    }

    private static void applyResolutions(List<CategoryState> states,
                                         List<Resolution> resolutions) {
        Set<Target> confirmedTargets = java.util.Collections.newSetFromMap(
                new IdentityHashMap<Target, Boolean>());
        // Pass one: a provider stable ID must independently authorize each Trakt target.
        for (Resolution resolution : resolutions) {
            JSONObject info = resolution.provider.optJSONObject("info");
            JSONObject movie = resolution.provider.optJSONObject("movie_data");
            ProviderIdentity identity = providerIdentity(info, movie);
            for (CategoryState state : states) for (TargetScan scan : state.scans) {
                if (("movie".equals(scan.target.type)) != resolution.movie
                        || !shortlisted(resolution.candidate, scan.target)) continue;
                boolean stableMatch = !identity.conflict && TraktImportPolicy.sameStableId(
                        scan.target.tmdb, scan.target.imdb, identity.tmdb, identity.imdb);
                if (stableMatch) confirmedTargets.add(scan.target);
            }
        }

        // Pass two: use every candidate's own provider payload. This is essential for
        // series because episode_xc_id is provider-specific even between duplicate shows.
        for (Resolution resolution : resolutions) {
            JSONObject info = resolution.provider.optJSONObject("info");
            JSONObject movie = resolution.provider.optJSONObject("movie_data");
            ProviderIdentity identity = providerIdentity(info, movie);
            for (CategoryState state : states) for (TargetScan scan : state.scans) {
                Target target = scan.target;
                Candidate candidate = resolution.candidate;
                if (("movie".equals(target.type)) != resolution.movie
                        || !shortlisted(candidate, target)) continue;
                boolean stableMatch = !identity.conflict && TraktImportPolicy.sameStableId(
                        target.tmdb, target.imdb, identity.tmdb, identity.imdb);
                boolean siblingMatch = confirmedTargets.contains(target)
                        && TraktImportPolicy.confirmedSibling(candidate.title, candidate.year,
                        target.title, target.year);
                if (!stableMatch && !siblingMatch) continue;

                Match match = new Match();
                match.candidate = candidate;
                match.target = target;
                if (resolution.movie) {
                    match.providerDurationMs = durationMs(info, movie);
                } else {
                    JSONObject episodes = resolution.provider.optJSONObject("episodes");
                    match.providerEpisodeIds.addAll(providerEpisodeIds(episodes));
                    JSONObject episode = findEpisode(episodes,
                            target.season, target.episode);
                    match.episodeXcId = episode == null ? "" : episode.optString("id", "");
                    match.providerDurationMs = episode == null ? 0L
                            : durationMs(episode.optJSONObject("info"), episode);
                }
                scan.matches.add(match);
            }
        }
    }

    private static boolean shortlisted(Candidate candidate, Target target) {
        return "episode".equals(target.type)
                ? TraktImportPolicy.shortlistSeries(candidate.title, candidate.year,
                target.title, target.year)
                : TraktImportPolicy.shortlist(candidate.title, candidate.year,
                target.title, target.year);
    }

    private static TraktImportPolicy.ShortlistIndex shortlistIndex(List<Target> targets,
                                                                    boolean series) {
        List<String> titles = new ArrayList<>(targets.size());
        List<Integer> years = new ArrayList<>(targets.size());
        for (Target target : targets) {
            titles.add(target.title);
            years.add(target.year);
        }
        return TraktImportPolicy.shortlistIndex(titles, years, series);
    }

    private static List<Candidate> catalog(SQLiteDatabase database, String table,
                                           List<Target> targets, int candidateLimit) {
        List<Candidate> result = new ArrayList<>();
        if (targets.isEmpty()) return result;
        TraktImportPolicy.ShortlistIndex targetIndex = shortlistIndex(targets,
                "series".equals(table));
        Set<String> columns = columns(database, table);
        String titleColumn = columns.contains("name") ? "name" : (columns.contains("title") ? "title" : null);
        if (titleColumn == null || !columns.contains("id") || !columns.contains("playlist_id") || !columns.contains("xc_id")) return result;
        long afterId = Long.MIN_VALUE;
        while (true) {
            Cursor cursor = database.rawQuery("SELECT c.id,c.xc_id,c." + titleColumn + ",p.url FROM " + table
                    + " c JOIN playlists p ON p.id=c.playlist_id WHERE c.xc_id IS NOT NULL AND c.id>? "
                    + "ORDER BY c.id LIMIT " + CATALOG_PAGE_SIZE, new String[]{String.valueOf(afterId)});
            int rows = 0;
            try {
                while (cursor.moveToNext()) {
                    rows++;
                    afterId = cursor.getLong(0);
                    String xc = cursor.isNull(1) ? "" : cursor.getString(1);
                    String title = cursor.isNull(2) ? "" : cursor.getString(2);
                    String url = cursor.isNull(3) ? "" : cursor.getString(3);
                    if (!xc.matches("[0-9]+") || title.length() == 0 || url.length() == 0) continue;
                    int year = yearFromTitle(title);
                    if (!targetIndex.contains(title, year)) continue;
                    if (result.size() >= candidateLimit) {
                        throw new IllegalStateException("provider candidate limit exceeded");
                    }
                    Candidate candidate = new Candidate();
                    candidate.id = afterId;
                    candidate.xcId = xc;
                    candidate.title = title;
                    candidate.year = year;
                    candidate.playlistUrl = url;
                    candidate.catalogOrder = result.size();
                    result.add(candidate);
                }
            } finally { cursor.close(); }
            if (rows < CATALOG_PAGE_SIZE) return result;
        }
    }

    private static JSONObject providerInfo(Candidate candidate, boolean movie) throws Exception {
        String url = movie ? XtreamUrlBuilder.vodInfoUrl(candidate.playlistUrl, candidate.xcId)
                : XtreamUrlBuilder.seriesInfoUrl(candidate.playlistUrl, candidate.xcId);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(15_000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "TiviMate-Trakt-Patch/1.0");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("provider HTTP " + status);
            }
            return new JSONObject(readText(connection.getInputStream()));
        } finally { connection.disconnect(); }
    }

    private static int reconcileEpisodes(SQLiteDatabase database, List<Match> matches,
                                         Set<String> removedEpisodeIdentities) {
        Map<Long, SeriesReconciliation> bySeries = new LinkedHashMap<>();
        for (Match match : matches) {
            if (!"episode".equals(match.target.type)) continue;
            SeriesReconciliation state = bySeries.get(match.candidate.id);
            if (state == null) {
                state = new SeriesReconciliation(match.candidate.id);
                bySeries.put(match.candidate.id, state);
            }
            state.providerIds.addAll(match.providerEpisodeIds);
            if (!match.episodeXcId.matches("[0-9]+")) {
                state.complete = false;
            } else {
                state.keepIds.add(match.episodeXcId);
                if (state.preferredEpisodeId.isEmpty()) {
                    state.preferredEpisodeId = match.episodeXcId;
                }
            }
        }

        int changed = 0;
        for (SeriesReconciliation state : bySeries.values()) {
            if (!state.complete || state.providerIds.isEmpty() || state.keepIds.isEmpty()) continue;
            List<Long> staleRows = new ArrayList<>();
            List<String> staleEpisodeIds = new ArrayList<>();
            Cursor rows = database.rawQuery(
                    "SELECT id,episode_xc_id FROM episode_last_played_positions WHERE series_id=?",
                    new String[]{String.valueOf(state.seriesId)});
            try {
                while (rows.moveToNext()) {
                    String episodeId = rows.isNull(1) ? "" : rows.getString(1);
                    if (state.providerIds.contains(episodeId) && !state.keepIds.contains(episodeId)) {
                        staleRows.add(rows.getLong(0));
                        staleEpisodeIds.add(episodeId);
                    }
                }
            } finally { rows.close(); }
            for (int index = 0; index < staleRows.size(); index++) {
                if (database.delete("episode_last_played_positions", "id=?",
                        new String[]{String.valueOf(staleRows.get(index))}) > 0) {
                    changed++;
                    removedEpisodeIdentities.add(state.seriesId + ":" + staleEpisodeIds.get(index));
                }
            }
            changed += reconcileSeriesParent(database, state);
        }
        return changed;
    }

    private static int reconcileSeriesParent(SQLiteDatabase database,
                                             SeriesReconciliation state) {
        Cursor cursor = database.rawQuery(
                "SELECT last_turn_on_time,last_episode_xc_id FROM series WHERE id=? LIMIT 1",
                new String[]{String.valueOf(state.seriesId)});
        try {
            if (!cursor.moveToFirst()) return 0;
            long lastTurn = cursor.isNull(0) ? 0L : cursor.getLong(0);
            String currentEpisode = cursor.isNull(1) ? "" : cursor.getString(1);
            if (state.keepIds.contains(currentEpisode)) return 0;
            if (!currentEpisode.isEmpty() && !state.providerIds.contains(currentEpisode)) return 0;
            String preferredEpisodeId = state.preferredEpisodeId;
            ContentValues parent = new ContentValues();
            parent.put("last_episode_xc_id", preferredEpisodeId);
            if (lastTurn <= 0L) parent.put("last_turn_on_time", System.currentTimeMillis());
            return database.update("series", parent, "id=?",
                    new String[]{String.valueOf(state.seriesId)}) > 0 ? 1 : 0;
        } finally { cursor.close(); }
    }

    private static String updateMovie(SQLiteDatabase database, Candidate candidate, Target target,
                                      long providerDurationMs) {
        Cursor cursor = database.rawQuery("SELECT playlist_id,xc_id,last_played_position_ms,duration_ms,last_turn_on_time FROM movies WHERE id=? LIMIT 1",
                new String[]{String.valueOf(candidate.id)});
        try {
            if (!cursor.moveToFirst()) return null;
            String playlistId = cursor.isNull(0) ? "null" : cursor.getString(0);
            String xcId = cursor.isNull(1) ? "null" : cursor.getString(1);
            long localPosition = cursor.isNull(2) ? 0L : cursor.getLong(2);
            long localDuration = cursor.isNull(3) ? 0L : cursor.getLong(3);
            String localDurationValue = cursor.isNull(3) ? "null" : cursor.getString(3);
            long localLastTurn = cursor.isNull(4) ? 0L : cursor.getLong(4);
            if (!target.watched && localDuration <= 0L) return null;
            long duration = target.watched
                    ? TraktImportPolicy.selectWatchedDuration(localDuration,
                    providerDurationMs, target.traktDurationMs)
                    : (localDuration > 0 ? localDuration : providerDurationMs);
            long position = TraktImportPolicy.mergePosition(localPosition, duration, target.progress, target.watched);
            boolean missingHistory = position > 0L && localLastTurn <= 0L;
            if (position == localPosition && duration == localDuration && !missingHistory) {
                return null;
            }
            ContentValues values = new ContentValues();
            values.put("last_played_position_ms", position);
            if (duration > 0) values.put("duration_ms", duration);
            if (missingHistory) values.put("last_turn_on_time", System.currentTimeMillis());
            if (database.update("movies", values, "id=?",
                    new String[]{String.valueOf(candidate.id)}) <= 0) return null;
            return "id=" + candidate.id + "|playlist_id=" + playlistId + "|xc_id=" + xcId
                    + "|last_played_position_ms=" + position + "|duration_ms="
                    + (duration > 0 ? String.valueOf(duration) : localDurationValue);
        } finally { cursor.close(); }
    }

    private static String updateEpisode(SQLiteDatabase database, Candidate series, Target target,
                                        String episodeXcId, long providerDurationMs) {
        if (!episodeXcId.matches("[0-9]+")) return null;
        Cursor cursor = database.rawQuery("SELECT id,position_ms,duration_ms FROM episode_last_played_positions WHERE series_id=? AND episode_xc_id=? LIMIT 1",
                new String[]{String.valueOf(series.id), episodeXcId});
        try {
            boolean exists = cursor.moveToFirst();
            long localPosition = exists && !cursor.isNull(1) ? cursor.getLong(1) : 0L;
            long localDuration = exists && !cursor.isNull(2) ? cursor.getLong(2) : 0L;
            if (!target.watched && localDuration <= 0L) return null;
            long duration = target.watched
                    ? TraktImportPolicy.selectWatchedDuration(localDuration,
                    providerDurationMs, target.traktDurationMs)
                    : (localDuration > 0 ? localDuration : providerDurationMs);
            long position = TraktImportPolicy.mergePosition(localPosition, duration, target.progress, target.watched);
            if (duration <= 0) return null;
            if (exists && position == localPosition && duration == localDuration) {
                ensureSeriesHistory(database, series.id, episodeXcId);
                return null;
            }
            ContentValues values = new ContentValues();
            values.put("position_ms", position);
            values.put("duration_ms", duration);
            long rowId;
            if (exists) {
                rowId = cursor.getLong(0);
                if (database.update("episode_last_played_positions", values, "id=?",
                        new String[]{String.valueOf(rowId)}) <= 0) return null;
            } else {
                if (!safeEpisodeInsertSchema(database)) return null;
                values.put("series_id", series.id);
                values.put("episode_xc_id", episodeXcId);
                rowId = database.insert("episode_last_played_positions", null, values); // id deliberately omitted
                if (rowId < 0) return null;
            }
            ensureSeriesHistory(database, series.id, episodeXcId);
            return "id=" + rowId + "|series_id=" + series.id + "|episode_xc_id="
                    + episodeXcId + "|position_ms=" + position + "|duration_ms=" + duration;
        } finally { cursor.close(); }
    }

    private static boolean ensureSeriesHistory(SQLiteDatabase database, long seriesId,
                                               String episodeXcId) {
        Cursor cursor = database.rawQuery(
                "SELECT last_turn_on_time,last_episode_xc_id FROM series WHERE id=? LIMIT 1",
                new String[]{String.valueOf(seriesId)});
        try {
            if (!cursor.moveToFirst()) return false;
            long lastTurn = cursor.isNull(0) ? 0L : cursor.getLong(0);
            if (lastTurn > 0L) return false;
        } finally { cursor.close(); }
        ContentValues parent = new ContentValues();
        parent.put("last_turn_on_time", System.currentTimeMillis());
        parent.put("last_episode_xc_id", episodeXcId);
        return database.update("series", parent, "id=?",
                new String[]{String.valueOf(seriesId)}) > 0;
    }

    private static boolean safeEpisodeInsertSchema(SQLiteDatabase database) {
        Set<String> expected = new HashSet<>();
        expected.add("id"); expected.add("series_id"); expected.add("episode_xc_id"); expected.add("position_ms"); expected.add("duration_ms");
        Set<String> names = new HashSet<>();
        boolean validId = false;
        Cursor cursor = database.rawQuery("PRAGMA table_info('episode_last_played_positions')", null);
        try {
            int nameIndex = cursor.getColumnIndex("name");
            int typeIndex = cursor.getColumnIndex("type");
            int pkIndex = cursor.getColumnIndex("pk");
            if (nameIndex < 0 || typeIndex < 0 || pkIndex < 0) return false;
            while (cursor.moveToNext()) {
                String name = cursor.getString(nameIndex);
                String type = cursor.isNull(typeIndex) ? "" : cursor.getString(typeIndex);
                int primaryKeyOrder = cursor.getInt(pkIndex);
                names.add(name);
                if ("id".equals(name)) {
                    validId = "INTEGER".equalsIgnoreCase(type.trim()) && primaryKeyOrder == 1;
                } else if (primaryKeyOrder != 0) {
                    return false;
                }
            }
            return names.equals(expected) && validId;
        } finally { cursor.close(); }
    }

    private static Set<String> columns(SQLiteDatabase database, String table) {
        Cursor cursor = database.rawQuery("SELECT * FROM " + table + " LIMIT 0", null);
        try {
            Set<String> result = new HashSet<>();
            for (String name : cursor.getColumnNames()) result.add(name);
            return result;
        } catch (RuntimeException ignored) {
            return new HashSet<>();
        } finally { cursor.close(); }
    }

    private static Set<String> providerEpisodeIds(JSONObject seasons) {
        Set<String> result = new HashSet<>();
        if (seasons == null) return result;
        java.util.Iterator<String> keys = seasons.keys();
        while (keys.hasNext()) {
            JSONArray episodes = seasons.optJSONArray(keys.next());
            if (episodes == null) continue;
            for (int index = 0; index < episodes.length(); index++) {
                JSONObject episode = episodes.optJSONObject(index);
                String id = episode == null ? "" : episode.optString("id", "");
                if (id.matches("[0-9]+")) result.add(id);
            }
        }
        return result;
    }

    private static JSONObject findEpisode(JSONObject seasons, int wantedSeason, int wantedEpisode) {
        if (seasons == null) return null;
        JSONArray values = seasons.optJSONArray(String.valueOf(wantedSeason));
        if (values == null) return null;
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.optJSONObject(i);
            if (value != null && value.optInt("episode_num", value.optInt("episode", 0)) == wantedEpisode) return value;
        }
        return null;
    }

    private static ProviderIdentity providerIdentity(JSONObject info, JSONObject movie) {
        ProviderIdentity result = new ProviderIdentity();
        Set<String> tmdbValues = new HashSet<>();
        Set<String> imdbValues = new HashSet<>();
        for (JSONObject source : new JSONObject[]{info, movie}) {
            if (source == null) continue;
            for (String key : new String[]{"tmdb_id", "tmdb"}) {
                String value = TraktImportPolicy.stableTmdb(source.opt(key));
                if (!value.isEmpty()) tmdbValues.add(value);
            }
            for (String key : new String[]{"imdb_id", "imdb"}) {
                String value = TraktImportPolicy.stableImdb(source.opt(key));
                if (!value.isEmpty()) imdbValues.add(value);
            }
        }
        result.conflict = tmdbValues.size() > 1 || imdbValues.size() > 1;
        result.tmdb = tmdbValues.isEmpty() ? "" : tmdbValues.iterator().next();
        result.imdb = imdbValues.isEmpty() ? "" : imdbValues.iterator().next();
        return result;
    }

    private static long durationMs(JSONObject first, JSONObject second) {
        for (JSONObject value : new JSONObject[]{first, second}) {
            if (value == null) continue;
            long seconds = value.optLong("duration_secs", value.optLong("duration_seconds", 0L));
            if (seconds > 0L && seconds < 86400L) return seconds * 1000L;
            String duration = value.optString("duration", "");
            if (duration.matches("[0-9]+")) {
                long numeric = Long.parseLong(duration);
                if (numeric > 0L && numeric < 1440L) return numeric * 60_000L;
            }
            long clock = TraktImportPolicy.parseClockDurationMs(duration);
            if (clock > 0L) return clock;
        }
        return 0L;
    }

    private static int yearFromTitle(String title) {
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("(?:^|\\D)((?:19|20)[0-9]{2})(?:\\D*$)").matcher(title);
        return match.find() ? Integer.parseInt(match.group(1)) : 0;
    }

    private static String readText(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            char[] chunk = new char[8192];
            int length = 0;
            for (int count; (count = reader.read(chunk, 0, chunk.length)) != -1;) {
                if (length + count > MAX_RESPONSE_CHARS) throw new IllegalStateException("response too large");
                result.append(chunk, 0, count);
                length += count;
            }
        }
        return result.toString();
    }

    private static final class Target {
        String type, tmdb, imdb, title;
        int year, season, episode;
        boolean watched;
        double progress;
        long traktDurationMs;
        String key() { return type + ':' + (!tmdb.isEmpty() ? "tmdb:" + tmdb : "imdb:" + imdb) + ':' + season + ':' + episode; }
    }

    private static final class Match {
        Candidate candidate;
        Target target;
        String episodeXcId = "";
        final Set<String> providerEpisodeIds = new HashSet<>();
        long providerDurationMs;
    }

    private static final class SeriesReconciliation {
        final long seriesId;
        final Set<String> providerIds = new HashSet<>();
        final Set<String> keepIds = new HashSet<>();
        String preferredEpisodeId = "";
        boolean complete = true;

        SeriesReconciliation(long seriesId) { this.seriesId = seriesId; }
    }

    private static final class ProviderIdentity {
        String tmdb, imdb;
        boolean conflict;
    }

    private static final class TargetScan {
        final Target target;
        final List<Candidate> candidates;
        final List<Match> matches = new ArrayList<>();

        TargetScan(Target target, List<Candidate> movies, List<Candidate> series) {
            this.target = target;
            this.candidates = "movie".equals(target.type) ? movies : series;
        }
    }

    private static final class CategoryState {
        final List<TargetScan> scans = new ArrayList<>();

        CategoryState(List<Target> targets, List<Candidate> movies, List<Candidate> series) {
            for (Target target : targets) scans.add(new TargetScan(target, movies, series));
        }
    }

    private static final class ProviderTask {
        final Candidate candidate;
        final boolean movie;
        ProviderTask(Candidate candidate, boolean movie) {
            this.candidate = candidate;
            this.movie = movie;
        }
    }

    private static final class Resolution {
        final Candidate candidate;
        final JSONObject provider;
        final boolean movie;
        Resolution(Candidate candidate, boolean movie, JSONObject provider) {
            this.candidate = candidate;
            this.provider = provider;
            this.movie = movie;
        }
    }

    private static final class Candidate {
        long id;
        String xcId, title, playlistUrl;
        int year, catalogOrder;
    }
}
