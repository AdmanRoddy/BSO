/* Bent Steering for Samsung TVs.
 *
 * The app is a frame around the website: the home screen, BSO TV and J R-dio load from
 * admanroddy.github.io/BSO, so they update without reinstalling and YouTube sees a real web address.
 * This page does what only an installed TV app may do, when the website asks over postMessage:
 * open the TV's other apps, read pictures off a USB stick, keep pictures in the app's private storage,
 * catch the remote's media and colour buttons, and keep Samsung's screen saver off during shows.
 *
 * Old-style JavaScript on purpose: 2018 Samsung TVs run Chromium 56.
 */
(function () {
  'use strict';
  var override = /[?&]site=([^&]+)/.exec(location.search);           // only used when testing a local copy
  var SITE = override ? decodeURIComponent(override[1]) : 'https://admanroddy.github.io/BSO/';
  var ORIGIN = SITE.match(/^https?:\/\/[^\/]+/)[0];
  var HOME = SITE + 'home/?host=tizen';

  var frame = document.getElementById('f');
  var boot = document.getElementById('boot');
  var msg = document.getElementById('msg');
  var off = document.getElementById('off');
  var btns = document.getElementById('btns');

  // The key layout is also set from here, in case the TV ignores the stylesheet.
  function place(el, css) { for (var k in css) el.style[k] = css[k]; }
  place(document.documentElement, { width: '100%', height: '100%', margin: '0', background: '#05091A', overflow: 'hidden' });
  place(document.body, { width: '100%', height: '100%', margin: '0', background: '#05091A', overflow: 'hidden' });
  place(frame, { position: 'absolute', top: '0', left: '0', width: '100%', height: '100%', border: '0', background: '#05091A', visibility: 'hidden' });
  place(boot, { position: 'absolute', top: '0', left: '0', right: '0', bottom: '0', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', background: '#05091A' });
  try { place(boot.querySelector('img'), { width: '900px' }); } catch (e) { /* no logo */ }
  function showBoot(on) { boot.style.display = on ? 'flex' : 'none'; }

  var hasTizen = typeof window.tizen !== 'undefined';
  var me = null;
  try { me = hasTizen ? tizen.application.getCurrentApplication() : null; } catch (e) { me = null; }
  var MY_ID = me ? me.appInfo.id : 'BSOffroad1.BentSteering';
  var VERSION = me ? me.appInfo.version : '0.0.0';

  function log(t) { try { console.log('[BSO shell] ' + t); } catch (e) { /* no console */ } }

  /* ---------- remote buttons ---------- */
  var WANT = ['MediaPlayPause', 'MediaPlay', 'MediaPause', 'MediaStop', 'MediaFastForward', 'MediaRewind',
    'MediaTrackPrevious', 'MediaTrackNext', 'ChannelUp', 'ChannelDown', 'ChannelList', 'Info', 'Caption',
    'ColorF0Red', 'ColorF1Green', 'ColorF2Yellow', 'ColorF3Blue', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'];
  try {
    var sup = tizen.tvinputdevice.getSupportedKeys().map(function (k) { return k.name; });
    WANT.forEach(function (n) { if (sup.indexOf(n) >= 0) { try { tizen.tvinputdevice.registerKey(n); } catch (e) { /* taken */ } } });
  } catch (e) { log('keys: ' + e.message); }

  var CODES = { 10009: 'GoBack', 427: 'ChannelUp', 428: 'ChannelDown', 10252: 'MediaPlayPause', 415: 'MediaPlay', 19: 'MediaPause',
    413: 'MediaStop', 417: 'MediaFastForward', 412: 'MediaRewind', 10232: 'MediaTrackPrevious', 10233: 'MediaTrackNext',
    403: 'ColorF0Red', 404: 'ColorF1Green', 405: 'ColorF2Yellow', 406: 'ColorF3Blue', 457: 'Info', 10073: 'ChannelList', 10221: 'Caption',
    37: 'ArrowLeft', 38: 'ArrowUp', 39: 'ArrowRight', 40: 'ArrowDown', 13: 'Enter', 8: 'Backspace', 27: 'Escape' };
  function keyName(e) {
    if (CODES[e.keyCode]) return CODES[e.keyCode];
    if (e.key && e.key !== 'Unidentified') return e.key;
    if (e.keyCode >= 48 && e.keyCode <= 57) return String(e.keyCode - 48);
    return '';
  }

  /* ---------- the website in the frame ---------- */
  var ready = false, page = '', loadTimer = 0, retryTimer = 0;
  function load(url) {
    ready = false;
    clearTimeout(loadTimer);
    frame.src = url;
    loadTimer = setTimeout(function () { if (!ready) offline(); }, 30000);
  }
  function focusFrame() {
    try { frame.focus(); } catch (e) { /* nothing to focus */ }
    try { frame.contentWindow.focus(); } catch (e) { /* cross-origin focus refused */ }
  }
  function onReady(p) {
    var first = !ready;
    ready = true; page = p || '';
    clearTimeout(loadTimer); clearTimeout(retryTimer);
    boot.className = 'gone'; off.className = ''; frame.className = 'on'; showBoot(false); frame.style.visibility = 'visible';
    focusFrame();
    screenSaver(/\/home\//.test(page));          // Samsung's screen saver stays off during shows and radio
    if (first) log('site ready: ' + page);
  }
  function offline() {
    ready = false;
    frame.className = ''; frame.style.visibility = 'hidden';
    boot.className = ''; showBoot(true);
    msg.textContent = 'No internet';
    off.className = 'on';
    renderOffline();
    clearTimeout(retryTimer);
    retryTimer = setTimeout(function () { msg.textContent = 'Trying again'; load(HOME); }, 20000);
  }
  window.addEventListener('online', function () { if (!ready) load(HOME); });

  /* ---------- requests from the website ---------- */
  function reply(id, result, error) {
    if (!id) return;
    try { frame.contentWindow.postMessage({ bso: 1, reply: id, result: result, error: error || null }, ORIGIN); }
    catch (e) { log('reply failed: ' + e.message); }
  }
  window.addEventListener('message', function (ev) {
    if (ev.origin !== ORIGIN || ev.source !== frame.contentWindow) return;
    var d = ev.data;
    if (!d || !d.bso || !d.cmd) return;
    if (d.cmd === 'ready') { onReady(d.args && d.args.page); return; }
    var fn = CMDS[d.cmd];
    if (!fn) { reply(d.id, null, 'unknown: ' + d.cmd); return; }
    Promise.resolve().then(function () { return fn(d.args || {}); })
      .then(function (r) { reply(d.id, r === undefined ? null : r); },
        function (e) { reply(d.id, null, String((e && (e.message || e.name)) || e)); });
  });

  /* ---------- buttons that land here instead of on the website ---------- */
  document.addEventListener('keydown', function (e) {
    var k = keyName(e);
    if (!ready) { offlineKey(k, e); return; }
    e.preventDefault();
    try { frame.contentWindow.postMessage({ bso: 1, key: k }, ORIGIN); } catch (x) { /* frame gone */ }
    focusFrame();
  });
  document.addEventListener('visibilitychange', function () {
    if (!ready) { if (!document.hidden) load(HOME); return; }
    try { frame.contentWindow.postMessage({ bso: 1, event: document.hidden ? 'pause' : 'resume' }, ORIGIN); } catch (e) { /* frame gone */ }
    if (!document.hidden) { focusFrame(); screenSaver(/\/home\//.test(page)); }
  });

  /* ---------- Samsung bits ---------- */
  function screenSaver(on) {
    try {
      var S = webapis.appcommon.AppCommonScreenSaverState;
      webapis.appcommon.setScreenSaver(on ? S.SCREEN_SAVER_ON : S.SCREEN_SAVER_OFF);
    } catch (e) { /* not on this TV */ }
  }
  var appInfo = {};       // id -> {name, icon}
  function listApps() {
    return new Promise(function (resolve, reject) {
      tizen.application.getAppsInfo(function (list) {
        var out = [];
        list.forEach(function (a) {
          if (!a.show || a.id === MY_ID) return;
          appInfo[a.id] = { name: a.name, icon: a.iconPath };
          out.push({ id: a.id, name: a.name, pkg: a.packageId });
        });
        resolve(out);
      }, reject);
    });
  }
  function launchOne(id) {
    return new Promise(function (resolve, reject) {
      tizen.application.launch(id, function () { resolve(id); }, reject);
    });
  }
  function launchAny(ids) {
    var p = Promise.reject(new Error('nothing to open'));
    (ids || []).filter(Boolean).forEach(function (id) { p = p['catch'](function () { return launchOne(id); }); });
    return p;
  }

  /* ---------- files: USB stick in, private storage kept ---------- */
  function fsResolve(loc, mode) {
    return new Promise(function (resolve, reject) { tizen.filesystem.resolve(loc, resolve, reject, mode || 'r'); });
  }
  function fsList(dir) { return new Promise(function (resolve, reject) { dir.listFiles(resolve, reject); }); }
  function mimeOf(n) {
    var m = /\.(\w+)$/.exec(n || ''); var x = m ? m[1].toLowerCase() : '';
    return { png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', webp: 'image/webp', gif: 'image/gif', bmp: 'image/bmp' }[x] || 'application/octet-stream';
  }
  function b64Bytes(b64) {
    var s = atob(b64), n = s.length, u = new Uint8Array(n);
    for (var i = 0; i < n; i++) u[i] = s.charCodeAt(i);
    return u;
  }
  function readBlob(file) {
    return new Promise(function (resolve, reject) {
      file.openStream('r', function (st) {
        try {
          var parts = [], left = file.fileSize, CH = 1572864;        // 1.5 MB at a time
          while (left > 0) {
            var n = Math.min(CH, left), b64 = st.readBase64(n);
            if (!b64) break;
            parts.push(b64Bytes(b64)); left -= n;
          }
          st.close();
          resolve(new Blob(parts, { type: mimeOf(file.name) }));
        } catch (e) { try { st.close(); } catch (x) { /* closed */ } reject(e); }
      }, reject);
    });
  }
  function blobB64(blob) {
    return new Promise(function (resolve, reject) {
      var r = new FileReader();
      r.onload = function () { var s = String(r.result); resolve(s.slice(s.indexOf(',') + 1)); };
      r.onerror = function () { reject(r.error || new Error('read')); };
      r.readAsDataURL(blob);
    });
  }
  function safeName(n) {
    n = String(n || '').replace(/[\/\\:*?"<>|]+/g, '_').replace(/^\.+/, '');
    if (!n) throw new Error('bad name');
    return n.slice(0, 120);
  }
  function kindDir(kind, create) {
    if (kind !== 'blueprints' && kind !== 'badges') return Promise.reject(new Error('bad kind'));
    return fsResolve('wgt-private', 'rw').then(function (root) {
      try { return root.resolve(kind); } catch (e) { return create ? root.createDirectory(kind) : null; }
    });
  }
  // pictures built into your copy of the app (pictures/<kind>/ in the package)
  function packDir(kind) {
    return fsResolve('wgt-package/pictures/' + kind, 'r').then(function (d) { return d; }, function () { return null; });
  }
  function names(dir) {
    if (!dir) return Promise.resolve([]);
    return fsList(dir).then(function (files) {
      return files.filter(function (f) { return !f.isDirectory && f.name.charAt(0) !== '.'; }).map(function (f) { return f.name; });
    }, function () { return []; });
  }
  function removeFile(dir, path) {
    return new Promise(function (resolve) { dir.deleteFile(path, resolve, function () { resolve(); }); });
  }
  var walkSkip = /^(System Volume Information|\$RECYCLE\.BIN|lost\+found)$/i;
  function walk(dir, depth, out, folder) {
    return fsList(dir).then(function (files) {
      var subs = [];
      files.forEach(function (f) {
        if (f.name.charAt(0) === '.') return;
        if (f.isDirectory) { if (depth < 4 && !walkSkip.test(f.name)) subs.push(f); return; }
        if (/\.(jpe?g|png|webp|gif|bmp)$/i.test(f.name) && f.fileSize > 0 && f.fileSize < 40e6 && out.length < 600) {
          out.push({ path: f.fullPath, name: f.name, size: f.fileSize, folder: folder });
        }
      });
      var p = Promise.resolve();
      subs.forEach(function (d) { p = p.then(function () { return walk(d, depth + 1, out, folder ? folder + '/' + d.name : d.name); }); });
      return p;
    });
  }

  var CMDS = {
    version: function () { return VERSION; },
    log: function (a) { log('page ' + page + ': ' + String(a.msg).slice(0, 300)); return true; },
    apps: function () { return listApps(); },
    launch: function (a) { return launchAny(a.ids); },
    store: function (a) {
      return new Promise(function (resolve) {
        try {
          var ctl = new tizen.ApplicationControl('http://tizen.org/appcontrol/operation/view', null, null, null,
            [new tizen.ApplicationControlData('Sub_Menu', ['detail']), new tizen.ApplicationControlData('widget_id', [a.id || ''])]);
          tizen.application.launchAppControl(ctl, 'org.volt.apps', function () { resolve(true); },
            function () { launchOne('org.volt.apps').then(function () { resolve(true); }, function () { resolve(false); }); });
        } catch (e) { resolve(false); }
      });
    },
    hide: function () { if (me) me.hide(); return true; },
    exit: function () { if (me) me.exit(); return true; },
    icon: function (a) {
      var info = appInfo[a.id];
      if (!info || !info.icon) return '';
      return new Promise(function (resolve) {
        var im = new Image(), done = false;
        var finish = function (v) { if (!done) { done = true; resolve(v); } };
        im.onload = function () {
          try {
            var w = Math.min(256, im.naturalWidth || 256), h = Math.round(w * (im.naturalHeight || w) / (im.naturalWidth || w));
            var c = document.createElement('canvas'); c.width = w; c.height = h;
            c.getContext('2d').drawImage(im, 0, 0, w, h);
            finish(c.toDataURL('image/png'));
          } catch (e) { finish(''); }
        };
        im.onerror = function () { finish(''); };
        setTimeout(function () { finish(''); }, 6000);
        im.src = /^file:/.test(info.icon) ? info.icon : 'file://' + info.icon;
      });
    },
    usbList: function () {
      return new Promise(function (resolve, reject) { tizen.filesystem.listStorages(resolve, reject); }).then(function (list) {
        var out = [], p = Promise.resolve();
        list.forEach(function (s) {
          if (s.type !== 'EXTERNAL' || s.state !== 'MOUNTED') return;
          p = p.then(function () { return fsResolve(s.label, 'r').then(function (root) { return walk(root, 0, out, ''); }, function () { /* unreadable */ }); });
        });
        return p.then(function () { return out; });
      });
    },
    readFile: function (a) {
      var path = String(a.path || '');
      if (!/^removable/.test(path) || path.indexOf('..') >= 0) return Promise.reject(new Error('not on a USB stick'));
      return fsResolve(path, 'r').then(readBlob);
    },
    saveFile: function (a) {
      var name = safeName(a.name);
      if (!(a.blob instanceof Blob)) return Promise.reject(new Error('no picture'));
      return Promise.all([kindDir(a.kind, true), blobB64(a.blob)]).then(function (r) {
        var dir = r[0], b64 = r[1], old = null;
        try { old = dir.resolve(name); } catch (e) { old = null; }
        return (old ? removeFile(dir, old.fullPath) : Promise.resolve()).then(function () {
          return new Promise(function (resolve, reject) {
            var f = dir.createFile(name);
            f.openStream('w', function (st) {
              try { st.writeBase64(b64); st.close(); resolve(true); } catch (e) { try { st.close(); } catch (x) { /* closed */ } reject(e); }
            }, reject);
          });
        });
      });
    },
    listSaved: function (a) {
      return Promise.all([kindDir(a.kind, false).then(names), packDir(a.kind).then(names)]).then(function (r) {
        var seen = {}, out = [];
        r[0].concat(r[1]).forEach(function (n) { if (!seen[n]) { seen[n] = 1; out.push(n); } });
        return out.sort();
      });
    },
    packCount: function (a) { return packDir(a.kind).then(names).then(function (n) { return n.length; }); },
    readSaved: function (a) {
      var name = safeName(a.name);
      return kindDir(a.kind, false).then(function (dir) {
        var f = null;
        try { f = dir ? dir.resolve(name) : null; } catch (e) { f = null; }
        if (f) return readBlob(f);
        return packDir(a.kind).then(function (pd) {
          if (!pd) throw new Error('not found');
          return readBlob(pd.resolve(name));
        });
      });
    },
    clearSaved: function (a) {
      if (a.kind !== 'blueprints' && a.kind !== 'badges') return Promise.reject(new Error('bad kind'));
      return fsResolve('wgt-private', 'rw').then(function (root) {
        var dir = null;
        try { dir = root.resolve(a.kind); } catch (e) { return true; }
        return new Promise(function (resolve, reject) { root.deleteDirectory(dir.fullPath, true, function () { resolve(true); }, reject); });
      });
    }
  };

  /* ---------- no internet: the TV's own apps still open from here ---------- */
  var OFF_APPS = [
    { name: 'Netflix', names: ['netflix'], ids: ['3201907018807', '11101200001'] },
    { name: 'YouTube', names: ['youtube'], ids: ['111299001912'] },
    { name: 'Prime Video', names: ['prime video', 'amazon prime video'], ids: ['3201910019365', '3201512006785'] }
  ];
  var offFocus = 0, offItems = [];
  function renderOffline() {
    offItems = [{ name: 'Try again', go: function () { msg.textContent = 'Trying again'; load(HOME); } }];
    OFF_APPS.forEach(function (o) {
      offItems.push({ name: o.name, go: function () {
        var ids = Object.keys(appInfo).filter(function (id) { return o.names.indexOf(String(appInfo[id].name).toLowerCase()) >= 0; }).concat(o.ids);
        launchAny(ids)['catch'](function () { /* not installed */ });
      } });
    });
    offItems.push({ name: 'Samsung TV', go: function () { if (me) me.hide(); } });
    btns.innerHTML = '';
    offItems.forEach(function (it, i) {
      var b = document.createElement('button');
      b.textContent = it.name;
      b.className = i === offFocus ? 'focus' : '';
      b.addEventListener('click', function () { offFocus = i; it.go(); });
      btns.appendChild(b);
    });
    if (hasTizen && !Object.keys(appInfo).length) listApps().then(function () { /* names known now */ }, function () { /* fine */ });
  }
  function offlineKey(k, e) {
    if (off.className !== 'on') return;
    if (k === 'ArrowLeft') offFocus = Math.max(0, offFocus - 1);
    else if (k === 'ArrowRight') offFocus = Math.min(offItems.length - 1, offFocus + 1);
    else if (k === 'Enter') { if (offItems[offFocus]) offItems[offFocus].go(); }
    else if (k === 'GoBack') { if (me) me.hide(); }
    else return;
    e.preventDefault();
    renderOffline();
  }

  // for the TV-engine test: ask the page in the frame how it's doing
  window.bsoAsk = function (cb) {
    var id = 'probe' + Date.now();
    var on = function (ev) {
      if (ev.origin !== ORIGIN || !ev.data || ev.data.probeReply !== id) return;
      window.removeEventListener('message', on);
      cb(ev.data.result);
    };
    window.addEventListener('message', on);
    try { frame.contentWindow.postMessage({ bso: 1, probe: id }, ORIGIN); } catch (e) { cb(null); }
  };

  log('Bent Steering ' + VERSION + (hasTizen ? '' : ' (no tizen API)'));
  load(HOME);
})();
