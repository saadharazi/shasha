"""
سيرفر تجربة يتصرف مثل Xtream Codes وقائمة M3U، عشان تجرب شاشة بدون اشتراك حقيقي.
القنوات والأفلام تحوّل لبثوث تجربة عامة (Big Buck Bunny وغيرها).

    python tools/mock-xtream.py            # يشتغل على المنفذ 8899
    adb reverse tcp:8899 tcp:8899          # التلفزيون يشوفه على 127.0.0.1:8899

في التطبيق: رابط السيرفر http://127.0.0.1:8899، اسم المستخدم demo، كلمة المرور demo.
أو رابط M3U: http://127.0.0.1:8899/playlist.m3u
"""
import base64
import io
import json
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, quote, unquote, urlparse

USER, PASS = "demo", "demo"
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8899
FONT = Path(__file__).resolve().parent.parent / "iptv/app/assets/fonts/Tajawal-Bold.ttf"

HLS = [
    "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
    "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8",
]
# عينات MP4 العامة القديمة صارت ترجع 403، فالأفلام والحلقات HLS بمدة (التقديم والرجوع يشتغلون)
VOD_HLS = HLS

LIVE_CATS = [("1", "أخبار"), ("2", "رياضة"), ("3", "أطفال"), ("4", "وثائقي"), ("5", "World")]
LIVE = [
    ("1", "الإخبارية HD", "NEWS"), ("1", "العربية", "AR"), ("1", "Sky News Arabia", "SKY"), ("1", "الشرق", "ASH"),
    ("1", "BBC Arabic", "BBC"), ("2", "SSC 1 HD", "SSC1"), ("2", "SSC 2 HD", "SSC2"), ("2", "beIN Sports 1", "BEIN"),
    ("2", "الرياضية السعودية", "KSA"), ("2", "AD Sports", "ADS"), ("3", "سبيستون", "SPC"), ("3", "Majid Kids", "MJD"),
    ("3", "MBC 3", "MBC3"), ("3", "براعم", "BR"), ("4", "ناشيونال جيوغرافيك أبوظبي", "NG"), ("4", "الجزيرة الوثائقية", "DOC"),
    ("4", "Discovery", "DSC"), ("5", "France 24", "F24"), ("5", "DW", "DW"), ("5", "NHK World", "NHK"),
    ("1", "قناة بدون شعار", None), ("2", "SSC Extra", None),
]
VOD_CATS = [("10", "أكشن"), ("11", "دراما"), ("12", "عائلي"), ("13", "وثائقي")]
VOD = [
    ("10", "Tears of Steel", 2012, 6.9), ("10", "مهمة الفجر", 2023, 7.4), ("10", "The Last Signal", 2021, 6.1),
    ("10", "صحراء الذئب", 2019, 7.8), ("11", "Sintel", 2010, 7.5), ("11", "رسائل من البحر", 2022, 8.1),
    ("11", "The Quiet Year", 2020, 6.6), ("11", "بيت الجدة", 2018, 7.2), ("12", "Big Buck Bunny", 2008, 6.5),
    ("12", "رحلة الفيل", 2006, 6.3), ("12", "Elephants Dream", 2006, 6.4), ("12", "عيد في الحارة", 2024, 7.0),
    ("13", "أسرار المحيط", 2017, 8.4), ("13", "Mountains", 2021, 7.9), ("13", "الطريق إلى العُلا", 2023, 8.0),
    ("13", "Night Sky", 2019, 7.1),
]
SERIES_CATS = [("20", "مسلسلات عربية"), ("21", "Series")]
SERIES = [
    ("20", "شارع الأعشى", 2025, 8.2, 2, 6), ("20", "ممالك النار", 2019, 8.5, 1, 8), ("20", "طاش", 2011, 7.6, 3, 5),
    ("21", "The Signal", 2022, 7.4, 2, 4), ("21", "Blue Planet", 2017, 9.0, 1, 7), ("21", "Northwind", 2024, 6.8, 1, 3),
]

PALETTE = [(46, 64, 87), (88, 61, 72), (54, 82, 70), (92, 80, 52), (63, 58, 96), (40, 84, 92), (90, 58, 48)]


