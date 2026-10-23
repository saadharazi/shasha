'use strict';
/* واجهة شاشة على تلفزيونات سامسونج (Tizen) وإل جي (webOS): نفس تصميم design/index.html ونفس تصرف تطبيق أندرويد.
   كل شي يتحرك بالريموت: الأسهم تختار أقرب عنصر في الاتجاه، OK يشغّل، الرجوع يرجع. */
var $ = function (s, r) { return (r || document).querySelector(s); };
var $$ = function (s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); };
var UA = navigator.userAgent;
var TIZEN = /Tizen/i.test(UA) && typeof tizen !== 'undefined', WEBOS = /Web0S|webOS/i.test(UA);
var TV = /Tizen|Web0S|webOS|SMART-TV|NetCast/i.test(UA);

var PAL = [['#1d5a7a', '#071621', '#8fd3f0'], ['#8a5a26', '#1d1108', '#f3c98d'], ['#8a3550', '#1d0a12', '#f6b0c3'], ['#583080', '#130a1f', '#cfb0f5'],
  ['#8f3418', '#1d0905', '#ffab7a'], ['#24325e', '#070a16', '#b0c0f5'], ['#22604a', '#061510', '#a3e6c6'], ['#3a4a5c', '#0b0f15', '#c7d3e0']];
var ICON = {
  play: '<svg class="i" viewBox="0 0 24 24"><path class="play" d="M7 4.8v14.4L19 12z"/></svg>',
  pause: '<svg class="i" viewBox="0 0 24 24" style="stroke-width:3"><path d="M8.5 5v14M15.5 5v14"/></svg>',
  info: '<svg class="i" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7.5v.5"/></svg>',
  plus: '<svg class="i" viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></svg>',
  check: '<svg class="i ok" viewBox="0 0 24 24"><path d="m5 12 5 5L20 7"/></svg>',
  starF: '<svg viewBox="0 0 24 24"><path d="m12 3.5 2.6 5.3 5.9.9-4.25 4.1 1 5.8L12 16.9l-5.25 2.7 1-5.8L3.5 9.7l5.9-.9z"/></svg>',
  restart: '<svg class="i" viewBox="0 0 24 24"><path d="M4 12a8 8 0 1 0 2.4-5.7M4 4v4.5h4.5"/></svg>',
  list: '<svg class="i" viewBox="0 0 24 24"><path d="M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01"/></svg>',
  hd: '<svg class="i" viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="14" rx="3"/><path d="M7 9v6M11 9v6M7 12h4M14 9v6h1.5a3 3 0 0 0 0-6z"/></svg>',
  fit: '<svg class="i" viewBox="0 0 24 24"><path d="M4 9V5h4M20 9V5h-4M4 15v4h4M20 15v4h-4"/></svg>',
  back10: '<svg class="i" viewBox="0 0 24 24"><path d="M4.5 12a7.5 7.5 0 1 0 2.2-5.3M4.5 4v4h4"/><text x="12.6" y="15.2" font-size="7.2" text-anchor="middle" fill="currentColor" stroke="none" font-family="Tajawal" font-weight="700">10</text></svg>',
  fwd10: '<svg class="i" viewBox="0 0 24 24"><path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3M19.5 4v4h-4"/><text x="11.4" y="15.2" font-size="7.2" text-anchor="middle" fill="currentColor" stroke="none" font-family="Tajawal" font-weight="700">10</text></svg>',
  next: '<svg class="i" viewBox="0 0 24 24"><path d="M6 5.5v13L15.5 12z" fill="currentColor"/><path d="M18.5 5.5v13"/></svg>',
  star: '<svg class="i" viewBox="0 0 24 24"><path d="m12 3.5 2.6 5.3 5.9.9-4.25 4.1 1 5.8L12 16.9l-5.25 2.7 1-5.8L3.5 9.7l5.9-.9z"/></svg>',
  rotate: '<svg class="i" viewBox="0 0 24 24"><path d="M20 12a8 8 0 1 1-2.4-5.7M20 4v4.5h-4.5"/></svg>',
  gear: '<svg class="i" viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/><path d="M12 3v2.5M12 18.5V21M3 12h2.5M18.5 12H21M5.6 5.6l1.8 1.8M16.6 16.6l1.8 1.8M5.6 18.4l1.8-1.8M16.6 7.4l1.8-1.8"/></svg>',
};

/* ---------- مساعدات ---------- */
function esc(s) { return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }
function latin(s) { return /^[\x00-\x7F]+$/.test(s); }
function pad2(n) { return (n < 10 ? '0' : '') + n; }
function clock(sec) { sec = Math.max(0, Math.round(sec || 0)); var h = Math.floor(sec / 3600), m = Math.floor(sec / 60) % 60; return (h ? h + ':' + pad2(m) : m) + ':' + pad2(sec % 60); }
// العدد بالعربي: دقيقة، دقيقتين، 3 دقايق، 11 دقيقة
function count(n, one, two, few, many) { return n === 1 ? one : n === 2 ? two : n >= 3 && n <= 10 ? n + ' ' + few : n + ' ' + many; }
function mins(n) { return count(n, 'دقيقة', 'دقيقتين', 'دقايق', 'دقيقة'); }
function hm(t) { var d = new Date(t); return (d.getHours() % 12 || 12) + ':' + pad2(d.getMinutes()); }
// نفس String.hashCode في جافا، عشان الألوان تطلع مثل تطبيق أندرويد
function palette(s) { var h = 0; s = s || ''; for (var i = 0; i < s.length; i++) h = (31 * h + s.charCodeAt(i)) | 0; return PAL[(h & 0x7fffffff) % PAL.length]; }
function genres(s) { return String(s || '').split(/\s*[,\/،|]\s*/).filter(Boolean).slice(0, 2); }
// الصورة فوق البديل، ولو ما انفتحت تختفي ويبان البديل. hide: البديل اللي قبلها يختفي لما تنفتح
function img(src, hide) { return src ? '<img data-src="' + esc(src) + '" alt="" onerror="this.style.display=\'none\'"' + (hide ? ' onload="this.previousSibling.style.visibility=\'hidden\'"' : '') + '>' : ''; }
var REG = [];
function reg(it) { REG.push(it); return REG.length - 1; }
function itemOf(el) { return el && el.dataset.i != null ? REG[+el.dataset.i] : null; }
function loadImgs(root) { $$('img[data-src]', root).forEach(function (i) { i.src = i.getAttribute('data-src'); i.removeAttribute('data-src'); }); }
function toast(t) { var e = $('#toast'); e.textContent = t; e.classList.add('show'); clearTimeout(e._t); e._t = setTimeout(function () { e.classList.remove('show'); }, 2600); }

/* ---------- الصور وبدائلها ---------- */
function tp(it) {
  var p = palette(it.name);
  return '<div class="tp" style="--a:' + p[0] + ';--b:' + p[1] + ';--h:' + p[2] + '"><span class="y">' + esc(it.year || '') + '</span><span class="n"' + (latin(it.name) ? ' dir="ltr"' : '') + '>' + esc(it.name) + '</span></div>';
}
function frame(inner, cls) { return '<div class="frame"><div class="art ' + (cls || '') + '">' + inner + '</div><div class="ring"></div></div>'; }
function clogo(ch) {
  var p = palette(ch.name), len = ch.name.length;
  return '<div class="clogo" style="--a:' + p[0] + ';--b:' + p[1] + ';--k:' + (len <= 5 ? 1.25 : len <= 9 ? 1 : 0.78) + '"><span>' + esc(ch.name.slice(0, 22)) + '</span>' + img(ch.logo, true) + '</div>';
}
function metaHtml(it, extra) {
  var out = '';
  if (it.rating) out += '<span class="star">' + ICON.starF + it.rating + '</span>';
  if (it.year) out += '<span>' + esc(it.year) + '</span>';
  if (it.mins) out += '<span>' + mins(it.mins) + '</span>';
  if (it.seasons) out += '<span>' + count(it.seasons, 'موسم واحد', 'موسمين', 'مواسم', 'موسم') + '</span>';
  return out + (extra || '') + genres(it.sub).map(function (g) { return '<span class="pill">' + esc(g) + '</span>'; }).join('');
}

