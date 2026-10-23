'use strict';
/* البيانات: الاشتراكات، Xtream وM3U، المفضلة ووين وقفت. نفس منطق Library.java وStore.java في تطبيق أندرويد.
   مكتوب لمتصفحات التلفزيونات القديمة (webOS 4 = Chromium 53): بدون async ولا ?. ولا padStart ولا Object.entries. */
var D = (function () {
  var LIVE = 'live', MOVIE = 'movie', SERIES = 'series', EP = 'ep', ALL = '*';
  var KINDS = [LIVE, MOVIE, SERIES];

  /* ---------- التخزين ---------- */
  function get(k, d) {
    try { var v = localStorage.getItem('shasha.' + k); return v == null ? d : JSON.parse(v); } catch (e) { return d; }
  }
  function put(k, v) {
    try { localStorage.setItem('shasha.' + k, JSON.stringify(v)); } catch (e) {}
  }

  /* ---------- الشبكة ---------- */
  function fetchText(url, ms) {
    return new Promise(function (ok, fail) {
      var x = new XMLHttpRequest();
      x.open('GET', url);
      x.timeout = ms || 60000;
      x.onload = function () { x.status >= 200 && x.status < 300 ? ok(x.responseText) : fail(new Error('السيرفر رد بخطأ ' + x.status + '.')); };
      x.onerror = function () { fail(new Error('ما قدرنا نوصل للسيرفر. تأكد من الرابط والنت.')); };
      x.ontimeout = function () { fail(new Error('السيرفر تأخر كثير في الرد.')); };
      x.send();
    });
  }
  function fetchJson(url) {
    return fetchText(url).then(function (t) {
      try { return JSON.parse(t); } catch (e) { throw new Error('السيرفر رد بشي غير Xtream. تأكد من رابط السيرفر.'); }
    });
  }

  /* ---------- الاشتراك ---------- */
  function withScheme(s) { return /^[a-z][a-z0-9+.-]*:\/\//i.test(s) ? s : 'http://' + s; }
  // يقبل الرابط بأي شكل: بدون http، أو معه / في الآخر، أو معه player_api.php
  function cleanServer(s) {
    s = withScheme(String(s).trim());
    var q = s.indexOf('?');
    if (q > 0) s = s.slice(0, q);
    ['/player_api.php', '/get.php', '/c/'].forEach(function (t) { if (s.slice(-t.length) === t) s = s.slice(0, -t.length); });
    return s.replace(/\/+$/, '');
  }
  function newId() { return Date.now().toString(36) + Math.random().toString(36).slice(2, 6); }
  function xtream(server, user, pass) { return { id: newId(), kind: 'xtream', server: cleanServer(server), user: String(user).trim(), pass: String(pass).trim() }; }
  // رابط get.php فيه username وpassword = حساب Xtream (يجيب الأفلام والمسلسلات بشكل أحسن)
  function fromLink(link) {
    link = withScheme(String(link).trim());
    var m = /^([a-z]+:\/\/[^\/?#]+)([^?#]*)\?([^#]*)/i.exec(link);
    if (m && /(get|player_api)\.php$/.test(m[2])) {
      var q = {};
      m[3].split('&').forEach(function (p) { var i = p.indexOf('='); if (i > 0) q[p.slice(0, i)] = decodeURIComponent(p.slice(i + 1).replace(/\+/g, ' ')); });
      if (q.username && q.password) return xtream(m[1] + m[2].slice(0, m[2].lastIndexOf('/')), q.username, q.password);
    }
    return { id: newId(), kind: 'm3u', url: link };
  }
  function api(s, action) {
    return s.server + '/player_api.php?username=' + encodeURIComponent(s.user) + '&password=' + encodeURIComponent(s.pass) + (action ? '&action=' + action : '');
  }
  function host(s) { var m = /:\/\/([^\/:?#]+)/.exec(s.kind === 'xtream' ? s.server : s.url); return m ? m[1] : 'اشتراك'; }
  function sourceTitle(s) { return s.name || host(s); }

  function sources() { return get('sources', []); }
  function active() { var all = sources(), id = get('active', null); return all.filter(function (s) { return s.id === id; })[0] || all[0] || null; }
  function addSource(s) { put('sources', sources().concat([s])); put('active', s.id); }
  function useSource(s) { put('active', s.id); }
  function removeSource(s) {
    put('sources', sources().filter(function (x) { return x.id !== s.id; }));
    try { localStorage.removeItem('shasha.fav.' + s.id); localStorage.removeItem('shasha.recent.' + s.id); } catch (e) {}
  }

  /* ---------- المكتبة ---------- */
  var L = { source: null, items: {}, groups: {}, account: null, ext: 'm3u8', byNew: {} };

  function group(list, cats) {
    var other = { id: '?', name: 'بدون قسم', count: 0 }, map = {};
    cats.forEach(function (g) { g.count = 0; map[g.id] = g; });
    list.forEach(function (i) { var g = i.group != null ? map[i.group] : null; if (g) g.count++; else { i.group = '?'; other.count++; } });
    var out = [{ id: ALL, name: 'الكل', count: list.length }].concat(cats.filter(function (g) { return g.count > 0; }));
    if (other.count) out.push(other);
    return out;
  }
  function rating(s) { var v = parseFloat(s); return v > 0 ? (v > 10 ? v / 10 : v).toFixed(1) : null; }
  function year(y, date) { var v = /^\d{4}/.test(y || '') ? y : date; return /^\d{4}/.test(v || '') ? v.slice(0, 4) : null; }
  function str(v) { return v == null ? '' : String(v).trim(); }
  function firstUrl(o) { var s = Array.isArray(o) ? o[0] : o; return typeof s === 'string' && s.indexOf('http') === 0 ? s : null; }

  function xtreamItem(kind, o) {
    var i = { k: kind, name: str(o.name), group: o.category_id != null ? String(o.category_id) : null };
    if (kind === LIVE) { i.id = o.stream_id; i.logo = o.stream_icon; i.num = +o.num || 0; }
    else if (kind === MOVIE) {
      i.id = o.stream_id; i.logo = o.stream_icon; i.ext = o.container_extension; i.rating = rating(o.rating);
      i.year = year(o.year, o.releasedate); i.added = +o.added || 0;
    } else {
      i.id = o.series_id; i.logo = o.cover; i.rating = rating(o.rating);
      i.year = year(o.year, o.releaseDate || o.release_date); i.sub = o.genre || null; i.added = +o.last_modified || 0;
      if (str(o.plot)) i.plot = str(o.plot);
      i.back = firstUrl(o.backdrop_path);
    }
    if (i.id != null) i.id = String(i.id);
    return i;
  }
  var CAT = { live: 'get_live_categories', movie: 'get_vod_categories', series: 'get_series_categories' };
  var LIST = { live: 'get_live_streams', movie: 'get_vod_streams', series: 'get_series' };
  function loadXtream(s) {
    return fetchJson(api(s)).then(function (o) {
      var u = o && o.user_info;
      if (!u || +u.auth === 0) throw new Error('اسم المستخدم أو كلمة المرور غلط.');
      var exp = +u.exp_date || 0, status = str(u.status);
      L.account = status && !/^active$/i.test(status) ? 'موقوف أو منتهي (' + status + ')'
        : exp ? 'فعّال، ينتهي ' + new Date(exp * 1000).toLocaleDateString('ar-SA-u-nu-latn-ca-gregory', { day: 'numeric', month: 'long', year: 'numeric' }) : 'فعّال، بدون تاريخ انتهاء';
      var f = JSON.stringify(u.allowed_output_formats || []);
      // المتصفح يشغّل HLS؛ TS بس لو السيرفر ما يسمح بغيره
      L.ext = f.indexOf('"m3u8"') >= 0 || f === '[]' ? 'm3u8' : 'ts';
      return Promise.all(KINDS.map(function (k) {
        return Promise.all([fetchJson(api(s, CAT[k])), fetchJson(api(s, LIST[k]))]).then(function (r) {
          var cats = (Array.isArray(r[0]) ? r[0] : []).filter(function (c) { return c.category_id != null; })
            .map(function (c) { return { id: String(c.category_id), name: str(c.category_name) }; });
          var list = (Array.isArray(r[1]) ? r[1] : []).map(function (o) { return xtreamItem(k, o); }).filter(function (i) { return i.id != null; });
          L.items[k] = list; L.groups[k] = group(list, cats);
        });
      }));
    });
  }

  // الاسم بعد أول فاصلة برا علامات التنصيص: #EXTINF:-1 tvg-name="a, b",الاسم
  function title(line) {
    var q = false;
    for (var i = 0; i < line.length; i++) { var c = line[i]; if (c === '"') q = !q; else if (c === ',' && !q) return line.slice(i + 1).trim(); }
    return '';
  }
  function attr(line, key) { var m = new RegExp(key + '="([^"]*)"').exec(line); return m && m[1].trim() ? m[1].trim() : null; }
  function isVod(url) {
    var u = url.toLowerCase().split('?')[0];
    return u.indexOf('/movie/') >= 0 || u.indexOf('/series/') >= 0 || /\.(mp4|mkv|avi|mov|m4v)$/.test(u);
  }
  function parseM3U(text) {
    var live = [], vod = [], lc = [], vc = [], seen = {}, next = null, grp = null, any = false;
    text.split(/\r?\n/).forEach(function (line) {
      line = line.trim();
      if (line.indexOf('#EXTM3U') === 0) any = true;
      else if (line.indexOf('#EXTINF') === 0) {
        any = true;
        next = { name: title(line) || attr(line, 'tvg-name') || '', logo: attr(line, 'tvg-logo'), group: attr(line, 'group-title') };
      } else if (line.indexOf('#EXTGRP:') === 0) grp = line.slice(8).trim();
      else if (line && line[0] !== '#') {
        var i = next || { name: '' };
        i.url = i.id = line;
        if (!i.name) i.name = decodeURIComponent(line.split('?')[0].split('/').pop() || line);
        if (!i.group) i.group = grp || '';
        var v = isVod(line);
        i.k = v ? MOVIE : LIVE;
        if (!v) i.num = live.length + 1;
        (v ? vod : live).push(i);
        var key = (v ? 'v' : 'l') + i.group;
        if (i.group && !seen[key]) { seen[key] = 1; (v ? vc : lc).push({ id: i.group, name: i.group }); }
        next = null; grp = null;
      }
    });
    if (!any) throw new Error('الرابط ما فيه قائمة M3U.');
    return { live: live, movie: vod, liveGroups: lc, movieGroups: vc };
  }
  function loadM3U(s) {
    return fetchText(s.url, 120000).then(function (t) {
      var r = parseM3U(t);
      L.account = null;
      L.items = { live: r.live, movie: r.movie, series: [] };
      L.groups = { live: group(r.live, r.liveGroups), movie: group(r.movie, r.movieGroups), series: group([], []) };
    });
  }
  // shortcut: القوائم تنجاب من السيرفر كل تشغيل (localStorage أصغر من القوائم الكبيرة)، لو صارت بطيئة نخزّنها في IndexedDB
  function load(s) {
    L.source = s; L.items = {}; L.groups = {}; L.byNew = {}; L.account = null;
    return (s.kind === 'xtream' ? loadXtream(s) : loadM3U(s)).then(function () { if (L.source !== s) throw new Error('stale'); });
  }

  /* ---------- صفوف الواجهة ---------- */
  // القائمة من الأحدث إضافة (لو السيرفر يعطي التاريخ)، وإلا من آخرها
  function newest(k) {
    var src = L.items[k] || [];
    if (L.byNew[k] && L.byNew[k].length === src.length) return L.byNew[k];
    var l = src.slice(), dated = l.some(function (i) { return i.added > 0; });
    if (dated) l.sort(function (a, b) { return b.added - a.added; }); else l.reverse();
    return (L.byNew[k] = l);
  }
  function head(l, n) { return l.slice(0, n); }
  function seeded(seed) { return function () { seed = (seed + 0x6D2B79F5) | 0; var t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }
  function shuffle(l, rnd) { for (var i = l.length - 1; i > 0; i--) { var j = Math.floor(rnd() * (i + 1)), t = l[i]; l[i] = l[j]; l[j] = t; } return l; }
  // أفضل n: من الأعلى تقييم في آخر 400 فيلم ومسلسل انضافوا، وتتغير كل 3 أيام (10 من أعلى 30)
  function top(n, kinds, now) {
    var pool = [];
    kinds.forEach(function (k) { pool = pool.concat(head(newest(k), 400)); });
    var rnd = seeded(Math.floor((now || Date.now()) / (3 * 864e5)));
    var rated = pool.filter(function (i) { return i.rating; });
    if (rated.length < n) return head(shuffle(head(pool, n * 3), rnd), n);
    var by = function (a, b) { return b.rating - a.rating; };
    rated.sort(by);
    return head(shuffle(head(rated, n * 3), rnd), n).sort(by);
  }
  // لكل تصنيف أحدث per عنصر، بترتيب التصنيفات من السيرفر
  function byGroup(k, per) {
    var out = [], map = {};
    (L.groups[k] || []).forEach(function (g) { if (g.id !== ALL) { var r = { group: g, items: [] }; map[g.id] = r; out.push(r); } });
    newest(k).forEach(function (i) { var r = map[i.group]; if (r && r.items.length < per) r.items.push(i); });
    return out.filter(function (r) { return r.items.length; });
  }
  // يبسّط الاسم للبحث: بدون تشكيل، والألف والتاء المربوطة والياء بشكل واحد
  function norm(s) {
    return String(s || '').toLowerCase().replace(/[ً-ٟـ]/g, '').replace(/[أإآ]/g, 'ا').replace(/ة/g, 'ه').replace(/ى/g, 'ي').trim();
  }
  function filter(k, groupId, query) {
    var q = norm(query);
    return (L.items[k] || []).filter(function (i) {
      if (q) { if (i.search == null) i.search = norm(i.name); return i.search.indexOf(q) >= 0; }
      return groupId === ALL || groupId === i.group;
    });
  }

  /* ---------- الروابط ---------- */
  function streamUrl(i) {
    if (i.url) return i.url;
    var s = L.source, base = s.server + '/', cred = encodeURIComponent(s.user) + '/' + encodeURIComponent(s.pass) + '/';
    if (i.k === LIVE) return base + 'live/' + cred + i.id + '.' + L.ext;
    return base + (i.k === MOVIE ? 'movie/' : 'series/') + cred + i.id + '.' + (i.ext || 'mp4');
  }
  // رابط بديل لو البث ما اشتغل: الصيغة الثانية (HLS ↔ TS)
  function altUrl(i) {
    if (i.k !== LIVE || i.url || !L.source || L.source.kind !== 'xtream') return null;
    var u = streamUrl(i);
    return /\.m3u8$/.test(u) ? u.replace(/\.m3u8$/, '.ts') : u.replace(/\.[^.\/]+$/, '.m3u8');
  }

  /* ---------- التفاصيل (Xtream بس) ---------- */
  function info(i) {
    var s = L.source;
    if (!s || s.kind !== 'xtream') return Promise.resolve(null);
    return fetchJson(api(s, i.k === SERIES ? 'get_series_info' : 'get_vod_info') + (i.k === SERIES ? '&series_id=' : '&vod_id=') + i.id).catch(function () { return null; });
  }
  // "01:45:00" أو "45 min" إلى دقائق
  function minutes(d) {
    d = str(d);
    if (!d) return 0;
    if (d.indexOf(':') >= 0) { var p = d.split(':').map(function (x) { return parseInt(x, 10) || 0; }); return p.length === 3 ? p[0] * 60 + p[1] : p[0]; }
    return parseInt(d.replace(/[^0-9]/g, ''), 10) || 0;
  }
  // القصة والصورة العريضة والنوع والمدة (القصة "" يعني سألنا وما فيه)
  function fill(it, o) {
    if (it.plot == null) it.plot = '';
    var n = o && o.info;
    if (!n || typeof n !== 'object') return;
    var p = str(n.plot || n.description);
    if (p) it.plot = p;
    if (!it.back) it.back = firstUrl(n.backdrop_path);
    if (!it.sub && str(n.genre)) it.sub = str(n.genre);
    if (!it.year) it.year = year(null, n.releasedate || n.releaseDate);
    var m = Math.round((+n.duration_secs || 0) / 60);
    it.mins = m > 0 ? m : minutes(n.duration || n.episode_run_time);
  }
  // اسم الحلقة بدون اسم المسلسل و S01E02 (كثير من السيرفرات تكتبها كذا)
  function episodeTitle(t, series) {
    t = str(t);
    if (series && t.indexOf(series) === 0) t = t.slice(series.length);
    t = t.replace(/^[\s\-–|:]+/, '').replace(/^s\d+\s*e\d+[\s\-–|:]*/i, '').trim();
    return t || null;
  }
  // حلقات المسلسل مرتبة بالمواسم: [{s, eps}]
  function episodes(series, o) {
    var eps = o && o.episodes, all = [];
    var collect = function (a) { (Array.isArray(a) ? a : []).forEach(function (e) { if (e && typeof e === 'object' && !Array.isArray(e)) all.push(e); else if (Array.isArray(e)) collect(e); }); };
    if (Array.isArray(eps)) collect(eps);
    else if (eps && typeof eps === 'object') Object.keys(eps).forEach(function (k) { collect(eps[k]); });
    var by = {};
    all.forEach(function (e) {
      var s = +e.season || 1, n = +e.episode_num || 0, inf = e.info && typeof e.info === 'object' ? e.info : {};
      var img = typeof inf.movie_image === 'string' && inf.movie_image.indexOf('http') === 0 ? inf.movie_image : null;
      var m = Math.round((+inf.duration_secs || 0) / 60);
      (by[s] = by[s] || []).push({
        k: EP, id: String(e.id), ext: e.container_extension || 'mp4', name: series.name, series: series.id, s: s, e: n,
        logo: img || series.back || series.logo, still: !!img, ep: episodeTitle(e.title, series.name),
        mins: m > 0 ? m : minutes(inf.duration), plot: str(inf.plot) || null,
      });
    });
    return Object.keys(by).map(Number).sort(function (a, b) { return a - b; })
      .map(function (s) { return { s: s, eps: by[s].sort(function (a, b) { return a.e - b.e; }) }; });
  }
  function b64utf8(s) { try { return decodeURIComponent(escape(atob(s))); } catch (e) { return ''; } }
  // البرنامج الحالي واللي بعده للقناة
  function epg(ch) {
    var s = L.source;
    if (!s || s.kind !== 'xtream' || ch.url) return Promise.resolve([]);
    return fetchJson(api(s, 'get_short_epg') + '&stream_id=' + ch.id + '&limit=3').then(function (o) {
      var now = Date.now();
      return ((o && o.epg_listings) || []).map(function (e) { return { t: b64utf8(e.title).trim(), a: +e.start_timestamp * 1000, z: +e.stop_timestamp * 1000 }; })
        .filter(function (p) { return p.t && !(p.z > 0 && p.z < now); }).slice(0, 2);
    }).catch(function () { return []; });
  }

  /* ---------- المفضلة ووين وقفت (لكل اشتراك) ---------- */
  var FIELDS = ['k', 'id', 'name', 'logo', 'back', 'group', 'num', 'ext', 'rating', 'year', 'sub', 'url', 'mins', 'series', 's', 'e', 'ep', 'still'];
  function slim(i) { var o = {}; FIELDS.forEach(function (f) { if (i[f] != null) o[f] = i[f]; }); return o; }
  function key(i) { return i.k + ':' + i.id; }
  function sid() { return L.source ? L.source.id : '-'; }
  function favs(k) { return get('fav.' + sid(), []).filter(function (i) { return !k || i.k === k; }); }
  function isFav(i) { return favs().some(function (f) { return key(f) === key(i); }); }
  function toggleFav(i) {
    var l = favs(), on = !isFav(i);
    put('fav.' + sid(), on ? [slim(i)].concat(l) : l.filter(function (f) { return key(f) !== key(i); }));
    return on;
  }
  function recents() { return get('recent.' + sid(), []); }
  function resumeAt(i) { var r = recents().filter(function (x) { return key(x) === key(i); })[0]; return r ? r.pos : 0; }
  // آخر حلقة شافها من المسلسل
  function lastEpisode(series) { return recents().filter(function (x) { return x.k === EP && x.series === series.id; })[0] || null; }
  // نحفظ وين وقف (بالثواني). لو خلّص الفيديو تقريبا، يطلع من القائمة
  function watched(i, pos, dur) {
    if (i.k === LIVE || !L.source) return;
    var out = [], done = dur > 0 && pos > dur * 0.95;
    if (!done && pos > 15) { var c = slim(i); c.pos = pos; c.dur = dur; out.push(c); }
    recents().forEach(function (r) {
      // من المسلسل نفسه نخلي آخر حلقة بس
      var same = key(r) === key(i) || (i.k === EP && r.k === EP && r.series === i.series && !done);
      if (!same && out.length < 40) out.push(r);
    });
    put('recent.' + sid(), out);
  }
  function forget(i) { put('recent.' + sid(), recents().filter(function (r) { return key(r) !== key(i); })); }

  return {
    LIVE: LIVE, MOVIE: MOVIE, SERIES: SERIES, EP: EP, ALL: ALL, L: L, get: get, put: put,
    cleanServer: cleanServer, xtream: xtream, fromLink: fromLink, sourceTitle: sourceTitle, host: host,
    sources: sources, active: active, addSource: addSource, useSource: useSource, removeSource: removeSource,
    load: load, parseM3U: parseM3U, title: title, isVod: isVod, rating: rating, year: year, group: group,
    newest: newest, head: head, top: top, byGroup: byGroup, norm: norm, filter: filter,
    streamUrl: streamUrl, altUrl: altUrl, info: info, fill: fill, episodes: episodes, episodeTitle: episodeTitle, minutes: minutes, epg: epg,
    key: key, favs: favs, isFav: isFav, toggleFav: toggleFav, recents: recents, resumeAt: resumeAt, lastEpisode: lastEpisode, watched: watched, forget: forget,
  };
})();
if (typeof module !== 'undefined') module.exports = D;
