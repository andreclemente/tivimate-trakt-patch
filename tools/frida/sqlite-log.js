// Frida SQLite logger for research only.
// Usage: frida -U -f ar.tvplayer.tv -l tools/frida/sqlite-log.js --no-pause
// Avoids logging obvious full stream URLs/tokens/credentials.
Java.perform(function () {
  var SECRET_RE = /(password|passwd|token|authorization|bearer|username|credential|secret|xtream|m3u|url\s*=|http:\/\/|https:\/\/)/i;
  var TARGET_RE = /(last_played_positions|episode_last_played_positions|history_programs|movies|series|progress|position|watched|episode|movie)/i;

  function stack() {
    try {
      return Java.use('android.util.Log')
        .getStackTraceString(Java.use('java.lang.Exception').$new())
        .split('\n').slice(0, 14).join('\n');
    } catch (e) { return String(e); }
  }
  function sanitize(value) {
    var s = String(value || '');
    if (SECRET_RE.test(s)) return '<redacted-secret-or-url>';
    return s.length > 2000 ? s.substring(0, 2000) + '<truncated>' : s;
  }
  function valuesToObject(values) {
    try {
      if (values === null) return null;
      var out = {};
      var set = values.keySet();
      var it = set.iterator();
      while (it.hasNext()) {
        var k = String(it.next());
        out[k] = sanitize(values.get(k));
      }
      return out;
    } catch (e) { return { error: String(e) }; }
  }
  function log(kind, data) {
    data = data || {};
    data.ts = Date.now();
    data.kind = kind;
    console.log('@@SQLITE_EVENT@@ ' + JSON.stringify(data));
  }
  function shouldStack(sqlOrTable) {
    return TARGET_RE.test(String(sqlOrTable || ''));
  }

  var SQLiteDatabase = Java.use('android.database.sqlite.SQLiteDatabase');

  SQLiteDatabase.execSQL.overload('java.lang.String').implementation = function (sql) {
    var s = sanitize(sql);
    log('SQLiteDatabase.execSQL', { sql: s, stack: shouldStack(sql) ? stack() : null });
    return this.execSQL(sql);
  };

  SQLiteDatabase.execSQL.overload('java.lang.String', '[Ljava.lang.Object;').implementation = function (sql, bindArgs) {
    var s = sanitize(sql);
    log('SQLiteDatabase.execSQL.bindArgs', { sql: s, stack: shouldStack(sql) ? stack() : null });
    return this.execSQL(sql, bindArgs);
  };

  SQLiteDatabase.rawQuery.overload('java.lang.String', '[Ljava.lang.String;').implementation = function (sql, args) {
    var s = sanitize(sql);
    log('SQLiteDatabase.rawQuery', { sql: s, stack: shouldStack(sql) ? stack() : null });
    return this.rawQuery(sql, args);
  };

  SQLiteDatabase.insertWithOnConflict.implementation = function (table, nullColumnHack, values, conflictAlgorithm) {
    log('SQLiteDatabase.insertWithOnConflict', {
      table: sanitize(table),
      values: valuesToObject(values),
      conflictAlgorithm: conflictAlgorithm,
      stack: shouldStack(table) ? stack() : null
    });
    return this.insertWithOnConflict(table, nullColumnHack, values, conflictAlgorithm);
  };

  SQLiteDatabase.updateWithOnConflict.implementation = function (table, values, whereClause, whereArgs, conflictAlgorithm) {
    log('SQLiteDatabase.updateWithOnConflict', {
      table: sanitize(table),
      values: valuesToObject(values),
      whereClause: sanitize(whereClause),
      conflictAlgorithm: conflictAlgorithm,
      stack: shouldStack(table) || shouldStack(whereClause) ? stack() : null
    });
    return this.updateWithOnConflict(table, values, whereClause, whereArgs, conflictAlgorithm);
  };

  SQLiteDatabase.delete.implementation = function (table, whereClause, whereArgs) {
    log('SQLiteDatabase.delete', {
      table: sanitize(table),
      whereClause: sanitize(whereClause),
      stack: shouldStack(table) || shouldStack(whereClause) ? stack() : null
    });
    return this.delete(table, whereClause, whereArgs);
  };

  log('sqlite-log-ready', { targetPattern: String(TARGET_RE) });
});
