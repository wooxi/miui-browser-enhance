(function () {
  'use strict';
  if (window.__hermesPopupGuard) return;
  window.__hermesPopupGuard = true;

  var AD_HOSTS = [
    'myavlive.com', 'mayzaent.com', 'miyazent.com', 'tsyndicate.com',
    'snaptrckr.fun', 'mavrtracktor.com', 'propellerads.com', 'juicyads.com',
    'exoclick.com', 'exosrv.com', 'eas4et.me', 'pl2205.com', 'h64bcvh.com',
    'popads.net', 'popcash.net', 'adsterra.com', 'hilltopads.net', 'clickadu.com'
  ];
  var WRAP_PATHS = ['/pop', '/popup', '/redirect', '/out', '/go', '/jump', '/link', '/open'];

  function dbg(m) {
    try { console.log('[hermes-guard] ' + m); } catch (e) {}
  }

  function isAdUrl(abs) {
    try {
      var u = new URL(abs, location.href);
      var p = u.pathname.toLowerCase();
      for (var i = 0; i < WRAP_PATHS.length; i++) {
        if (p === WRAP_PATHS[i] || p.indexOf(WRAP_PATHS[i] + '/') === 0) return true;
      }
      var h = u.hostname;
      for (var j = 0; j < AD_HOSTS.length; j++) {
        if (h.indexOf(AD_HOSTS[j]) !== -1) return true;
      }
      return false;
    } catch (e) { return true; }
  }

  var fake = {
    closed: false, opener: null, name: '_blank', parent: null, top: null,
    location: { href: 'about:blank', replace: function () {}, assign: function () {} },
    document: { write: function () {}, writeln: function () {}, close: function () {} },
    close: function () {}, focus: function () {}, blur: function () {}, print: function () {},
    moveTo: function () {}, moveBy: function () {}, resizeTo: function () {}, resizeBy: function () {},
    scrollBy: function () {}, scrollTo: function () {},
    postMessage: function () {},
    addEventListener: function () {}, removeEventListener: function () {},
    dispatchEvent: function () { return true; }
  };
  fake.parent = fake;
  fake.top = fake;

  var origOpen = window.open;
  window.open = function (u, n, f) {
    var abs = String(u || '');
    try { abs = new URL(abs, location.href).href; } catch (e) {}
    if (isAdUrl(abs)) {
      dbg('blocked -> ' + abs);
      return fake;
    }
    return origOpen.apply(window, arguments);
  };

  document.addEventListener('click', function (e) {
    if (e.defaultPrevented) return;
    var a = e.target;
    while (a && a.tagName !== 'A') a = a.parentElement;
    if (!a || !a.target || a.target === '_self') return;
    if (isAdUrl(a.href || '')) {
      dbg('click cancel -> ' + a.href);
      e.preventDefault();
      e.stopPropagation();
    }
  }, true);

  document.addEventListener('submit', function (e) {
    try {
      var f = e.target;
      if (f && f.target && f.target !== '_self' && isAdUrl(f.action || '')) {
        dbg('submit cancel -> ' + f.action);
        e.preventDefault();
        e.stopPropagation();
      }
    } catch (err) {}
  }, true);
})();