/* ---------- الخلفية ---------- */
var bdI = 0, bdKey = '';
function backdrop(it) {
  var src = it ? it.back || it.logo : null, key = it ? src || it.name : '';
  if (key === bdKey) return;
  bdKey = key;
  var tint = $('#bdt'), cur = $('#bd' + bdI), nxt = $('#bd' + (bdI ^ 1));
  if (!it) { cur.classList.remove('on'); tint.classList.remove('on'); return; }
  var showTint = function () {
    var p = palette(it.name);
    tint.style.setProperty('--a', p[0]); tint.style.setProperty('--b', p[1]);
    tint.classList.add('on'); cur.classList.remove('on');
  };
  if (!src || it.k === D.LIVE) return showTint();
  var im = new Image();
  im.onload = function () {
    if (bdKey !== key) return;
    nxt.src = src; nxt.classList.toggle('tall', !it.back);
    nxt.classList.add('on'); cur.classList.remove('on'); tint.classList.remove('on');
    bdI ^= 1;
  };
  im.onerror = function () { if (bdKey === key) showTint(); };
  im.src = src;
}

/* ---------- المكتبة ---------- */
var ready = false, loadErr = null;
function startLibrary(s) {
  ready = false; loadErr = null;
  return D.load(s).then(function () { ready = true; }, function (e) { if (e.message !== 'stale') loadErr = e.message; throw e; });
}
function message(head, body, spin, buttons) {
  var m = $('#msg');
  m.innerHTML = (spin ? '<div class="spin"></div>' : '') + (head ? '<h2>' + esc(head) + '</h2>' : '') + '<p>' + esc(body || '') + '</p>' +
    (buttons ? '<div class="actions">' + buttons + '</div>' : '');
  m.hidden = false;
}
function hideMessage() { $('#msg').hidden = true; }

/* ---------- التصفح: الرئيسية، الأفلام، المسلسلات، قائمتي، البحث ---------- */
var kind = 'home', feat = 0, featList = [], featTimer = null, mode = 'bill', query = '';
function row(t, type, items) { return { t: t, type: type, items: items }; }
function rowsFor(k) {
  var L = D.L, out = [];
  if (k === 'search') {
    if (!query) return out;
    return [row('أفلام', 'poster', D.head(D.filter(D.MOVIE, D.ALL, query), 30)), row('مسلسلات', 'poster', D.head(D.filter(D.SERIES, D.ALL, query), 30)),
      row('قنوات', 'chan', D.head(D.filter(D.LIVE, D.ALL, query), 30))];
  }
  if (k === 'home' || k === 'favs') out.push(row('كمّل المشاهدة', 'cont', D.recents()));
  if (k === 'home') {
    var fav = D.favs(D.LIVE);
    out.push(row('أفضل 10 في شاشة', 'top', D.top(10, [D.MOVIE, D.SERIES])), row('أفلام جديدة', 'poster', D.head(D.newest(D.MOVIE), 24)),
      row('مسلسلات جديدة', 'poster', D.head(D.newest(D.SERIES), 24)), row(fav.length ? 'قنواتك' : 'القنوات', 'chan', fav.length ? fav : D.head(L.items.live || [], 20)),
      row('قائمتي', 'poster', D.favs(D.MOVIE).concat(D.favs(D.SERIES))));
  } else if (k === 'favs') {
    out.push(row('قنواتك', 'chan', D.favs(D.LIVE)), row('أفلام', 'poster', D.favs(D.MOVIE)), row('مسلسلات', 'poster', D.favs(D.SERIES)));
  } else {
    var p = k === 'movies' ? D.MOVIE : D.SERIES;
    out.push(row(p === D.MOVIE ? 'أفضل 10 أفلام' : 'أفضل 10 مسلسلات', 'top', D.top(10, [p])), row('أضيفت جديد', 'poster', D.head(D.newest(p), 24)));
    D.byGroup(p, 24).forEach(function (g) { out.push(row(g.group.name, 'poster', g.items)); });
  }
  return out.filter(function (r) { return r.items.length; });
}
function featuredFor(k) {
  if (k === 'search') return [];
  var src = k === 'favs' ? D.favs(D.MOVIE).concat(D.favs(D.SERIES)) : k === 'home' ? D.top(10, [D.MOVIE, D.SERIES]) : D.top(10, [k === 'movies' ? D.MOVIE : D.SERIES]);
  return src.filter(function (i) { return i.logo; }).slice(0, 5);
}
function cardHtml(type, it, n) {
  var i = reg(it), at = ' data-f data-i="' + i + '" data-t="' + type + '"';
  if (type === 'chan') {
    var p = palette(it.name);
    return '<div class="card wide chcard"' + at + '>' + frame(it.logo ? img(it.logo) + '<div class="nm">' + esc(it.name) + '</div>' : '<div class="big center">' + esc(it.name) + '</div>', '')
      .replace('class="art "', 'class="art" style="--a:' + p[0] + ';--b:' + p[1] + '"') + '</div>';
  }
  if (type === 'cont') {
    var pct = it.dur ? Math.round(it.pos / it.dur * 100) : 0;
    return '<div class="card wide"' + at + '>' + frame(tp(it) + img(it.k === D.EP && it.still ? it.logo : it.back || it.logo) + '<div class="shade2"></div>') +
      '<div class="bar"><i style="--p:' + pct + '%"></i></div></div>';
  }
  if (type === 'ep') {
    return '<div class="card ep wide"' + at + '>' + frame(tp(it) + img(it.logo) + '<div class="shade2"></div><div class="num">' + it.e + '</div>' + (n.done ? '<div class="seen">شفتها</div>' : '') +
      (n.p ? '<div class="bar"><i style="--p:' + n.p + '%"></i></div>' : '')) + '<div class="t">' + esc(it.ep || 'الحلقة ' + it.e) + '</div><div class="s"><span>الحلقة ' + it.e + '</span>' +
      (it.mins ? '<span>' + mins(it.mins) + '</span>' : '') + '</div></div>';
  }
  var art = tp(it) + img(it.logo);
  if (type === 'top') return '<div class="card top"' + at + '><div class="num">' + (n + 1) + '</div>' + frame(art) + '</div>';
  return '<div class="card poster"' + at + '>' + frame(art) + '</div>';
}
function rowHtml(r) {
  return '<div class="row"><h2>' + esc(r.t) + '</h2><div class="vp"><div class="track" data-group>' + r.items.map(function (it, i) { return cardHtml(r.type, it, i); }).join('') + '</div></div></div>';
}
function renderBrowse(k) {
  kind = k; feat = 0; mode = 'bill';
  if (!ready) {
    $('#rows').innerHTML = ''; $('#info').innerHTML = ''; backdrop(null);
    if (loadErr) message('ما قدرنا نجيب القائمة', loadErr, false, '<div class="btn" data-f data-act="retry" data-first>' + ICON.rotate + 'حاول مرة ثانية</div><div class="btn" data-f data-go="add">' + ICON.gear + 'الاشتراكات</div>');
    else message(null, 'نجهّز القنوات والأفلام والمسلسلات…', true);
    return;
  }
  hideMessage();
  var rows = rowsFor(k);
  featList = featuredFor(k);
  $('#rows').innerHTML = rows.map(rowHtml).join('');
  $('#info').innerHTML = '';
  if (!rows.length && k !== 'search') message(k === 'favs' ? 'قائمتك فاضية' : 'ما فيه شي هنا',
    k === 'favs' ? 'افتح أي فيلم أو مسلسل واضغط ＋ عشان ينضاف لقائمتي.' : 'ما فيه شي في هذا القسم من اشتراكك.');
  showFeatured(true);
  placeRows(null);
  clearInterval(featTimer);
  // الواجهة تتبدّل كل 9 ثواني بس وأنت واقف على أزرارها
  featTimer = setInterval(function () {
    if (screen === 'browse' && mode === 'bill' && featList.length > 1 && cur && cur.closest('#info')) { feat = (feat + 1) % featList.length; showFeatured(false); }
  }, 9000);
}
function playLabel(it) {
  if (it.k === D.SERIES) { var l = D.lastEpisode(it); return l ? 'كمّل الحلقة ' + l.e : 'شغّل'; }
  return D.resumeAt(it) ? 'كمّل' : 'شغّل';
}
function searchHead() {
  return '<div class="shead"><h1>بحث</h1><input class="field" id="q" data-f data-first placeholder="اكتب اسم فيلم أو مسلسل أو قناة" value="' + esc(query) + '" autocomplete="off">' +
    '<p>' + (query ? (D.filter(D.MOVIE, D.ALL, query).length + D.filter(D.SERIES, D.ALL, query).length + D.filter(D.LIVE, D.ALL, query).length ? '' : 'ما لقينا شي باسم «' + esc(query) + '». جرّب كلمة أقصر.') : 'اضغط OK على الخانة وتطلع لك لوحة المفاتيح.') + '</p></div>';
}
function showFeatured(first, focusPlay) {
  var html;
  if (kind === 'search') html = searchHead();
  else if (!featList.length) { html = ''; backdrop(null); }
  else {
    var it = featList[feat % featList.length], i = reg(it), n = featList.length;
    needInfo(it, function () { if (featList[feat % featList.length] === it && mode === 'bill') { var s = $('#info .syn'); if (s) s.textContent = it.plot; backdrop(it); } });
    html = '<h1>' + esc(it.name) + '</h1><div class="meta">' + metaHtml(it) + '</div><p class="syn">' + esc(it.plot || '') + '</p>' +
      '<div class="actions"><div class="btn" data-f data-act="play" data-i="' + i + '" data-first>' + ICON.play + playLabel(it) + '</div>' +
      '<div class="btn" data-f data-act="details" data-i="' + i + '">' + ICON.info + 'معلومات أكثر</div>' +
      '<div class="btn round" data-f data-act="fav" data-i="' + i + '">' + (D.isFav(it) ? ICON.check : ICON.plus) + '</div></div>' +
      (n > 1 ? '<div class="dots">' + featList.map(function (_, j) { return '<i class="' + (j === feat ? 'on' : '') + '"></i>'; }).join('') + '</div>' : '');
    backdrop(it);
  }
  if (first) $('#info').innerHTML = html;
  else swapInfo(html, function () { if (focusPlay || (cur && !cur.isConnected)) focus($('#info [data-first]') || $('#rows [data-f]')); });
  if (kind === 'search') backdrop(null);
}
// من أول صف لفوق: ترجع الواجهة بأزرارها
function toBill() {
  if (!$('#info [data-f]')) return;
  mode = 'bill';
  clearTimeout(infoTimer);
  placeRows(null);
  if (kind === 'search') return focus($('#q'));
  showFeatured(false, true);
}
var infoT;
function swapInfo(html, after) {
  var info = $('#info');
  clearTimeout(infoT);
  info.classList.add('out');
  infoT = setTimeout(function () { info.innerHTML = html; info.classList.remove('out'); if (after) after(); }, 160);
}
// القصة والصورة العريضة تنجاب لما تحتاجها (القوائم ما فيها قصص الأفلام)
function needInfo(it, done) {
  if (it.plot != null || (it.k !== D.MOVIE && it.k !== D.SERIES)) return;
  if (it._asking) return;
  it._asking = true;
  D.info(it).then(function (o) { D.fill(it, o); it._asking = false; if (it.plot || it.back) done(); });
}
function infoFor(el) {
  var it = itemOf(el), t = el.dataset.t;
  if (!it) return '';
  backdrop(it);
  if (it.k === D.LIVE) {
    return '<h1>' + esc(it.name) + '</h1><div class="meta"><span class="pill red"><span class="dot"></span>مباشر</span>' + (it.num ? '<span>القناة ' + it.num + '</span>' : '') + '</div>';
  }
  var extra = '';
  if (t === 'cont') {
    var left = Math.max(1, Math.ceil((it.dur - it.pos) / 60));
    extra = '<div class="cont">' + (it.k === D.EP ? '<span>الموسم ' + it.s + '، الحلقة ' + it.e + (it.ep ? ': ' + esc(it.ep) : '') + '</span>' : '') +
      '<div class="bar"><i style="--p:' + Math.round(it.pos / it.dur * 100) + '%"></i></div><span>باقي ' + mins(left) + '</span></div>';
  }
  var hot = t === 'top' ? '<span class="pill hot">رقم ' + (+$('.num', el).textContent) + '</span>' : '';
  return '<h1>' + esc(it.name) + '</h1><div class="meta">' + hot + metaHtml(it) + '</div>' + extra + '<p class="syn">' + esc(it.plot || '') + '</p>';
}
// الصف المركّز يثبت تحت المعلومات، واللي فوقه يختفي
function placeRows(r) {
  var rows = $('#rows'), all = $$('.row', rows), idx = r ? all.indexOf(r) : -1;
  var y = r ? 600 - r.offsetTop : (featList.length || kind === 'search' ? 712 : 140);
  rows.style.transform = 'translateY(' + y + 'px)';
  all.forEach(function (x, i) { x.classList.toggle('gone', i < idx); if (i >= idx - 1 && i <= Math.max(idx, 0) + 3) loadImgs(x); });
}

