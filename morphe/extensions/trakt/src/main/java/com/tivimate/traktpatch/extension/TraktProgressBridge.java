package com.tivimate.traktpatch.extension;

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

/**
 * Opt-in runtime bridge for discovering TiviMate's persisted playback model.
 * It never performs network I/O: the database hook only captures committed
 * state changes so movie/episode identity and position units can be proven
 * before production Trakt writes are enabled.
 */
public final class TraktProgressBridge {
    private static final String TAG = "TiviMateTraktDb";
    private static final String DATABASE_NAME = "TvPlayer.db";
    private static final String[] TABLES = {
            "last_played_positions",
            "episode_last_played_positions"
    };
    private static final String[] SCHEMA_ONLY_TABLES = {
            "movies",
            "series",
            "channels"
    };
    private static final int MAX_LOGGED_CHANGES = 20;
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();
    private static final Map<String, List<String>> SNAPSHOTS = new HashMap<>();
    private static final Set<String> SCHEMAS_LOGGED = new HashSet<>();

    private TraktProgressBridge() { }

    public static void onTransactionEnded(SQLiteDatabase database) {
        if (database == null || Boolean.TRUE.equals(ACTIVE.get())) return;
        String path = database.getPath();
        if (path == null || !(path.equals(DATABASE_NAME) || path.endsWith("/" + DATABASE_NAME))) return;
        // Room can nest transactions. Query only after the outer commit boundary.
        if (database.inTransaction()) return;

        ACTIVE.set(Boolean.TRUE);
        try {
            synchronized (SNAPSHOTS) {
                for (String table : SCHEMA_ONLY_TABLES) logSchema(database, table);
                for (String table : TABLES) capture(database, table);
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "capture failed: " + error.getClass().getSimpleName());
        } finally {
            ACTIVE.remove();
        }
    }

    private static void capture(SQLiteDatabase database, String table) {
        logSchema(database, table);

        List<String> current = readRows(database, table);
        List<String> previous = SNAPSHOTS.put(table, current);
        if (previous == null) {
            Log.i(TAG, "baseline " + table + " rows=" + current.size());
            return;
        }
        if (previous.equals(current)) return;

        Set<String> before = new HashSet<>(previous);
        Set<String> after = new HashSet<>(current);
        List<String> removed = new ArrayList<>(before);
        removed.removeAll(after);
        List<String> added = new ArrayList<>(after);
        added.removeAll(before);
        Collections.sort(removed);
        Collections.sort(added);

        Log.i(TAG, "changed " + table + " rows=" + previous.size() + "->" + current.size()
                + " removed=" + removed.size() + " added=" + added.size());
        logChanges(table, "removed", removed);
        logChanges(table, "added", added);
    }

    private static void logSchema(SQLiteDatabase database, String table) {
        if (SCHEMAS_LOGGED.add(table)) {
            Log.i(TAG, "schema " + table + ": " + readSchema(database, table));
        }
    }

    private static String readSchema(SQLiteDatabase database, String table) {
        Cursor cursor = database.rawQuery("PRAGMA table_info(`" + table + "`)", null);
        try {
            List<String> columns = new ArrayList<>();
            int name = cursor.getColumnIndex("name");
            int type = cursor.getColumnIndex("type");
            int primaryKey = cursor.getColumnIndex("pk");
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(name) + ":" + cursor.getString(type)
                        + (cursor.getInt(primaryKey) != 0 ? ":pk" : ""));
            }
            return columns.toString();
        } finally {
            cursor.close();
        }
    }

    private static List<String> readRows(SQLiteDatabase database, String table) {
        Cursor cursor = database.rawQuery("SELECT * FROM `" + table + "`", null);
        try {
            String[] columns = cursor.getColumnNames();
            List<String> rows = new ArrayList<>();
            while (cursor.moveToNext()) {
                StringBuilder row = new StringBuilder();
                for (int index = 0; index < columns.length; index++) {
                    if (index > 0) row.append('|');
                    row.append(columns[index]).append('=');
                    int type = cursor.getType(index);
                    if (type == Cursor.FIELD_TYPE_NULL) {
                        row.append("null");
                    } else if (type == Cursor.FIELD_TYPE_BLOB) {
                        byte[] value = cursor.getBlob(index);
                        row.append("<blob:").append(value == null ? 0 : value.length).append('>');
                    } else {
                        row.append(sanitize(cursor.getString(index)));
                    }
                }
                rows.add(row.toString());
            }
            Collections.sort(rows);
            return rows;
        } finally {
            cursor.close();
        }
    }

    private static String sanitize(String value) {
        if (value == null) return "null";
        String cleaned = value.replace('\n', ' ').replace('\r', ' ');
        return cleaned.length() <= 160 ? cleaned : cleaned.substring(0, 160) + "…";
    }

    private static void logChanges(String table, String kind, List<String> rows) {
        int count = Math.min(rows.size(), MAX_LOGGED_CHANGES);
        for (int index = 0; index < count; index++) {
            Log.i(TAG, table + " " + kind + " " + rows.get(index));
        }
        if (rows.size() > count) {
            Log.i(TAG, table + " " + kind + " <" + (rows.size() - count) + " more>");
        }
    }
}
