// A stand-in for the Samsung TV's tizen.* functions, so the Bent Steering app can run in the
// Chromium 56 test browser. Records every call as console lines starting "TIZEN ".
(function () {
  var fs = require('fs'), path = require('path');
  var USB_DIR = process.env.MOCK_USB || '';          // a real folder that plays the USB stick
  function note(s) { console.log('TIZEN ' + s); }
  var APPS = [
    { id: '3201907018807', name: 'Netflix', show: true, packageId: '3201907018807', iconPath: '' },
    { id: '111299001912', name: 'YouTube', show: true, packageId: '111299001912', iconPath: '' },
    { id: '3201910019365', name: 'Prime Video', show: true, packageId: '3201910019365', iconPath: '' },
    { id: '3201808016802', name: 'Pluto TV', show: true, packageId: '3201808016802', iconPath: '' },
    { id: 'org.tizen.browser', name: 'Internet', show: true, packageId: 'org.tizen.browser', iconPath: '' },
    { id: 'org.volt.apps', name: 'Apps', show: true, packageId: 'org.volt.apps', iconPath: '' },
    { id: 'org.tizen.hidden-service', name: 'Hidden', show: false, packageId: 'x', iconPath: '' },
    { id: 'BSOffroad1.BentSteering', name: 'Bent Steering', show: true, packageId: 'BSOffroad1', iconPath: '' }
  ];
  function later(f) { setTimeout(f, 5); }
  function err(name, msg) { var e = new Error(msg || name); e.name = name; return e; }

  // ---- files: an in-memory "wgt-private" plus the USB folder read from disk ----
  var priv = { dir: true, kids: {} };       // name -> node {dir, kids} | {data: Buffer}
  function mkFile(vpath, node, real) {
    var name = vpath.split('/').pop();
    var f = {
      name: name, fullPath: vpath, isDirectory: !!(node ? node.dir : fs.statSync(real).isDirectory()),
      get fileSize() { return node ? (node.data ? node.data.length : 0) : (fs.statSync(real).isDirectory() ? 0 : fs.statSync(real).size); },
      listFiles: function (ok, bad) {
        later(function () {
          try {
            if (node) ok(Object.keys(node.kids).map(function (k) { return mkFile(vpath + '/' + k, node.kids[k]); }));
            else ok(fs.readdirSync(real).map(function (k) { return mkFile(vpath + '/' + k, null, path.join(real, k)); }));
          } catch (e) { bad && bad(e); }
        });
      },
      resolve: function (rel) {
        if (node) { var n = node.kids[rel]; if (!n) throw err('NotFoundError', rel); return mkFile(vpath + '/' + rel, n); }
        var r = path.join(real, rel); if (!fs.existsSync(r)) throw err('NotFoundError', rel); return mkFile(vpath + '/' + rel, null, r);
      },
      createDirectory: function (rel) { if (!node) throw err('IOError', 'read-only'); node.kids[rel] = node.kids[rel] || { dir: true, kids: {} }; return mkFile(vpath + '/' + rel, node.kids[rel]); },
      createFile: function (rel) { if (!node) throw err('IOError', 'read-only'); if (node.kids[rel]) throw err('IOError', 'exists'); node.kids[rel] = { data: Buffer.alloc(0) }; return mkFile(vpath + '/' + rel, node.kids[rel]); },
      deleteFile: function (p, ok) { var nm = p.split('/').pop(); delete node.kids[nm]; later(function () { ok && ok(); }); },
      deleteDirectory: function (p, rec, ok) { var nm = p.split('/').pop(); delete node.kids[nm]; later(function () { ok && ok(); }); },
      openStream: function (mode, ok, bad) {
        later(function () {
          var buf = node ? node.data : fs.readFileSync(real), pos = 0;
          ok({
            readBase64: function (n) { var b = buf.slice(pos, pos + n); pos += b.length; return b.toString('base64'); },
            writeBase64: function (s) { node.data = Buffer.concat([node.data, Buffer.from(s, 'base64')]); },
            close: function () {}
          });
        });
      }
    };
    return f;
  }
  function resolvePath(loc) {
    var parts = loc.split('/'), root = parts.shift();
    if (root === 'wgt-private') {
      var n = priv;
      for (var i = 0; i < parts.length; i++) { n = n.kids[parts[i]]; if (!n) throw err('NotFoundError', loc); }
      return mkFile(loc, n);
    }
    if (root === 'wgt-package' && process.env.MOCK_PKG) {
      var rp = path.join.apply(null, [process.env.MOCK_PKG].concat(parts));
      if (!fs.existsSync(rp)) throw err('NotFoundError', loc);
      return mkFile(loc, null, rp);
    }
    if (root === 'removable_sda1' && USB_DIR) {
      var r = path.join.apply(null, [USB_DIR].concat(parts));
      if (!fs.existsSync(r)) throw err('NotFoundError', loc);
      return mkFile(loc, null, r);
    }
    throw err('NotFoundError', loc);
  }

  window.tizen = {
    application: {
      getCurrentApplication: function () {
        return { appInfo: { id: 'BSOffroad1.BentSteering', version: '1.0.0' }, hide: function () { note('hide'); }, exit: function () { note('exit'); } };
      },
      getAppsInfo: function (ok) { note('getAppsInfo'); later(function () { ok(APPS.slice()); }); },
      launch: function (id, ok, bad) {
        var hit = APPS.some(function (a) { return a.id === id; });
        note('launch ' + id + (hit ? ' OK' : ' (not installed)'));
        later(function () { if (hit) ok && ok(); else bad && bad(err('NotFoundError', id)); });
      },
      launchAppControl: function (ctl, id, ok) { note('launchAppControl ' + id + ' ' + JSON.stringify(ctl.data)); later(function () { ok && ok(); }); }
    },
    ApplicationControl: function (op, uri, mime, cat, data) { this.operation = op; this.data = (data || []).map(function (d) { return d.key + '=' + d.value; }); },
    ApplicationControlData: function (key, value) { this.key = key; this.value = value; },
    tvinputdevice: {
      getSupportedKeys: function () { return ['MediaPlayPause', 'MediaPlay', 'MediaPause', 'ChannelUp', 'ChannelDown', 'ColorF0Red', 'ColorF1Green', 'ColorF2Yellow', 'ColorF3Blue', 'Info', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'].map(function (n) { return { name: n }; }); },
      registerKey: function (n) { note('registerKey ' + n); }
    },
    filesystem: {
      listStorages: function (ok) {
        later(function () {
          ok([{ label: 'internal0', type: 'INTERNAL', state: 'MOUNTED' }].concat(USB_DIR ? [{ label: 'removable_sda1', type: 'EXTERNAL', state: 'MOUNTED' }] : []));
        });
      },
      resolve: function (loc, ok, bad) { later(function () { try { ok(resolvePath(loc)); } catch (e) { bad && bad(e); } }); }
    }
  };
  window.webapis = { appcommon: { AppCommonScreenSaverState: { SCREEN_SAVER_OFF: 0, SCREEN_SAVER_ON: 1 }, setScreenSaver: function (v) { note('screensaver ' + (v ? 'ON' : 'OFF')); } } };
  note('mock ready (usb ' + (USB_DIR || 'none') + ')');
})();