/* ---------- قائمة طويلة بدون ما نرسم كل العناصر (القوائم فيها آلاف القنوات) ---------- */
var ROW_H = 124;
function vlist(box, items, rowFn, idx) {
  box._v = { items: items, rowFn: rowFn, idx: idx || 0, y: 0 };
  box.style.height = items.length * ROW_H + 'px';
  box.style.transform = '';
  return vrender(box, box._v.idx);
}
function vrender(box, idx) {
  var v = box._v, H = box.parentElement.clientHeight || 900, top = +(box.dataset.top || 0), bot = +(box.dataset.bot || 60);
  if (!v.items.length) { box.innerHTML = ''; return null; }
  idx = Math.max(0, Math.min(v.items.length - 1, idx));
  v.idx = idx;
  var T = idx * ROW_H, B = T + ROW_H - 6, y = v.y;
  if (B + y > H - bot) y = H - bot - B;
  if (T + y < top) y = top - T;
  v.y = y = Math.min(0, y);
  box.style.transform = 'translateY(' + y + 'px)';
  var first = Math.max(0, Math.floor(-y / ROW_H) - 3), last = Math.min(v.items.length, first + Math.ceil(H / ROW_H) + 6), html = '';
  for (var i = first; i < last; i++) html += v.rowFn(v.items[i], i).replace('<div class="ch', '<div style="top:' + (i * ROW_H) + 'px" data-vi="' + i + '" class="ch');
  box.innerHTML = html;
  loadImgs(box);
  return $('[data-vi="' + idx + '"]', box);
}

/* ---------- المباشر ---------- */
var liveCat = D.ALL, playingId = null;
function liveGroups() {
  var g = (D.L.groups.live || []).slice(), f = D.favs(D.LIVE).length;
  if (f) g.splice(1, 0, { id: 'fav', name: 'قنواتك', count: f });
  return g;
}
function liveList(cat) { return cat === 'fav' ? D.favs(D.LIVE) : D.filter(D.LIVE, cat, ''); }
function chRow(ch) {
  return '<div class="ch' + (ch.id === playingId ? ' playing' : '') + '" data-f data-act="chan" data-i="' + reg(ch) + '"><div class="no">' + (ch.num || '') + '</div>' + clogo(ch) +
    '<div class="inf"><div class="nm">' + esc(ch.name) + '</div></div><div class="eq"><i></i><i></i><i></i></div></div>';
}
function renderLive() {
  if (!ready) return go('home');
  var gs = liveGroups();
  if (!gs.some(function (g) { return g.id === liveCat; })) liveCat = D.ALL;
  $('#cats').innerHTML = gs.map(function (g) { return '<div class="cat' + (g.id === liveCat ? ' on' : '') + '" data-f data-act="cat" data-c="' + esc(g.id) + '"><span>' + esc(g.id === D.ALL ? 'كل القنوات' : g.name) + '</span><small>' + g.count + '</small></div>'; }).join('');
  var list = liveList(liveCat), name = $('.cat.on span').textContent;
  $('#liveT').textContent = name;
  $('#liveN').textContent = count(list.length, 'قناة وحدة', 'قناتين', 'قنوات', 'قناة');
  var at = Math.max(0, list.map(function (c) { return c.id; }).indexOf(playingId));
  vlist($('#chs'), list, chRow, at);
  $('#pv').innerHTML = '';
  if (list.length) preview(list[at]);
  else $('#chs').innerHTML = '<p style="color:var(--ink3);font-size:28px;margin:30px 22px">ما فيه قنوات في هذا القسم.</p>';
}
var pvT;
function preview(ch) {
  clearTimeout(pvT);
  pvT = setTimeout(function () {
    var p = palette(ch.name), g = (D.L.groups.live || []).filter(function (x) { return x.id === ch.group; })[0];
    $('#pv').innerHTML = '<div class="scr" style="--a:' + p[0] + ';--b:' + p[1] + '">' + (ch.logo ? img(ch.logo) : '<div class="big center">' + esc(ch.name) + '</div>') + '<div class="onair"><span class="dot"></span>مباشر</div></div>' +
      '<h3>' + esc(ch.name) + '</h3><div class="sub">' + (ch.num ? 'القناة ' + ch.num : '') + (g && g.id !== '?' ? (ch.num ? '، ' : '') + esc(g.name) : '') + '</div><div id="pvEpg"></div>';
    loadImgs($('#pv'));
    D.epg(ch).then(function (l) {
      var box = $('#pvEpg');
      if (!l.length || !box || screen !== 'live') return;
      var now = l[0], nx = l[1], pct = now.z > now.a ? Math.min(100, Math.max(0, (Date.now() - now.a) / (now.z - now.a) * 100)) : 0;
      box.innerHTML = '<div class="now panel"><small>الحين</small><b>' + esc(now.t) + '</b><div class="tl"><span>' + hm(now.a) + '</span><div class="bar"><i style="--p:' + pct + '%"></i></div><span>' + hm(now.z) + '</span></div></div>' +
        (nx ? '<div class="next"><div><small>بعده</small><b>' + esc(nx.t) + '</b></div><span>' + hm(nx.a) + '</span></div>' : '');
    });
  }, 250);
}