def image(kind, n, label):
    """شعار أو بوستر PNG بسيط (بالإنجليزي: Pillow بدون raqm ما يشكّل العربي)."""
    from PIL import Image, ImageDraw, ImageFont
    w, h = (240, 150) if kind == "logo" else (300, 450)
    c = PALETTE[n % len(PALETTE)]
    im = Image.new("RGB", (w, h), c)
    d = ImageDraw.Draw(im)
    for y in range(h):  # تدرج هادي
        k = y / h
        d.line([(0, y), (w, y)], fill=tuple(int(v * (1.15 - 0.55 * k)) for v in c))
    f = ImageFont.truetype(str(FONT), 46 if kind == "logo" else 40)
    text = label if kind == "logo" else "\n".join(label.split(" ", 1)) if len(label) > 11 else label
    box = d.multiline_textbbox((0, 0), text, font=f, align="center")
    d.multiline_text(((w - box[2]) / 2, (h - box[3]) / 2 + (0 if kind == "logo" else 70)), text, font=f, fill=(243, 243, 240), align="center")
    out = io.BytesIO()
    im.save(out, "PNG")
    return out.getvalue()


def slug(name, fallback):
    return quote(name if name.isascii() else fallback)


def base(h):
    return "http://" + (h.headers.get("Host") or f"127.0.0.1:{PORT}")


def live_streams(b):
    return [{"num": i + 1, "name": n, "stream_type": "live", "stream_id": 100 + i,
             "stream_icon": f"{b}/img/logo/{i}/{code}.png" if code else "", "epg_channel_id": None,
             "category_id": cat, "tv_archive": 0} for i, (cat, n, code) in enumerate(LIVE)]


def vod_streams(b):
    return [{"num": i + 1, "name": n, "stream_type": "movie", "stream_id": 500 + i,
             "stream_icon": f"{b}/img/poster/{i}/{slug(n, f'Film {i + 1}')}.png", "rating": str(r), "rating_5based": r / 2,
             "added": "1700000000", "category_id": cat, "container_extension": "m3u8", "year": str(y)}
            for i, (cat, n, y, r) in enumerate(VOD)]


def series_list(b):
    return [{"num": i + 1, "name": n, "series_id": 800 + i, "cover": f"{b}/img/poster/{i + 30}/{slug(n, f'Series {i + 1}')}.png",
             "plot": "مسلسل تجربة.", "genre": "دراما، عائلي", "releaseDate": f"{y}-01-01", "rating": str(r),
             "category_id": cat, "backdrop_path": []} for i, (cat, n, y, r, s, e) in enumerate(SERIES)]


