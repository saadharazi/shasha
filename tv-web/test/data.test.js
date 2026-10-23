// node tv-web/test/data.test.js
const assert = require('assert');
const D = require('../data.js');

// M3U: الاسم بعد الفاصلة برا التنصيص، والأفلام تنعرف من الرابط
const r = D.parseM3U('#EXTM3U\n#EXTINF:-1 tvg-name="a, b" tvg-logo="http://x/l.png" group-title="أخبار",العربية HD\nhttp://s/live/1.ts\n' +
  '#EXTINF:-1 group-title="أفلام",فيلم\nhttp://s/movie/u/p/2.mkv?x=1\n#EXTGRP:منوعات\nhttp://s/live/3.m3u8\n');
assert.strictEqual(r.live.length, 2);
assert.strictEqual(r.live[0].name, 'العربية HD');
assert.strictEqual(r.live[0].logo, 'http://x/l.png');
assert.strictEqual(r.live[1].group, 'منوعات');
assert.strictEqual(r.live[1].name, '3.m3u8');
assert.strictEqual(r.movie[0].k, 'movie');
assert.deepStrictEqual(r.liveGroups.map(g => g.id), ['أخبار', 'منوعات']);
assert.throws(() => D.parseM3U('<html>'), /M3U/);

// الاشتراك: الرابط بأي شكل، ورابط get.php يصير حساب Xtream
assert.strictEqual(D.cleanServer(' example.com:8080/player_api.php?x '), 'http://example.com:8080');
assert.strictEqual(D.cleanServer('https://a.b/'), 'https://a.b');
const x = D.fromLink('a.b:80/get.php?username=u%40&password=p&type=m3u');
assert.deepStrictEqual([x.kind, x.server, x.user, x.pass], ['xtream', 'http://a.b:80', 'u@', 'p']);
assert.strictEqual(D.fromLink('http://a.b/list.m3u').kind, 'm3u');

assert.strictEqual(D.rating('72'), '7.2');
assert.strictEqual(D.rating('0'), null);
assert.strictEqual(D.year(null, '2019-05-01'), '2019');
assert.strictEqual(D.episodeTitle('مدينة الغد - S01E02 - الجسر', 'مدينة الغد'), 'الجسر');
assert.strictEqual(D.episodeTitle('S01E02', ''), null);
assert.strictEqual(D.minutes('01:45:00'), 105);
assert.strictEqual(D.minutes('45 min'), 45);
assert.strictEqual(D.norm('أُسرة'), 'اسره');

// الحلقات: مواسم مرتبة والحلقات بالترتيب
const eps = D.episodes({ id: '9', name: 'س' }, { episodes: { 2: [{ id: 5, season: 2, episode_num: 1 }], 1: [{ id: 2, season: 1, episode_num: 2 }, { id: 1, season: 1, episode_num: 1, info: { duration_secs: 1800 } }] } });
assert.deepStrictEqual(eps.map(s => s.s), [1, 2]);
assert.deepStrictEqual(eps[0].eps.map(e => e.e), [1, 2]);
assert.strictEqual(eps[0].eps[0].mins, 30);

// أفضل 10: ثابتة طول الثلاث أيام، ومرتبة بالتقييم
D.L.items = { movie: Array.from({ length: 50 }, (_, i) => ({ k: 'movie', id: String(i), rating: (i % 10 + 1).toFixed(1), added: i })) };
D.L.byNew = {};
const day = 1.7e12, a = D.top(10, ['movie'], day), b = D.top(10, ['movie'], day + 3600e3);
assert.strictEqual(a.length, 10);
assert.deepStrictEqual(a.map(i => i.id), b.map(i => i.id));
assert.ok(a.every((v, i) => i === 0 || +a[i - 1].rating >= +v.rating));
console.log('data.js ok');