/* ---------- التفاصيل ---------- */
var dItem = null, dSeasons = [], season = 0;
function renderDetails(it) {
  dItem = it = it || dItem;
  backdrop(it);
  drawDetails();
  if (it.plot == null || (it.k === D.SERIES && !it._eps)) {
    D.info(it).then(function (o) {
      D.fill(it, o);
      if (it.k === D.SERIES) { it._eps = D.episodes(it, o); it.seasons = it._eps.length; }
      if (dItem === it && screen === 'details') { var keep = cur && cur.closest('#dinfo') ? cur.dataset.act : null; drawDetails(); backdrop(it); focus((keep && $('#dinfo [data-act="' + keep + '"]')) || $('#dinfo [data-first]')); }
    });
  }
}
function drawDetails() {
  var it = dItem, i = reg(it), last = it.k === D.SERIES ? D.lastEpisode(it) : null, pos = it.k === D.MOVIE ? D.resumeAt(it) : 0, main;
  if (last) {
    main = '<div class="btn" data-f data-act="resumeEp" data-i="' + i + '" data-first style="--p:' + Math.round(last.pos / last.dur * 100) + '%">' + ICON.play +
      '<span class="two">كمّل الحلقة ' + last.e + (dSeasonsCount(it) > 1 ? '، الموسم ' + last.s : '') + '<span class="mini"><i></i></span></span></div>' +
      '<div class="btn" data-f data-act="firstEp" data-i="' + i + '">' + ICON.restart + 'من أول حلقة</div>';
  } else if (pos) {
    var d = it.mins ? it.mins * 60 : pos * 2;
    main = '<div class="btn" data-f data-act="play" data-i="' + i + '" data-first style="--p:' + Math.round(pos / d * 100) + '%">' + ICON.play + '<span class="two">كمّل من ' + clock(pos) + '<span class="mini"><i></i></span></span></div>' +
      '<div class="btn" data-f data-act="fromstart" data-i="' + i + '">' + ICON.restart + 'من البداية</div>';
  } else main = '<div class="btn" data-f data-act="' + (it.k === D.SERIES ? 'firstEp' : 'play') + '" data-i="' + i + '" data-first>' + ICON.play + 'شغّل</div>';
  $('#dinfo').innerHTML = '<h1>' + esc(it.name) + '</h1><div class="meta">' + metaHtml(it) + '</div><p class="syn">' + esc(it.plot || '') + '</p>' +
    '<div class="actions">' + main + '<div class="btn round" data-f data-act="fav" data-i="' + i + '">' + (D.isFav(it) ? ICON.check : ICON.plus) + '</div></div>';
  if (it.k === D.SERIES) {
    dSeasons = it._eps || [];
    var want = last ? last.s : dSeasons.length ? dSeasons[0].s : 0;
    season = Math.max(0, dSeasons.map(function (s) { return s.s; }).indexOf(want));
    $('#dbottom').innerHTML = !it._eps ? '' : !dSeasons.length ? '<h2 style="color:var(--ink3)">ما لقينا حلقات لهذا المسلسل.</h2>'
      : (dSeasons.length > 1 ? '<div class="seg panel" data-group>' + dSeasons.map(function (s, j) { return '<b class="' + (j === season ? 'on' : '') + '" data-f data-act="season" data-s="' + j + '">الموسم ' + s.s + '</b>'; }).join('') + '</div>' : '<h2>الحلقات</h2>') +
        '<div class="vp"><div class="track" id="eps" data-group></div></div>';
    if (dSeasons.length) renderEps();
  } else {
    var like = D.newest(D.MOVIE).filter(function (x) { return x !== it && x.group === it.group; }).slice(0, 12);
    $('#dbottom').innerHTML = like.length ? '<h2>ممكن يعجبك</h2><div class="vp"><div class="track" data-group>' + like.map(function (x) { return cardHtml('poster', x); }).join('') + '</div></div>' : '';
    loadImgs($('#dbottom'));
  }
}
function dSeasonsCount(it) { return it._eps ? it._eps.length : 0; }
function renderEps() {
  var last = D.lastEpisode(dItem), sv = dSeasons[season];
  $('#eps').innerHTML = sv.eps.map(function (e) {
    var done = last && (sv.s < last.s || (sv.s === last.s && e.e < last.e)), p = done ? 100 : last && last.id === e.id ? Math.round(last.pos / last.dur * 100) : 0;
    return cardHtml('ep', e, { done: done, p: p });
  }).join('');
  var tr = $('#eps'); tr._x = 0; tr.style.transform = '';
  loadImgs(tr);
}
function allEps(series) { var out = []; (series._eps || []).forEach(function (s) { out = out.concat(s.eps); }); return out; }

