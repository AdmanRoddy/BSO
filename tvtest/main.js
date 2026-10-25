// Chromium 56 (Electron 1.6) harness: same engine as a 2018 Samsung TV (Tizen 4.0).
// usage: electron harness URL SECONDS [probeFile]
var electron = require('electron');
var app = electron.app, BrowserWindow = electron.BrowserWindow;
var fs = require('fs');
if (!process.env.NO_PROXY_SWITCH) app.commandLine.appendSwitch('proxy-server', process.env.PROXY || 'http://127.0.0.1:37511');
app.commandLine.appendSwitch('proxy-bypass-list', '<local>;localhost;127.0.0.1');
app.commandLine.appendSwitch('ignore-gpu-blacklist');
var args = process.argv.slice(process.argv.findIndex(function (a) { return /(harness|tvtest|\.)$/.test(a); }) + 1);
var url = args[0], secs = +args[1] || 30, probe = args[2] ? fs.readFileSync(args[2], 'utf8') : null;
var UA = process.env.UA || 'Mozilla/5.0 (SMART-TV; LINUX; Tizen 4.0) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/2.1 Chrome/56.0.2924.0 TV Safari/537.36';
var t0 = Date.now();
function log(s) { process.stdout.write(((Date.now() - t0) / 1000).toFixed(1) + 's ' + s + '\n'); }
electron.ipcMain.on('clog', function (e, l, m) { if (process.env.QUIET && l !== 'error' && l !== 'ERROR' && !/^(ASK|TIZEN|\[BSO)/.test(m)) return; log('[' + l + '] ' + String(m).slice(0, 600)); });
app.on('ready', function () {
  var win = new BrowserWindow({ width: 1920, height: 1080, show: true, webPreferences: { nodeIntegration: false, backgroundThrottling: false, preload: require('path').join(__dirname, 'preload.js') } });
  var wc = win.webContents;
  wc.setUserAgent(UA);
  var lv = ['log', 'warn', 'ERROR', 'debug'];
  wc.on('console-message', function (e, level, msg, line, src) {
    if (process.env.QUIET && level < 2) return;
    log('[' + (lv[level] || level) + '] ' + String(msg).slice(0, 400) + (src ? '  (' + String(src).split('/').pop().slice(0, 60) + ':' + line + ')' : ''));
  });
  wc.on('did-fail-load', function (e, code, desc, u) { log('LOAD FAIL ' + code + ' ' + desc + ' ' + u); });
  wc.on('crashed', function () { log('RENDERER CRASHED'); });
  wc.on('did-finish-load', function () { log('loaded ' + wc.getURL()); });
  if (process.env.KEYS) {
    // KEYS="5:Enter,8:ArrowDown" -> send keys at given seconds
    process.env.KEYS.split(',').forEach(function (k) {
      var p = k.split(':');
      setTimeout(function () {
        log('key ' + p[1]);
        wc.sendInputEvent({ type: 'keyDown', keyCode: p[1] });
        wc.sendInputEvent({ type: 'keyUp', keyCode: p[1] });
      }, +p[0] * 1000);
    });
  }
  wc.loadURL(url);
  if (process.env.STEPS) {
    JSON.parse(fs.readFileSync(process.env.STEPS, 'utf8')).forEach(function (st) {
      setTimeout(function () {
        if (st.key) { log('key ' + st.key); wc.sendInputEvent({ type: 'keyDown', keyCode: st.key }); wc.sendInputEvent({ type: 'char', keyCode: st.key }); wc.sendInputEvent({ type: 'keyUp', keyCode: st.key }); }
        if (st.code) {   // a Samsung key code, pressed on the app page (goes through the app's own key handling)
          log('code ' + st.code);
          wc.executeJavaScript("(function(){var e=document.createEvent('Event');e.initEvent('keydown',true,true);Object.defineProperty(e,'keyCode',{value:" + st.code + "});document.dispatchEvent(e);})()");
        }
        if (st.js) wc.executeJavaScript(st.js, false, function (r) { log('JS ' + (st.label || '') + ' ' + JSON.stringify(r)); });
        if (st.ask) wc.executeJavaScript("window.bsoAsk(function(r){console.log('ASK " + (st.ask === true ? '' : st.ask) + " '+JSON.stringify(r))}); 1");
        if (st.shot) wc.capturePage(function (img) { fs.writeFileSync((process.env.SHOTDIR || '.') + '/' + st.shot + '.png', img.toPNG()); log('shot ' + st.shot); });
      }, st.t * 1000);
    });
  }
  var shots = (process.env.SHOTS || '').split(',').filter(Boolean);
  shots.forEach(function (s) {
    setTimeout(function () {
      wc.capturePage(function (img) { fs.writeFileSync(process.env.SHOTDIR + '/shot-' + s + 's.png', img.toPNG()); log('shot ' + s); });
    }, +s * 1000);
  });
  setTimeout(function () {
    if (!probe) { app.quit(); return; }
    wc.executeJavaScript(probe, false, function (r) { log('PROBE ' + JSON.stringify(r)); setTimeout(function () { app.quit(); }, 300); });
  }, secs * 1000);
});
