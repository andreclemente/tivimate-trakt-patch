// Frida method trace template.
// Edit TARGETS below after schema/player logs identify candidate classes/methods.
// Usage: frida -U -f ar.tvplayer.tv -l tools/frida/method-trace-template.js --no-pause
Java.perform(function () {
  var TARGETS = [
    // { className: 'fully.qualified.ClassName', methods: ['methodA', 'methodB'] }
  ];

  function safe(v) {
    try { return v === null || v === undefined ? null : String(v); } catch (e) { return '<toString failed>'; }
  }
  function stack() {
    try {
      return Java.use('android.util.Log')
        .getStackTraceString(Java.use('java.lang.Exception').$new())
        .split('\n').slice(0, 16).join('\n');
    } catch (e) { return String(e); }
  }
  function emit(kind, data) {
    data = data || {};
    data.ts = Date.now();
    data.kind = kind;
    console.log('@@METHOD_TRACE@@ ' + JSON.stringify(data));
  }
  function hook(target) {
    try {
      var C = Java.use(target.className);
      target.methods.forEach(function (methodName) {
        if (!C[methodName]) {
          emit('missing-method', { className: target.className, methodName: methodName });
          return;
        }
        C[methodName].overloads.forEach(function (ov) {
          ov.implementation = function () {
            var args = [];
            for (var i = 0; i < arguments.length; i++) args.push(safe(arguments[i]));
            emit('call', { className: target.className, methodName: methodName, args: args, stack: stack() });
            var ret = ov.apply(this, arguments);
            emit('return', { className: target.className, methodName: methodName, value: safe(ret) });
            return ret;
          };
        });
        emit('hooked-method', { className: target.className, methodName: methodName, overloads: C[methodName].overloads.length });
      });
    } catch (e) {
      emit('hook-error', { className: target.className, error: String(e) });
    }
  }

  TARGETS.forEach(hook);
  emit('method-trace-template-ready', { targetCount: TARGETS.length });
});
