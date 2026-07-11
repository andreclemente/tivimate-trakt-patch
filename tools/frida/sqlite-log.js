// Frida SQLite logger for research only. Avoids logging full stream URLs/tokens.
Java.perform(function () {
  function stack() {
    return Java.use('android.util.Log').getStackTraceString(Java.use('java.lang.Exception').$new()).split('\n').slice(0, 10).join('\n');
  }
  function log(kind, sql) {
    sql = String(sql || '');
    if (/token|password|username|auth|credential|url=/i.test(sql)) return;
    console.log(JSON.stringify({ts: Date.now(), kind: kind, sql: sql, stack: stack()}));
  }
  var SQLiteDatabase = Java.use('android.database.sqlite.SQLiteDatabase');
  SQLiteDatabase.execSQL.overload('java.lang.String').implementation = function (sql) {
    log('SQLiteDatabase.execSQL', sql);
    return this.execSQL(sql);
  };
  SQLiteDatabase.rawQuery.overload('java.lang.String', '[Ljava.lang.String;').implementation = function (sql, args) {
    log('SQLiteDatabase.rawQuery', sql);
    return this.rawQuery(sql, args);
  };
  SQLiteDatabase.insertWithOnConflict.implementation = function (table, nullColumnHack, values, conflictAlgorithm) {
    log('SQLiteDatabase.insertWithOnConflict', table);
    return this.insertWithOnConflict(table, nullColumnHack, values, conflictAlgorithm);
  };
  SQLiteDatabase.updateWithOnConflict.implementation = function (table, values, whereClause, whereArgs, conflictAlgorithm) {
    log('SQLiteDatabase.updateWithOnConflict', table + ' where ' + whereClause);
    return this.updateWithOnConflict(table, values, whereClause, whereArgs, conflictAlgorithm);
  };
  SQLiteDatabase.delete.implementation = function (table, whereClause, whereArgs) {
    log('SQLiteDatabase.delete', table + ' where ' + whereClause);
    return this.delete(table, whereClause, whereArgs);
  };
});