class Handler(BaseHTTPRequestHandler):
    def send(self, code, body=b"", ctype="application/json", extra=None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def json(self, o):
        self.send(200, json.dumps(o, ensure_ascii=False).encode("utf-8"))

    def do_GET(self):
        u = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(u.query).items()}
        p = u.path
        b = base(self)
        if p == "/player_api.php":
            if q.get("username") != USER or q.get("password") != PASS:
                return self.json({"user_info": {"auth": 0}})
            a = q.get("action")
            if not a:
                return self.json({"user_info": {"username": USER, "auth": 1, "status": "Active",
                                                "exp_date": str(int(time.time()) + 200 * 86400), "max_connections": "2",
                                                "active_cons": "0", "allowed_output_formats": ["m3u8", "ts"]},
                                  "server_info": {"url": "127.0.0.1", "port": str(PORT)}})
            if a == "get_live_categories":
                return self.json([{"category_id": i, "category_name": n, "parent_id": 0} for i, n in LIVE_CATS])
            if a == "get_vod_categories":
                return self.json([{"category_id": i, "category_name": n, "parent_id": 0} for i, n in VOD_CATS])
            if a == "get_series_categories":
                return self.json([{"category_id": i, "category_name": n, "parent_id": 0} for i, n in SERIES_CATS])
            if a == "get_live_streams":
                return self.json(live_streams(b))
            if a == "get_vod_streams":
                return self.json(vod_streams(b))
            if a == "get_series":
                return self.json(series_list(b))
            if a == "get_vod_info":
                i = int(q.get("vod_id", 500)) - 500
                cat, n, y, r = VOD[i % len(VOD)]
                return self.json({"info": {"name": n, "plot": "فيلم تجربة قصير من مشاريع Blender المفتوحة. القصة هنا عشان نشوف شكل الوصف في الصفحة، وكيف ينقص لما يكون طويل.",
                                           "genre": "مغامرة، خيال", "duration_secs": 596 + i * 60, "releasedate": f"{y}-05-01",
                                           "rating": r, "movie_image": f"{b}/img/poster/{i}/{slug(n, f'Film {i + 1}')}.png", "backdrop_path": []},
                                  "movie_data": {"stream_id": 500 + i, "name": n, "container_extension": "m3u8"}})
            if a == "get_series_info":
                i = int(q.get("series_id", 800)) - 800
                cat, n, y, r, seasons, eps = SERIES[i % len(SERIES)]
                episodes = {}
                for s in range(1, seasons + 1):
                    episodes[str(s)] = [{"id": str(9000 + i * 100 + s * 10 + e), "episode_num": e, "season": s,
                                         "title": f"{n} - S{s:02d}E{e:02d}" if e % 2 else f"الحلقة {e}",
                                         "container_extension": "m3u8",
                                         "info": {"duration_secs": 600 + e * 30, "movie_image": f"{b}/img/poster/{e + 60}/EP%20{e}.png"}}
                                        for e in range(1, eps + 1)]
                return self.json({"seasons": [{"season_number": s} for s in range(1, seasons + 1)],
                                  "info": {"name": n, "plot": "مسلسل تجربة: كل الحلقات تشغّل نفس الفيديو المفتوح.",
                                           "genre": "دراما، عائلي", "releaseDate": f"{y}-01-01", "rating": str(r),
                                           "cover": f"{b}/img/poster/{i + 30}/{slug(n, f'Series {i + 1}')}.png", "backdrop_path": []},
                                  "episodes": episodes})
            if a == "get_short_epg":
                now = int(time.time()) // 3600 * 3600
                t = lambda s: base64.b64encode(s.encode()).decode()
                return self.json({"epg_listings": [
                    {"title": t("نشرة الأخبار"), "start_timestamp": str(now), "stop_timestamp": str(now + 3600)},
                    {"title": t("برنامج الليلة"), "start_timestamp": str(now + 3600), "stop_timestamp": str(now + 7200)}]})
            return self.json([])
        if p == "/playlist.m3u" or p == "/get.php":
            lines = ["#EXTM3U"]
            for s in live_streams(b):
                cat = dict(LIVE_CATS)[s["category_id"]]
                lines += [f'#EXTINF:-1 tvg-id="" tvg-name="{s["name"]}" tvg-logo="{s["stream_icon"]}" group-title="{cat}",{s["name"]}',
                          HLS[s["stream_id"] % len(HLS)]]
            for v in vod_streams(b):
                lines += [f'#EXTINF:-1 tvg-logo="{v["stream_icon"]}" group-title="أفلام",{v["name"]}', f'{b}/movie/{USER}/{PASS}/{v["stream_id"]}.m3u8']
            return self.send(200, "\n".join(lines).encode("utf-8"), "audio/x-mpegurl")
        parts = p.strip("/").split("/")
        if len(parts) == 4 and parts[0] in ("live", "movie", "series"):
            if parts[1] != USER or parts[2] != PASS:
                return self.send(403)
            sid = int(parts[3].split(".")[0])
            target = (HLS if parts[0] == "live" else VOD_HLS)[sid % len(HLS)]
            return self.send(302, extra={"Location": target})
        if len(parts) == 4 and parts[0] == "img":
            kind, n, label = parts[1], int(parts[2]), unquote(parts[3].rsplit(".", 1)[0])
            return self.send(200, image(kind, n, label), "image/png", {"Cache-Control": "max-age=86400"})
        self.send(404, b"{}")

    def log_message(self, fmt, *args):
        sys.stderr.write("%s\n" % (fmt % args))


if __name__ == "__main__":
    print(f"Mock Xtream on http://0.0.0.0:{PORT}  (user {USER} / pass {PASS})")
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
