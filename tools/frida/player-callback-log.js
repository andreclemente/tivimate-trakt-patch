// Frida Media3/ExoPlayer callback discovery helper.
// Usage: frida -U -f ar.tvplayer.tv -l tools/frida/player-callback-log.js --no-pause
// Research only. Does not send network traffic.
Java.perform(function () {
  function now() { return Date.now(); }
  function safeString(v) {
    try { return v === null || v === undefined ? null : String(v); } catch (e) { return '<toString failed>'; }
  }
  function log(kind, data) {
    data = data || {};
    data.ts = now();
    data.kind = kind;
    console.log('@@PLAYER_EVENT@@ ' + JSON.stringify(data));
  }
  function stack() {
    try {
      return Java.use('android.util.Log')
        .getStackTraceString(Java.use('java.lang.Exception').$new())
        .split('\n').slice(0, 12).join('\n');
    } catch (e) { return String(e); }
  }
  function hookClassMethod(className, methodName) {
    try {
      var C = Java.use(className);
      if (!C[methodName]) return false;
      C[methodName].overloads.forEach(function (ov) {
        ov.implementation = function () {
          var args = [];
          for (var i = 0; i < arguments.length; i++) args.push(safeString(arguments[i]));
          log(className + '.' + methodName, { args: args, stack: stack() });
          return ov.apply(this, arguments);
        };
      });
      log('hooked', { className: className, methodName: methodName, overloads: C[methodName].overloads.length });
      return true;
    } catch (e) {
      log('hook-error', { className: className, methodName: methodName, error: String(e) });
      return false;
    }
  }

  [
    'androidx.media3.exoplayer.ExoPlayerImpl',
    'androidx.media3.exoplayer.ExoPlayerImplInternal',
    'androidx.media3.common.BasePlayer',
    'ar.tvplayer.tv.player.ui.CustomPlayerView'
  ].forEach(function (cls) {
    [
      'play', 'pause', 'stop', 'seekTo', 'release',
      'getCurrentPosition', 'getDuration', 'getPlaybackState',
      'setPlayWhenReady', 'onPlaybackStateChanged', 'onIsPlayingChanged'
    ].forEach(function (m) { hookClassMethod(cls, m); });
  });

  log('player-callback-log-ready', {});
});