/* ---------- المشغّل ---------- */
var P = null, osdT, hls = null, fitMode = D.get('fit', 0);
var FITS = [['الأصلي', 'الصورة كاملة بدون قص'], ['ملء الشاشة', 'تكبير وقص الأطراف'], ['مط', 'تمتد على الشاشة كلها']];
var video = $('#video');
function playItem(it, opts) {
  opts = opts || {};
  var queue = opts.queue || [it], qi = Math.max(0, queue.indexOf(it));
  P = { item: it, live: it.k === D.LIVE, queue: queue, qi: qi, start: opts.fromStart ? 0 : opts.pos != null ? opts.pos : D.resumeAt(it), alt: false, saved: 0, nextAt: -1, nextDismissed: false };
  if (P.live) { playingId = it.id; D.put('lastLive', it.id); }
  // من «كمّل المشاهدة»: نجيب حلقات المسلسل عشان الحلقة التالية
  if (it.k === D.EP && queue.length < 2) {
    var s = (D.L.items.series || []).filter(function (x) { return x.id === it.series; })[0];
    if (s) {
      var withEps = s._eps ? Promise.resolve() : D.info(s).then(function (o) { D.fill(s, o); s._eps = D.episodes(s, o); s.seasons = s._eps.length; });
      withEps.then(function () {
        var all = allEps(s), at = all.map(function (e) { return e.id; }).indexOf(it.id);
        if (P && P.item === it && at >= 0) { P.queue = all; P.qi = at; P.item = all[at]; renderPinfo(); }
      });
    }
  }
  if (screen !== 'player') go('player'); else { corner(null); renderPlayer(); wakeOsd(); focusDefault(); }
}
function detach() {
  if (hls) { hls.destroy(); hls = null; }
  video.removeAttribute('src');
  try { video.load(); } catch (e) {}
}
function attach(url) {
  detach();
  $('#spin').classList.add('on');
  var start = P.start > 5 ? P.start : 0;
  // على التلفزيون مشغّل الجهاز أخف ويشغّل HLS وTS؛ في متصفح الكمبيوتر hls.js
  if (/\.m3u8(\?|$)/i.test(url) && !TV && window.Hls && Hls.isSupported()) {
    hls = new Hls({ startPosition: start || -1, maxBufferLength: 30 });
    hls.on(Hls.Events.ERROR, function (e, d) { if (d.fatal) failed(); });
    hls.on(Hls.Events.MANIFEST_PARSED, function () { video.play().catch(function () {}); });
    hls.loadSource(url);
    hls.attachMedia(video);
  } else {
    video.src = url;
    if (start) video.addEventListener('loadedmetadata', function once() { video.removeEventListener('loadedmetadata', once); try { video.currentTime = start; } catch (e) {} });
    var p = video.play();
    if (p && p.catch) p.catch(function () {});
  }
}
function failed() {
  if (!P || screen !== 'player') return;
  var alt = !P.alt && D.altUrl(P.item);
  if (alt) { P.alt = true; return attach(alt); }
  $('#spin').classList.remove('on');
  flash(P.item.name, 'ما اشتغل البث');
}
function stopPlayer() {
  if (!P) return;
  save(true);
  detach();
  P = null;
}
function save(force) {
  if (!P || P.live || !video.duration || !isFinite(video.duration)) return;
  var now = Date.now();
  if (!force && now - P.saved < 10000) return;
  P.saved = now;
  D.watched(P.item, Math.floor(video.currentTime), Math.floor(video.duration));
}
function flash(small, big) {
  var fl = $('#flash');
  fl.innerHTML = '<small>' + esc(small) + '</small><b>' + esc(big) + '</b>';
  fl.classList.add('show'); clearTimeout(fl._t); fl._t = setTimeout(function () { fl.classList.remove('show'); }, 2600);
}
function renderPlayer() {
  video.className = 'fit' + fitMode;
  renderPinfo();
  attach(D.streamUrl(P.item));
}
function renderPinfo() {
  var it = P.item;
  if (P.live) {
    $('#pinfo').innerHTML = '<div class="prow">' + clogo(it) + '<div class="ptitle"><h2>' + esc(it.name) + '</h2><p>' + (it.num ? 'القناة ' + it.num : 'مباشر') + '</p></div>' +
      '<div class="badges"><span class="badge panel" id="qBadge" style="display:none"></span><span class="badge panel"><span class="dot"></span>مباشر</span></div></div>' +
      '<div class="pnow" id="pnow"></div>' +
      '<div class="ctl" data-group><div class="pbtn" data-f data-act="channels" data-first>' + ICON.list + 'القنوات</div>' +
      '<div class="pbtn" data-f data-act="quality" id="qBtn" style="display:none">' + ICON.hd + 'الجودة</div>' +
      '<div class="pbtn" data-f data-act="fit">' + ICON.fit + 'مقاس الصورة</div>' +
      '<div class="pbtn" data-f data-act="fav" data-i="' + reg(it) + '">' + (D.isFav(it) ? ICON.check + 'في قنواتك' : ICON.star + 'أضف لقنواتك') + '</div>' +
      '<div class="pnext" id="pnext"></div></div>';
    loadImgs($('#pinfo'));
    D.epg(it).then(function (l) {
      if (!P || P.item !== it || !l.length) return;
      var n = l[0], x = l[1], pct = n.z > n.a ? (Date.now() - n.a) / (n.z - n.a) : 0;
      $('#pnow').outerHTML = '<div class="pnow"><small>الحين</small><b>' + esc(n.t) + '</b><span dir="ltr">' + hm(n.a) + ' – ' + hm(n.z) + '</span></div>' +
        '<div class="seek live"><div class="track2"><div class="fill" style="--p:' + Math.min(1, Math.max(0, pct)) + '"></div></div></div>';
      if (x) $('#pnext').innerHTML = 'بعده: <b>' + esc(x.t) + '</b>  ' + hm(x.a);
    });
  } else {
    var ep = it.k === D.EP, hasNext = P.qi + 1 < P.queue.length;
    $('#pinfo').innerHTML = '<div class="ptitle"><h2>' + esc(ep ? it.ep || 'الحلقة ' + it.e : it.name) + '</h2><p>' + (ep ? esc(it.name) + '، الموسم ' + it.s + '، الحلقة ' + it.e : esc([it.year, genres(it.sub)[0]].filter(Boolean).join('، '))) + '</p></div>' +
      '<div class="seek"><span id="tNow">0:00</span><div class="track2"><div class="fill" id="tFill" style="--p:0"></div><div class="knob" id="tKnob" style="--p:0"></div></div><span id="tLeft"></span></div>' +
      '<div class="ctl" data-group>' + (ep && P.queue.length > 1 ? '<div class="pbtn" data-f data-act="episodes">' + ICON.list + 'الحلقات</div>' : '') +
      (ep && hasNext ? '<div class="pbtn" data-f data-act="nextnow">' + ICON.next + 'الحلقة التالية</div>' : '') +
      '<div class="mid"><div class="pbtn ic" data-f data-act="rew">' + ICON.back10 + '</div><div class="pbtn ic big" data-f data-act="pause" data-first>' + (video.paused && video.readyState > 2 ? ICON.play : ICON.pause) + '</div><div class="pbtn ic" data-f data-act="fwd">' + ICON.fwd10 + '</div></div>' +
      '<div class="pbtn" data-f data-act="quality" id="qBtn" style="display:none">' + ICON.hd + 'الجودة</div>' +
      '<div class="pbtn" data-f data-act="fit">' + ICON.fit + 'مقاس الصورة</div></div>';
    updateSeek();
  }
  qualityButton();
}
function qualityButton() {
  var b = $('#qBtn');
  if (b) b.style.display = hls && hls.levels && hls.levels.length > 1 ? '' : 'none';
}
function updateSeek() {
  if (!P || P.live) return;
  var d = video.duration, t = video.currentTime, f = $('#tFill');
  if (!f || !d || !isFinite(d)) return;
  var p = Math.min(1, t / d);
  f.style.setProperty('--p', p); $('#tKnob').style.setProperty('--p', p);
  $('#tNow').textContent = clock(t); $('#tLeft').textContent = '-' + clock(d - t);
}
function seekBy(s) {
  if (!P || P.live || !isFinite(video.duration)) return;
  video.currentTime = Math.max(0, Math.min(video.duration - 1, video.currentTime + s));
  updateSeek();
}
function togglePause() {
  if (!P) return;
  if (video.paused) video.play(); else video.pause();
}
function tick() {
  if (screen !== 'player' || !P || P.live) return;
  updateSeek(); save(false); checkNext();
}
// آخر 3 دقائق: بطاقة الحلقة التالية، وتشتغل لحالها بعد دقيقتين (مثل PlayerActivity)
function checkNext() {
  var d = video.duration, t = video.currentTime, n = P.queue[P.qi + 1];
  if (P.item.k !== D.EP || !n || !isFinite(d) || d < 300) return;
  if (P.nextAt < 0) {
    if (P.nextDismissed || d - t > 180) return;
    P.nextAt = Math.min(d, t + 120);
    $('#cNext').innerHTML = '<div class="th">' + img(n.logo) + '<div class="bar"><i id="cdBar" style="--p:1"></i></div></div>' +
      '<div><small>الحلقة التالية</small><b>' + n.e + '. ' + esc(n.ep || 'الحلقة ' + n.e) + '</b><div class="cd" id="cdText"></div>' +
      '<div class="acts"><div class="xbtn" data-f data-act="nextnow">' + ICON.play + 'شغّلها الحين</div><div class="xbtn" data-f data-act="credits">كمّل الختام</div></div></div>';
    loadImgs($('#cNext'));
    corner('cNext');
    if (!overlay) focus($('#cNext [data-act="nextnow"]'));
  }
  if (!shown('cNext')) return;
  var wait = Math.max(0, P.nextAt - t);
  $('#cdText').textContent = 'تبدأ بعد ' + clock(wait);
  $('#cdBar').style.setProperty('--p', wait / 120);
  if (wait <= 0) playNext();
}
function playNext() {
  if (!P || P.qi + 1 >= P.queue.length) return;
  save(true);
  var n = P.queue[P.qi + 1];
  playItem(n, { queue: P.queue, pos: 0 });
  flash(n.name + '، الموسم ' + n.s + '، الحلقة ' + n.e, n.ep || 'الحلقة ' + n.e);
}
function zap(step) {
  if (!P || !P.live || P.queue.length < 2) return;
  var i = (P.qi + step + P.queue.length) % P.queue.length;
  playItem(P.queue[i], { queue: P.queue });
}
function corner(id) { $$('#corner > div').forEach(function (d) { d.classList.toggle('show', d.id === id); }); }
function hideCorner(id) { var d = $('#' + id); if (d.classList.contains('show')) { d.classList.remove('show'); if (cur && d.contains(cur)) focusDefault(); } }
function shown(id) { return $('#' + id).classList.contains('show'); }
function wakeOsd() {
  if (overlay && overlay.id === 'side') return;
  $('#osd').classList.remove('off');
  $('#corner').classList.add('up');
  clearTimeout(osdT);
  osdT = setTimeout(function () {
    if (overlay || screen !== 'player') return;
    $('#osd').classList.add('off'); $('#corner').classList.remove('up');
    if (cur && cur.closest('#osd')) { var c = $('#corner > div.show [data-f]'); focus(c || null); }
  }, 6000);
}
function focusDefault() { focus($('#corner > div.show [data-f]') || $('#pinfo [data-first]')); }
video.addEventListener('waiting', function () { $('#spin').classList.add('on'); });
video.addEventListener('playing', function () { $('#spin').classList.remove('on'); qualityButton(); });
video.addEventListener('play', function () { var b = $('[data-act="pause"]'); if (b) b.innerHTML = ICON.pause; });
video.addEventListener('pause', function () { var b = $('[data-act="pause"]'); if (b) b.innerHTML = ICON.play; save(true); });
video.addEventListener('error', function () { if (!hls) failed(); });
video.addEventListener('ended', function () {
  if (!P || P.live) return;
  save(true);
  if (P.item.k === D.EP && P.qi + 1 < P.queue.length && !P.nextDismissed) playNext(); else back();
});

