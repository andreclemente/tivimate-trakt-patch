// Dump app SQLite schemas from inside the app process with Frida.
// Usage: frida -U -f ar.tvplayer.tv -l tools/frida/dump-sqlite-schema.js --no-pause
Java.perform(function () {
  var File = Java.use('java.io.File');
  var SQLiteDatabase = Java.use('android.database.sqlite.SQLiteDatabase');
  var StringCls = Java.use('java.lang.String');
  var pkg = 'ar.tvplayer.tv';

  function cstr(s) { return StringCls.$new(s); }
  function dumpCursor(cur) {
    var cols = [];
    for (var i = 0; i < cur.getColumnCount(); i++) cols.push(String(cur.getColumnName(i)));
    var rows = [];
    while (cur.moveToNext()) {
      var o = {};
      for (var j = 0; j < cols.length; j++) {
        try { o[cols[j]] = String(cur.getString(j)); } catch (e) { o[cols[j]] = null; }
      }
      rows.push(o);
    }
    return rows;
  }
  function runQuery(db, sql) {
    var cur = db.rawQuery(cstr(sql), null);
    try { return dumpCursor(cur); } finally { cur.close(); }
  }
  function dumpDb(path) {
    try {
      var db = SQLiteDatabase.openDatabase(cstr(path), null, 1); // OPEN_READONLY
      try {
        var tables = runQuery(db, "SELECT name, type, sql FROM sqlite_master WHERE type IN ('table','view','index','trigger') ORDER BY type, name");
        console.log('@@DB_SCHEMA@@ ' + JSON.stringify({path: path, objects: tables}));
        tables.forEach(function (t) {
          if (t.type === 'table' && t.name.indexOf('sqlite_') !== 0 && t.name !== 'android_metadata') {
            try {
              var info = runQuery(db, 'PRAGMA table_info(`' + t.name.replace(/`/g, '``') + '`)');
              console.log('@@DB_TABLE_INFO@@ ' + JSON.stringify({path: path, table: t.name, columns: info}));
            } catch (e) {
              console.log('@@DB_TABLE_INFO_ERROR@@ ' + JSON.stringify({path: path, table: t.name, error: String(e)}));
            }
          }
        });
      } finally { db.close(); }
    } catch (e) {
      console.log('@@DB_ERROR@@ ' + JSON.stringify({path: path, error: String(e)}));
    }
  }
  function dumpAll() {
    var dir = File.$new('/data/data/' + pkg + '/databases');
    var files = dir.listFiles();
    if (files === null) {
      console.log('@@DB_NO_DIR@@ /data/data/' + pkg + '/databases');
      return;
    }
    for (var i = 0; i < files.length; i++) {
      var p = String(files[i].getAbsolutePath());
      if (p.match(/-(wal|shm|journal)$/)) continue;
      dumpDb(p);
    }
  }
  setTimeout(dumpAll, 5000);
});
