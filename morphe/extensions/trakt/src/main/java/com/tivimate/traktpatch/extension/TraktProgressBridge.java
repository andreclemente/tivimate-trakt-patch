package com.tivimate.traktpatch.extension;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Runtime bridge from committed playback state to asynchronous metadata
 * resolution and Trakt scrobbling. Transaction hook only schedules work.
 */
public final class TraktProgressBridge {
    private static final String TAG = "TiviMateTraktDb";
    private static final String DATABASE_NAME = "TvPlayer.db";
    private static final String[] TABLES = {"movies", "episode_last_played_positions"};
    private static final ExecutorService CAPTURE = Executors.newSingleThreadExecutor();
    private static final Map<String, List<String>> SNAPSHOTS = new HashMap<>();
    private static final Object SCHEDULE_LOCK = new Object();
    /** Serializes a committed import plus baseline merge against outbound reads. */
    private static final ReentrantLock CAPTURE_EPOCH = new ReentrantLock();
    private static final ThreadLocal<Boolean> IMPORT_WRITE = new ThreadLocal<>();
    private static SQLiteDatabase pendingDatabase;
    private static boolean pending;
    private static boolean running;

    private TraktProgressBridge() { }

    public static void initialize(Context context) {
        if (context == null) return;
        final String path = context.getDatabasePath(DATABASE_NAME).getAbsolutePath();
        CAPTURE.execute(new Runnable() {
            @Override public void run() {
                SQLiteDatabase database = null;
                try {
                    java.io.File file = new java.io.File(path);
                    CAPTURE_EPOCH.lock();
                    try {
                        synchronized (SNAPSHOTS) {
                            if (!file.isFile()) {
                                for (String table : TABLES) SNAPSHOTS.put(table, Collections.<String>emptyList());
                                return;
                            }
                            database = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY);
                            for (String table : TABLES) SNAPSHOTS.put(table, readRows(database, table));
                        }
                    } finally {
                        CAPTURE_EPOCH.unlock();
                    }
                } catch (RuntimeException error) {
                    Log.w(TAG, "startup baseline failed type=" + error.getClass().getSimpleName());
                } finally {
                    if (database != null) database.close();
                }
            }
        });
    }

    public static void onTransactionEnded(SQLiteDatabase database) {
        if (database == null) return;
        String path = database.getPath();
        if (path == null || !(path.equals(DATABASE_NAME) || path.endsWith("/" + DATABASE_NAME))) return;
        if (database.inTransaction()) return;
        // Suppress only the direct import transaction on this thread. Unrelated
        // transaction threads continue to enqueue outbound capture normally.
        if (Boolean.TRUE.equals(IMPORT_WRITE.get())) return;
        synchronized (SCHEDULE_LOCK) {
            pendingDatabase = database;
            pending = true;
            if (running) return;
            running = true;
        }
        CAPTURE.execute(new Runnable() {
            @Override public void run() { drainCaptures(); }
        });
    }

    /** Scopes hook suppression to the current direct import transaction. */
    public static void beginImportWrite() {
        CAPTURE_EPOCH.lock();
        IMPORT_WRITE.set(Boolean.TRUE);
    }

    /** Clears thread state on success, rollback, and begin/end failure paths. */
    public static void endImportWrite() {
        IMPORT_WRITE.remove();
        CAPTURE_EPOCH.unlock();
    }

    /**
     * Merges the exact rows produced by committed inbound writes. Never reread the
     * database here: a local transaction may have committed the same row between the
     * import commit and reconciliation, and that newer value must remain exportable.
     */
    public static void mergeExpectedRowsAfterImport(Set<String> movieRows,
                                                    Set<String> episodeRows,
                                                    Set<String> removedEpisodeIdentities) {
        try {
            synchronized (SNAPSHOTS) {
                mergeExpectedRows("movies", movieRows, Collections.<String>emptySet());
                mergeExpectedRows("episode_last_played_positions", episodeRows,
                        removedEpisodeIdentities);
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "import baseline merge failed type=" + error.getClass().getSimpleName());
        }
    }

    private static void mergeExpectedRows(String table, Set<String> expectedRows,
                                          Set<String> removedIdentities) {
        if ((expectedRows == null || expectedRows.isEmpty())
                && (removedIdentities == null || removedIdentities.isEmpty())) return;
        List<String> previous = SNAPSHOTS.get(table);
        List<String> merged = previous == null ? new ArrayList<String>() : new ArrayList<>(previous);
        Set<String> keys = new HashSet<>();
        if (removedIdentities != null) keys.addAll(removedIdentities);
        if (expectedRows != null) {
            for (String row : expectedRows) if (row != null) keys.add(rowIdentity(row, table));
        }
        for (int index = merged.size() - 1; index >= 0; index--) {
            if (keys.contains(rowIdentity(merged.get(index), table))) merged.remove(index);
        }
        if (expectedRows != null) {
            for (String row : expectedRows) if (row != null) merged.add(row);
        }
        Collections.sort(merged);
        SNAPSHOTS.put(table, merged);
    }

    private static String rowIdentity(String row, String table) {
        if ("movies".equals(table)) return rowValue(row, "id");
        return rowValue(row, "series_id") + ':' + rowValue(row, "episode_xc_id");
    }

    private static void drainCaptures() {
        while (true) {
            SQLiteDatabase database;
            synchronized (SCHEDULE_LOCK) {
                if (!pending) {
                    running = false;
                    return;
                }
                pending = false;
                database = pendingDatabase;
            }
            CAPTURE_EPOCH.lock();
            try {
                synchronized (SNAPSHOTS) {
                    for (String table : TABLES) capture(database, table);
                }
            } catch (RuntimeException error) {
                Log.w(TAG, "capture failed type=" + error.getClass().getSimpleName());
            } finally {
                CAPTURE_EPOCH.unlock();
            }
        }
    }

    private static void capture(SQLiteDatabase database, String table) {
        List<String> current = readRows(database, table);
        List<String> previous = SNAPSHOTS.put(table, current);
        if (previous == null || previous.equals(current)) return;

        Set<String> before = new HashSet<>(previous);
        Set<String> after = new HashSet<>(current);
        List<String> added = new ArrayList<>(after);
        added.removeAll(before);
        if ("movies".equals(table)) resolveMovieMetadata(database, added);
        if ("episode_last_played_positions".equals(table)) resolveEpisodeMetadata(database, added);
    }

    private static void resolveMovieMetadata(SQLiteDatabase database, List<String> added) {
        for (String row : added) {
            String playlistId = rowValue(row, "playlist_id");
            String xcId = rowValue(row, "xc_id");
            long positionMs = positiveLong(rowValue(row, "last_played_position_ms"));
            long durationMs = positiveLong(rowValue(row, "duration_ms"));
            if (positionMs <= 0L) continue;
            if (playlistId == null || xcId == null || "null".equals(xcId)) continue;
            Cursor cursor = database.rawQuery(
                    "SELECT url FROM playlists WHERE id = ? LIMIT 1", new String[]{playlistId});
            try {
                if (cursor.moveToFirst() && !cursor.isNull(0)) {
                    XtreamMetadataResolver.resolveAsync(cursor.getString(0), xcId, positionMs, durationMs);
                }
            } finally {
                cursor.close();
            }
        }
    }

    private static void resolveEpisodeMetadata(SQLiteDatabase database, List<String> added) {
        for (String row : added) {
            String seriesId = rowValue(row, "series_id");
            String episodeXcId = rowValue(row, "episode_xc_id");
            long positionMs = positiveLong(rowValue(row, "position_ms"));
            long durationMs = positiveLong(rowValue(row, "duration_ms"));
            if (positionMs <= 0L) continue;
            if (seriesId == null || episodeXcId == null || "null".equals(episodeXcId)) continue;
            Cursor cursor = database.rawQuery(
                    "SELECT s.xc_id, p.url FROM series s JOIN playlists p ON p.id = s.playlist_id "
                            + "WHERE s.id = ? LIMIT 1", new String[]{seriesId});
            try {
                if (cursor.moveToFirst() && !cursor.isNull(0) && !cursor.isNull(1)) {
                    XtreamMetadataResolver.resolveEpisodeAsync(cursor.getString(1), cursor.getString(0),
                            episodeXcId, positionMs, durationMs);
                }
            } finally {
                cursor.close();
            }
        }
    }

    private static long positiveLong(String value) {
        if (value == null) return 0L;
        try {
            return Math.max(0L, Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String rowValue(String row, String wanted) {
        for (String field : row.split("\\|")) {
            int equals = field.indexOf('=');
            if (equals > 0 && wanted.equals(field.substring(0, equals))) {
                return field.substring(equals + 1);
            }
        }
        return null;
    }

    private static List<String> readRows(SQLiteDatabase database, String table) {
        String query = "movies".equals(table)
                ? "SELECT id, playlist_id, xc_id, last_played_position_ms, duration_ms "
                    + "FROM movies WHERE last_played_position_ms > 0"
                : "SELECT id, series_id, episode_xc_id, position_ms, duration_ms "
                    + "FROM episode_last_played_positions WHERE position_ms > 0";
        Cursor cursor = database.rawQuery(query, null);
        try {
            String[] columns = cursor.getColumnNames();
            List<String> rows = new ArrayList<>();
            while (cursor.moveToNext()) {
                StringBuilder row = new StringBuilder();
                for (int index = 0; index < columns.length; index++) {
                    if (index > 0) row.append('|');
                    row.append(columns[index]).append('=');
                    row.append(cursor.isNull(index) ? "null" : cursor.getString(index));
                }
                rows.add(row.toString());
            }
            Collections.sort(rows);
            return rows;
        } finally {
            cursor.close();
        }
    }
}