/* ---------- اللوحة الجانبية (قنوات أو حلقات) والقوائم الصغيرة ---------- */
var overlay = null, overlayReturn = null;
function openSide(what) {
  var side = $('#side');
  if (what === 'channels') {
    side.innerHTML = '<h3>القنوات <small>' + count(P.queue.length, 'قناة وحدة', 'قناتين', 'قنوات', 'قناة') + '</small></h3><div class="box"><div class="vl" data-top="0" data-bot="160"></div><div class="fadeb"></div></div>';
    overlay = side; overlayReturn = cur;
    side.classList.add('open');
    var el = vlist($('.vl', side), P.queue, chRow, P.qi);
    $('#osd').classList.add('off'); $('#corner').classList.remove('up');
    return focus(el);
  }
  side.innerHTML = '<h3>' + esc(P.item.name) + ' <small>الموسم ' + P.item.s + '</small></h3><div class="box"><div class="scroll" data-scroll data-top="0" data-bot="160">' +
    P.queue.filter(function (e) { return e.s === P.item.s; }).map(function (e) {
      var p = e.e < P.item.e ? 100 : 0;
      return '<div class="ep2' + (e === P.item ? ' now' : '') + '" data-f data-act="episode" data-i="' + reg(e) + '"><div class="th">' + img(e.logo) + (p ? '<div class="bar"><i style="--p:' + p + '%"></i></div>' : '') +
        '</div><div><b>' + e.e + '. ' + esc(e.ep || 'الحلقة ' + e.e) + '</b><small>' + (e.mins ? mins(e.mins) : '') + '</small></div></div>';
    }).join('') + '</div><div class="fadeb"></div></div>';
  loadImgs(side);
  overlay = side; overlayReturn = cur;
  side.classList.add('open');
  $('#osd').classList.add('off'); $('#corner').classList.remove('up');
  focus($('.ep2.now', side) || $('[data-f]', side));
}
function openSheet(title, sub, opts, sel, act) {
  var sh = $('#sheet');
  sh.innerHTML = '<h3>' + esc(title) + '</h3>' + (sub ? '<p>' + esc(sub) + '</p>' : '') +
    opts.map(function (o, i) { return '<div class="opt' + (i === sel ? ' sel' : '') + '" data-f data-act="' + act + '" data-q="' + i + '"><span>' + esc(o[0]) + '</span>' + (o[1] ? '<small>' + esc(o[1]) + '</small>' : '') + ICON.check + '</div>'; }).join('');
  overlay = sh; overlayReturn = cur;
  sh.classList.add('open'); $('#mscrim').classList.add('on');
  focus($('.opt.sel', sh) || $('.opt', sh));
}
function openQuality() {
  var lv = hls.levels.map(function (l, i) { return { i: i, h: l.height, b: l.bitrate }; }).sort(function (a, b) { return b.h - a.h || b.b - a.b; });
  P.qmap = [-1].concat(lv.map(function (l) { return l.i; }));
  var cur0 = hls.autoLevelEnabled ? 0 : P.qmap.indexOf(hls.currentLevel);
  openSheet('الجودة', 'التلقائي يختار أعلى جودة يتحملها النت، وينزّلها بس لو احتاج عشان ما يوقف.',
    [['تلقائي، حسب سرعة النت', '']].concat(lv.map(function (l) { return [l.h ? l.h + 'p' : Math.round(l.b / 1000) + ' kbps', '']; })), cur0, 'pickq');
}
function closeOverlay() {
  if (!overlay) return false;
  overlay.classList.remove('open'); $('#mscrim').classList.remove('on');
  overlay = null;
  focus(overlayReturn && overlayReturn.isConnected ? overlayReturn : null);
  if (screen === 'player') { wakeOsd(); if (!cur) focusDefault(); }
  return true;
}

/* ---------- الاشتراكات ---------- */
var formKind = 'x', delArm = null;
function renderAdd() {
  var all = D.sources(), act = D.active();
  $('#cancelBtn').style.display = all.length ? '' : 'none';
  $('#ferr').textContent = '';
  $('#addSub').textContent = all.length ? 'أضف اشتراك ثاني، أو اختار واحد من اشتراكاتك.' : 'أضف اشتراكك: حساب Xtream أو رابط قائمة M3U.';
  $('#mine').innerHTML = !all.length
    ? '<h3>أهلًا في شاشة</h3><p>شاشة يشغّل اشتراك IPTV حقك، وما فيه قنوات جاهزة. أضف بيانات اشتراكك وتطلع لك القنوات والأفلام والمسلسلات.</p>' +
      '<p style="color:var(--ink3);font-size:22px">البيانات تنحفظ في التلفزيون بس، وما تطلع لأي مكان ثاني.</p>'
    : '<h3>اشتراكاتك</h3>' + all.map(function (s) {
      var on = act && s.id === act.id;
      return '<div class="src' + (on ? ' on' : '') + '"><b>' + esc(D.sourceTitle(s)) + '</b><small>' + (s.kind === 'xtream' ? 'Xtream' : 'M3U') + '، ' + esc(D.host(s)) +
        (on ? '، ' + esc(ready ? D.L.account || 'شغّال' : loadErr ? 'ما اشتغل' : 'يتحمّل…') : '') + '</small>' +
        (on ? '<div class="xbtn" data-f data-act="reload">' + ICON.rotate + 'حدّث القوائم</div>' : '<div class="xbtn" data-f data-act="use" data-s="' + s.id + '">استخدمه</div>') +
        '<div class="xbtn" data-f data-act="del" data-s="' + s.id + '">' + (delArm === s.id ? 'متأكد؟ اضغط مرة ثانية' : 'احذفه') + '</div></div>';
    }).join('');
}
function saveSource() {
  var v = function (id) { return $('#' + id).value.trim(); }, s;
  if (formKind === 'x') {
    if (!v('fServer') || !v('fUser') || !v('fPass')) return ($('#ferr').textContent = 'عبّ رابط السيرفر واسم المستخدم وكلمة المرور.');
    s = D.xtream(v('fServer'), v('fUser'), v('fPass'));
  } else {
    if (!v('fUrl')) return ($('#ferr').textContent = 'اكتب رابط القائمة.');
    s = D.fromLink(v('fUrl'));
  }
  if (v('fName')) s.name = v('fName');
  var prev = D.active(), btn = $('#saveBtn');
  btn.lastChild.textContent = 'نتحقّق…';
  $('#ferr').textContent = '';
  startLibrary(s).then(function () {
    D.addSource(s);
    ['fServer', 'fUser', 'fPass', 'fUrl', 'fName'].forEach(function (id) { $('#' + id).value = ''; });
    btn.lastChild.textContent = 'احفظ وتحقّق';
    stack.length = 0; show({ name: 'home' });
    toast('انضاف ' + D.sourceTitle(s));
  }, function (e) {
    btn.lastChild.textContent = 'احفظ وتحقّق';
    $('#ferr').textContent = e.message;
    if (prev) startLibrary(prev).catch(function () {});
  });
}
function useSource(id) {
  var s = D.sources().filter(function (x) { return x.id === id; })[0];
  if (!s) return;
  D.useSource(s);
  stack.length = 0;
  startLibrary(s).then(function () { if (screen === 'browse') show({ name: kind }); }, function () { if (screen === 'browse') show({ name: kind }); });
  show({ name: 'home' });
}

