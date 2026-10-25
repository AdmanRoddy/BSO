// Runs before the page: passes its console and errors to the harness, and adds the stand-in TV when asked.
var ipc = require('electron').ipcRenderer;
['log', 'info', 'warn', 'error'].forEach(function (l) {
  var orig = console[l];
  console[l] = function () {
    try {
      ipc.send('clog', l, Array.prototype.map.call(arguments, function (a) {
        try { return typeof a === 'string' ? a : JSON.stringify(a); } catch (e) { return String(a); }
      }).join(' '));
    } catch (e) { /* harness gone */ }
    return orig.apply(console, arguments);
  };
});
window.addEventListener('error', function (e) { ipc.send('clog', 'ERROR', e.message + ' @ ' + e.filename + ':' + e.lineno); });
if (process.env.MOCK_TIZEN) require('./mock-tizen.js');
