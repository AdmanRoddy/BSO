/* Bent Steering TV helpers, shared by the home screen, BSO TV and J R-dio.
 *
 * - Fills in what the 2018 Samsung TV's web engine (Chromium 56) is missing.
 * - Turns Samsung remote key codes into standard key names (bsoKey).
 * - Knows when the page runs inside the Bent Steering Samsung app (?host=tizen) and talks to it
 *   (bsoTizen.call). The app shows these pages full screen in a frame and does what only it can:
 *   open other TV apps, read the USB stick, keep the screen saver off.
 * - Remote Back inside the Samsung app: close what's open, otherwise go back to the home screen.
 *
 * Written in old-style JavaScript on purpose so the 2018 engine can read it.
 */
(function () {
  'use strict';

  /* ---------- polyfills for Chromium 56 ---------- */
  if (!String.prototype.padStart) {
    String.prototype.padStart = function (n, s) {
      var str = String(this); s = s === undefined ? ' ' : String(s);
      if (str.length >= n || !s) return str;
      var fill = ''; while (fill.length < n - str.length) fill += s;
      return fill.slice(0, n - str.length) + str;
    };
  }
  if (!String.prototype.padEnd) {
    String.prototype.padEnd = function (n, s) {
      var str = String(this); s = s === undefined ? ' ' : String(s);
      if (str.length >= n || !s) return str;
      var fill = ''; while (fill.length < n - str.length) fill += s;
      return str + fill.slice(0, n - str.length);
    };
  }
  if (!Promise.prototype['finally']) {
    Promise.prototype['finally'] = function (f) {
      return this.then(function (v) { return Promise.resolve(f()).then(function () { return v; }); },
        function (e) { return Promise.resolve(f()).then(function () { throw e; }); });
    };
  }
  // scrollIntoView({block:'nearest'}) only arrived in Chromium 61; older engines treat the options as "align to top".
  window.bsoScrollTo = function (el) {
    if (!el) return;
    if ('scrollBehavior' in document.documentElement.style) el.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    else if (el.scrollIntoViewIfNeeded) el.scrollIntoViewIfNeeded(false);
    else el.scrollIntoView(false);
  };

  /* ---------- where are we running? ---------- */
  var host = null;
  if (/[?&]host=tizen\b/.test(location.search)) host = 'tizen';
  try {
    if (host) sessionStorage.setItem('bsoHost', host);
    else host = sessionStorage.getItem('bsoHost');
  } catch (e) { /* storage off: the address still says it */ }
  if (host === 'tizen' && window.parent === window) host = null;      // opened on its own, not in the app
  window.BSO_HOST = host;                                            // 'tizen' | null
  window.BSO_IN_APP = host === 'tizen' || /BSOTV/i.test(navigator.userAgent) || !!window.BSOApp;
  // the site this script came from (the live site, or a local copy when testing)
  var me = document.currentScript;
  window.BSO_SITE = me && /\/tv\.js(\?|$)/.test(me.src) ? me.src.replace(/tv\.js(\?.*)?$/, '') : 'https://admanroddy.github.io/BSO/';
  window.bsoHomeUrl = function () { return window.BSO_SITE + 'home/?host=tizen&nosplash=1'; };
  window.bsoWithHost = function (url) {
    if (host !== 'tizen') return url;
    return url + (url.indexOf('?') < 0 ? '?' : '&') + 'host=tizen';
  };

  /* ---------- Samsung remote keys ---------- */
  var CODES = {
    10009: 'GoBack', 10182: 'Exit', 427: 'ChannelUp', 428: 'ChannelDown',
    10252: 'MediaPlayPause', 415: 'MediaPlay', 19: 'MediaPause', 413: 'MediaStop',
    417: 'MediaFastForward', 412: 'MediaRewind', 10232: 'MediaTrackPrevious', 10233: 'MediaTrackNext',
    403: 'ColorF0Red', 404: 'ColorF1Green', 405: 'ColorF2Yellow', 406: 'ColorF3Blue',
    457: 'Info', 10073: 'ChannelList', 10135: 'Tools', 10221: 'Caption', 10140: 'Menu'
  };
  var NAMES = {
    XF86Back: 'GoBack', XF86Exit: 'Exit', XF86RaiseChannel: 'ChannelUp', XF86LowerChannel: 'ChannelDown',
    XF86PlayBack: 'MediaPlayPause', XF86AudioPlay: 'MediaPlay', XF86AudioPause: 'MediaPause', XF86AudioStop: 'MediaStop',
    XF86AudioForward: 'MediaFastForward', XF86AudioRewind: 'MediaRewind', XF86Info: 'Info',
    XF86Red: 'ColorF0Red', XF86Green: 'ColorF1Green', XF86Yellow: 'ColorF2Yellow', XF86Blue: 'ColorF3Blue',
    Left: 'ArrowLeft', Right: 'ArrowRight', Up: 'ArrowUp', Down: 'ArrowDown', Esc: 'Escape'
  };
  var ARROWS = { 37: 'ArrowLeft', 38: 'ArrowUp', 39: 'ArrowRight', 40: 'ArrowDown', 13: 'Enter', 8: 'Backspace', 27: 'Escape' };
  /** The key's standard name, whichever remote or keyboard it came from. */
  window.bsoKey = function (e) {
    var c = e.keyCode || 0, k = e.key;
    if (CODES[c]) return CODES[c];
    if (k && NAMES[k]) return NAMES[k];
    if (k && k !== 'Unidentified') return k;
    if (ARROWS[c]) return ARROWS[c];
    if (c >= 48 && c <= 57) return String(c - 48);
    return k || '';
  };

  /* ---------- talking to the Samsung app ---------- */
  var seq = 0, waiting = {};
  window.bsoTizen = {
    on: host === 'tizen',
    /** Ask the Samsung app to do something; resolves with its answer. */
    call: function (cmd, args, ms) {
      if (host !== 'tizen') return Promise.reject(new Error('not in the Samsung app'));
      return new Promise(function (resolve, reject) {
        var id = ++seq;
        waiting[id] = { resolve: resolve, reject: reject };
        window.parent.postMessage({ bso: 1, id: id, cmd: cmd, args: args || {} }, '*');
        setTimeout(function () {
          if (waiting[id]) { delete waiting[id]; reject(new Error(cmd + ': no answer')); }
        }, ms || 20000);
      });
    },
    /** Fire and forget. */
    tell: function (cmd, args) {
      if (host === 'tizen') window.parent.postMessage({ bso: 1, id: 0, cmd: cmd, args: args || {} }, '*');
    }
  };
  window.addEventListener('message', function (ev) {
    if (host !== 'tizen' || ev.source !== window.parent) return;
    var d = ev.data;
    if (!d || !d.bso) return;
    if (d.reply && waiting[d.reply]) {
      var w = waiting[d.reply]; delete waiting[d.reply];
      if (d.error) w.reject(new Error(d.error)); else w.resolve(d.result);
    } else if (d.key) {
      // the app passes on remote buttons that reached it instead of this page
      var e = document.createEvent('Event');
      e.initEvent('keydown', true, true);
      e.bsoName = d.key;
      document.dispatchEvent(e);
    } else if (d.probe) {
      var res = null;
      try { res = window.bsoProbe ? window.bsoProbe() : { page: location.pathname }; } catch (x) { res = { error: x.message }; }
      window.parent.postMessage({ bso: 1, probeReply: d.probe, result: res }, '*');
    } else if (d.event === 'resume') {
      if (window.bsoResume) window.bsoResume();
    } else if (d.event === 'pause') {
      if (window.bsoPause) window.bsoPause();
    }
  });
  // a forwarded key carries its name on the event
  var plainKey = window.bsoKey;
  window.bsoKey = function (e) { return e.bsoName || plainKey(e); };

  /* ---------- Back inside the Samsung app ---------- */
  if (host === 'tizen') {
    window.addEventListener('keydown', function (e) {
      var k = window.bsoKey(e);
      if (k !== 'GoBack') return;
      e.preventDefault(); if (e.stopImmediatePropagation) e.stopImmediatePropagation();
      if (window.bsoBack && window.bsoBack()) return;          // the page closed something
      if (window.BSO_IS_HOME) return;                         // already home
      location.replace(window.bsoHomeUrl());
    }, true);
    window.bsoTizen.tell('ready', { page: location.pathname });
    // page errors show up in the app's log (sdb dlog on the TV, the console in the test browser)
    window.addEventListener('error', function (e) {
      window.bsoTizen.tell('log', { msg: 'error ' + e.message + ' @ ' + String(e.filename || '').split('/').pop() + ':' + e.lineno });
    });
    window.addEventListener('unhandledrejection', function (e) {
      var r = e.reason; window.bsoTizen.tell('log', { msg: 'unhandled ' + (r && r.message ? r.message : String(r)) });
    });
  }
})();