/* ---------- التنقل بالريموت ---------- */
var cur = null, screen = null, here = null, scale = 1, railFrom = null, infoTimer, dwellT;
var stack = [];
var SECTION = { home: 'browse', movies: 'browse', series: 'browse', favs: 'browse', search: 'browse', live: 'live', details: 'details', player: 'player', add: 'add' };
function go(name, it) {
  if (name === 'back') return back();
  if ((name === 'live' || name === 'search') && !ready) name = 'home';
  var target = { name: name, item: it || null };
  if (here && (here.name !== name || here.item !== target.item)) stack.push(here);
  if (stack.length > 30) stack.shift();
  show(target);
}
function show(t) {
  if (screen === 'player' && t.name !== 'player') stopPlayer();
  here = t;
  screen = SECTION[t.name];
  REG = REG.length > 5000 ? [] : REG;
  closeRail();
  if (overlay) { overlay.classList.remove('open'); $('#mscrim').classList.remove('on'); overlay = null; }
  hideMessage();
  $$('.screen').forEach(function (s) { s.classList.remove('on'); });
  var sec = $('#' + screen);
  if (screen === 'browse') renderBrowse(t.name);
  if (screen === 'live') renderLive();
  if (screen === 'details') renderDetails(t.item);
  if (screen === 'add') renderAdd();
  if (screen === 'player') { corner(null); renderPlayer(); wakeOsd(); }
  sec.classList.add('on');
  $('#bd').classList.toggle('off', screen !== 'browse' && screen !== 'details');
  $('#rail').classList.toggle('hide', ['browse', 'live', 'add'].indexOf(screen) < 0 || (screen === 'add' && !D.sources().length));
  $$('.ri').forEach(function (r) { r.classList.toggle('on', r.dataset.go === t.name); });
  $('#rail')._last = $('.ri[data-go="' + t.name + '"]');
  if (screen === 'live') { $('#cats')._last = $('.cat.on'); focus($('#chs .ch[data-vi="' + $('#chs')._v.idx + '"]') || $('.cat.on')); }
  else if (screen === 'player') {
    focus($('#pinfo [data-first]'));
    if (!P.live && P.start > 5) {
      $('#cResume').innerHTML = '<span>كمّلنا من وين وقفت: <b>' + clock(P.start) + '</b></span><div class="xbtn" data-f data-act="restart">' + ICON.restart + 'من البداية</div>';
      corner('cResume');
      setTimeout(function () { hideCorner('cResume'); }, 7000);
    }
  } else focus($('#msg:not([hidden]) [data-first]') || $('[data-first]', sec) || $('[data-f]', sec) || $('#rail')._last);
}
function back() {
  if (document.activeElement && document.activeElement.tagName === 'INPUT') { document.activeElement.blur(); return; }
  if (closeOverlay()) return;
  if ($('#rail').classList.contains('open')) { closeRail(); focus(railFrom && railFrom.isConnected ? railFrom : $('#' + screen + ' [data-f]')); return; }
  if (screen === 'player' && P && P.nextAt >= 0 && shown('cNext')) { P.nextDismissed = true; hideCorner('cNext'); return; }
  if (screen === 'browse' && mode === 'rows') return toBill();
  var prev = stack.pop();
  if (prev) return show(prev);
  if (screen === 'browse' && kind !== 'home') return show({ name: 'home' });
  if (screen === 'browse' || screen === 'live' || screen === 'add') {
    if (!$('#rail').classList.contains('hide') && (!cur || !cur.closest('#rail'))) return focus($('#rail')._last || $('.ri'));
    exitApp();
  }
}
function exitApp() {
  try { if (TIZEN) return tizen.application.getCurrentApplication().exit(); } catch (e) {}
  try { if (window.PalmSystem) return window.PalmSystem.platformBack(); } catch (e) {}
  window.close();
}
function openRail() { $('#rail').classList.add('open'); $('#dim').classList.add('on'); }
function closeRail() { $('#rail').classList.remove('open'); $('#dim').classList.remove('on'); }
function focus(el) {
  if (cur) cur.classList.remove('f');
  cur = el || null;
  if (!el) return;
  el.classList.add('f');
  var g = el.closest('[data-group]');
  if (g) g._last = el;
  var inRail = !!el.closest('#rail');
  if (inRail) openRail(); else closeRail();
  reveal(el);
  clearTimeout(dwellT);
  if (screen === 'browse' && !inRail) {
    var r = el.closest('.row');
    if (r) {
      mode = 'rows';
      placeRows(r);
      clearTimeout(infoTimer);
      // لو المستخدم ماسك السهم، ننتظر لين يهدأ بعدين نبدّل المعلومات والخلفية
      if (kind !== 'search') infoTimer = setTimeout(function () { if (cur === el) swapInfo(infoFor(el)); }, 180);
      var it = itemOf(el);
      if (it && it.plot == null && kind !== 'search') dwellT = setTimeout(function () { needInfo(it, function () { if (cur === el) swapInfo(infoFor(el)); }); }, 700);
    }
  }
  if (screen === 'live' && el.classList.contains('ch') && !el.closest('#side')) preview(itemOf(el));
}
function reveal(el) {
  var tr = el.closest('.track');
  if (tr) {
    var Wd = tr.clientWidth, L = el.offsetLeft, R = L + el.offsetWidth, m = 30, x = tr._x || 0;
    if (L + x < m) x = m - L;
    if (R + x > Wd - m) x = Wd - m - R;
    x = Math.max(0, x);
    if (x !== tr._x) { tr._x = x; tr.style.transform = 'translateX(' + x + 'px)'; }
    loadImgs(tr);
  }
  var sc = el.closest('[data-scroll]');
  if (sc) {
    var H = sc.parentElement.clientHeight, top = +(sc.dataset.top || 0), bot = +(sc.dataset.bot || 60), T = el.offsetTop, B = T + el.offsetHeight, y = sc._y || 0;
    if (B + y > H - bot) y = H - bot - B;
    if (T + y < top) y = top - T;
    y = Math.min(0, y);
    if (y !== sc._y) { sc._y = y; sc.style.transform = 'translateY(' + y + 'px)'; }
  }
}
function visible(e) { return e.offsetParent !== null && !e.closest('[hidden]'); }
function candidates() {
  if (overlay) return $$('[data-f]', overlay);
  var msg = $('#msg'), list = !msg.hidden ? $$('[data-f]', msg) : $$('[data-f]', $('#' + screen));
  if (screen === 'player') list = list.filter(function (e) { return !e.closest('.off') && (!e.closest('#corner') || e.closest('.show')); });
  if (!$('#rail').classList.contains('hide')) list = list.concat($$('[data-f]', $('#rail')));
  return list.filter(visible);
}
function move(dir) {
  if (!cur || !cur.isConnected) { if (screen === 'player') focusDefault(); else focus(candidates()[0]); return; }
  if (screen === 'browse' && dir === 'up' && cur.closest('.row') === $('#rows .row') && $('#info [data-f]')) return toBill();
  var vl = cur.closest('.vl');
  if (vl && (dir === 'up' || dir === 'down')) {
    var n = vl._v.idx + (dir === 'up' ? -1 : 1);
    if (n >= 0 && n < vl._v.items.length) focus(vrender(vl, n));
    return;
  }
  var a = cur.getBoundingClientRect(), ax = (a.left + a.right) / 2, ay = (a.top + a.bottom) / 2, horiz = dir === 'left' || dir === 'right';
  var best = null, bs = Infinity;
  candidates().forEach(function (e) {
    // فوق وتحت ما يدخلون الشريط الجانبي ولا يطلعون منه
    if (e === cur || (!horiz && !cur.closest('#rail') !== !e.closest('#rail'))) return;
    var b = e.getBoundingClientRect(), bx = (b.left + b.right) / 2, by = (b.top + b.bottom) / 2, p;
    if (dir === 'left') { if (bx >= ax - 1 || b.right > a.left + a.width / 2) return; p = a.left - b.right; }
    if (dir === 'right') { if (bx <= ax + 1 || b.left < a.right - a.width / 2) return; p = b.left - a.right; }
    if (dir === 'up') { if (by >= ay - 1 || b.bottom > a.top + a.height / 2) return; p = a.top - b.bottom; }
    if (dir === 'down') { if (by <= ay + 1 || b.top < a.bottom - a.height / 2) return; p = b.top - a.bottom; }
    // فوق وتحت: اللي تحتك مباشرة يفوز، وبينهم الأقرب لبداية السطر (اليمين)
    var over = b.left < a.right && b.right > a.left;
    var score = Math.max(p, 0) + (horiz ? Math.abs(by - ay) * 3 : over ? Math.abs(b.right - a.right) * 0.05 : Math.abs(bx - ax) * 0.4);
    if (score < bs) { bs = score; best = e; }
  });
  if (!best) return;
  var bvl = best.closest('.vl');
  if (bvl && bvl !== vl) best = vrender(bvl, bvl._v.idx) || best;
  var g = best.closest('[data-group]');
  if (g && g !== cur.closest('[data-group]') && g._last && g._last.isConnected && g._last.offsetParent && g.contains(g._last)) best = g._last;
  if (best.closest('#rail') && !cur.closest('#rail')) railFrom = cur;
  focus(best);
}
function activate(el) {
  if (!el) return;
  var d = el.dataset, it = itemOf(el);
  if (el.tagName === 'INPUT') { el.focus(); return; }
  if (d.go) return go(d.go);
  if (d.t) {
    if (it.k === D.LIVE) { var row = $$('[data-i]', el.parentNode).map(itemOf); return playItem(it, { queue: row }); }
    if (d.t === 'ep') return playItem(it, { queue: allEps(dItem), pos: D.resumeAt(it) });
    if (d.t === 'cont') return it.k === D.SERIES ? go('details', it) : playItem(it, { pos: it.pos });
    return go('details', it);
  }
  switch (d.act) {
    case 'play': if (it.k === D.SERIES) return go('details', it); return playItem(it);
    case 'details': return go('details', it);
    case 'fromstart': return playItem(it, { fromStart: true });
    case 'resumeEp': case 'firstEp': {
      var all = allEps(it), l = d.act === 'resumeEp' ? D.lastEpisode(it) : null;
      var at = l ? all.map(function (e) { return e.id; }).indexOf(l.id) : 0;
      if (!all.length) return toast('الحلقات لسا تتحمّل');
      return playItem(all[Math.max(0, at)], { queue: all, pos: l ? l.pos : 0 });
    }
    case 'episode': { var e = it; if (overlay) { overlay.classList.remove('open'); overlay = null; } return playItem(e, { queue: P.queue, pos: 0 }); }
    case 'cat': liveCat = d.c; renderLive(); return focus($('.cat[data-c="' + d.c + '"]'));
    case 'chan':
      if (screen === 'player') { var q = P.queue; closeOverlay(); return playItem(it, { queue: q }); }
      return playItem(it, { queue: $('#chs')._v.items });
    case 'season': season = +d.s; $$('.seg b', $('#dbottom')).forEach(function (b) { b.classList.toggle('on', b === el); }); return renderEps();
    case 'channels': return openSide('channels');
    case 'episodes': return openSide('episodes');
    case 'quality': return openQuality();
    case 'pickq': hls.currentLevel = P.qmap[+d.q]; return closeOverlay();
    case 'fit': return openSheet('مقاس الصورة', '', FITS, fitMode, 'pickfit');
    case 'pickfit': fitMode = +d.q; D.put('fit', fitMode); video.className = 'fit' + fitMode; return closeOverlay();
    case 'pause': return togglePause();
    case 'rew': return seekBy(-10);
    case 'fwd': return seekBy(10);
    case 'restart': video.currentTime = 0; hideCorner('cResume'); return focusDefault();
    case 'nextnow': return playNext();
    case 'credits': P.nextDismissed = true; return hideCorner('cNext');
    case 'fav': {
      var on = D.toggleFav(it);
      if (screen === 'player') el.innerHTML = on ? ICON.check + 'في قنواتك' : ICON.star + 'أضف لقنواتك';
      else el.innerHTML = on ? ICON.check : ICON.plus;
      return toast(on ? 'صار في قائمتي' : 'طلع من قائمتي');
    }
    case 'kind':
      formKind = d.k;
      $$('#kind b').forEach(function (b) { b.classList.toggle('on', b === el); });
      $('#fx').hidden = d.k !== 'x'; $('#fm').hidden = d.k === 'x';
      return;
    case 'save': return saveSource();
    case 'use': return useSource(d.s);
    case 'reload': { var s = D.active(); stack.length = 0; startLibrary(s).then(renderAdd, renderAdd); toast('نحدّث القوائم من السيرفر'); return renderAdd(); }
    case 'del': {
      if (delArm !== d.s) { delArm = d.s; renderAdd(); return focus($('[data-act="del"][data-s="' + d.s + '"]')); }
      var gone = D.sources().filter(function (x) { return x.id === d.s; })[0], wasActive = D.active() && D.active().id === d.s;
      delArm = null;
      D.removeSource(gone);
      if (wasActive) { ready = false; var nx = D.active(); if (nx) startLibrary(nx).then(renderAdd, renderAdd); }
      stack.length = 0; here = null;
      return show({ name: 'add' });
    }
    case 'retry': { var a = D.active(); if (a) startLibrary(a).then(function () { show({ name: kind }); }, function () { show({ name: kind }); }); return show({ name: kind }); }
  }
}

/* ---------- المفاتيح ---------- */
var KEY = { 37: 'left', 38: 'up', 39: 'right', 40: 'down', 13: 'ok', 8: 'back', 27: 'back', 461: 'back', 10009: 'back',
  415: 'play', 19: 'pause', 10252: 'playpause', 179: 'playpause', 413: 'stop', 65376: 'ok', 65385: 'back', 417: 'ff', 412: 'rw', 427: 'chup', 428: 'chdown', 33: 'chup', 34: 'chdown' };
if (TIZEN) {
  try { ['MediaPlayPause', 'MediaPlay', 'MediaPause', 'MediaStop', 'MediaFastForward', 'MediaRewind', 'ChannelUp', 'ChannelDown'].forEach(function (k) { tizen.tvinputdevice.registerKey(k); }); } catch (e) {}
}
var introDone = false;
function endIntro() {
  if (introDone) return;
  introDone = true;
  $('#intro').classList.add('gone');
  $('#stage').classList.add('rise');
  setTimeout(function () { $('#stage').classList.remove('rise'); }, 1400);
  if (!screen) show({ name: D.sources().length ? 'home' : 'add' });
}
addEventListener('keydown', function (e) {
  var k = KEY[e.keyCode] || (e.key === ' ' ? 'ok' : null);
  if (!introDone) { endIntro(); e.preventDefault(); return; }
  var typing = document.activeElement && document.activeElement.tagName === 'INPUT';
  if (typing) {
    // في خانة الكتابة: يمين ويسار يحركون المؤشر، والباقي يطلّعك منها
    if (k === 'left' || k === 'right' || k === 'chup' || k === 'chdown' || (k === 'back' && e.keyCode === 8) || !k) return;
    document.activeElement.blur();
    e.preventDefault();
    if (k === 'up' || k === 'down') move(k);
    return;
  }
  if (!k) return;
  e.preventDefault();
  if (screen === 'player' && P) {
    if (k === 'play' || k === 'pause' || k === 'playpause') { if (k === 'play') video.play(); else if (k === 'pause') video.pause(); else togglePause(); return wakeOsd(); }
    if (k === 'ff' || k === 'rw') { seekBy(k === 'ff' ? 30 : -30); return wakeOsd(); }
    if (k === 'chup' || k === 'chdown') return zap(k === 'chup' ? 1 : -1);
    if (k === 'stop') return back();
    var hidden = $('#osd').classList.contains('off'), inCorner = cur && cur.closest('#corner');
    if (hidden && !overlay && !inCorner && k !== 'back') {
      // OSD مخفي: يمين ويسار يقدّمون ويرجّعون، والباقي يطلّعه
      if (!P.live && (k === 'left' || k === 'right')) { seekBy(k === 'right' ? 10 : -10); return wakeOsd(); }
      wakeOsd(); return focusDefault();
    }
    if (!hidden) wakeOsd();
  }
  if (k === 'ok') activate(cur);
  else if (k === 'back') back();
  else if (k === 'left' || k === 'right' || k === 'up' || k === 'down') move(k);
});
$('#stage').addEventListener('click', function (e) {
  if (!introDone) return endIntro();
  if (screen === 'player') wakeOsd();
  if (e.target === $('#mscrim')) return back();
  var el = e.target.closest('[data-f]');
  if (el) { if (el.closest('#rail') && cur && !cur.closest('#rail')) railFrom = cur; focus(el); if (el.tagName !== 'INPUT') activate(el); }
});
document.addEventListener('focusin', function (e) { if (e.target.tagName === 'INPUT' && e.target !== cur) focus(e.target); });
document.addEventListener('input', function (e) {
  if (e.target.id !== 'q') return;
  clearTimeout(e.target._t);
  e.target._t = setTimeout(function () {
    query = e.target.value.trim();
    var keep = e.target;
    $('#rows').innerHTML = rowsFor('search').map(rowHtml).join('');
    $('#info .shead p').innerHTML = searchHead().split('<p>')[1].split('</p>')[0];
    placeRows(null);
    if (cur !== keep) focus(keep);
  }, 350);
});
document.addEventListener('visibilitychange', function () { if (document.hidden && P) { save(true); if (!P.live) video.pause(); } });

function clockTick() {
  var d = new Date();
  $$('[data-clock]').forEach(function (c) { c.textContent = (d.getHours() % 12 || 12) + ':' + pad2(d.getMinutes()) + ' ' + (d.getHours() < 12 ? 'ص' : 'م'); });
}
clockTick(); setInterval(clockTick, 15000);
setInterval(tick, 500);
function fit() {
  scale = Math.min(innerWidth / 1920, innerHeight / 1080);
  $('#stage').style.transform = 'translate(' + (innerWidth - 1920 * scale) / 2 + 'px,' + (innerHeight - 1080 * scale) / 2 + 'px) scale(' + scale + ')';
}
fit(); addEventListener('resize', fit);

var first = D.active();
if (first) startLibrary(first).then(function () { if (screen === 'browse') show(here); }, function () { if (screen === 'browse') show(here); });
setTimeout(endIntro, 2900);
