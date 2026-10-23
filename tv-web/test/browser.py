"""
تجربة نسخة التلفزيون في متصفح 1920x1080 ضد سيرفر التجربة، بأزرار الكيبورد بدل الريموت (الأسهم، Enter، Backspace).
يشغّل سيرفر التجربة وسيرفر للملفات بنفسه، ويحفظ الصور في tv-web/build/screens.

    python -X utf8 tv-web/test/browser.py

يحتاج playwright ومتصفح Chrome (كروميوم حق playwright ما يشغّل H.264).
"""
import subprocess
import sys
import time
from pathlib import Path

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[2]
WEB, OUT = ROOT / "tv-web", ROOT / "tv-web/build/screens"
MOCK, HTTP = 8897, 8772
OUT.mkdir(parents=True, exist_ok=True)
procs = [subprocess.Popen([sys.executable, "-X", "utf8", str(ROOT / "tools/mock-xtream.py"), str(MOCK)], stderr=subprocess.DEVNULL),
         subprocess.Popen([sys.executable, "-m", "http.server", str(HTTP), "--directory", str(WEB)], stderr=subprocess.DEVNULL, stdout=subprocess.DEVNULL)]
time.sleep(1.5)
errors = []


def keys(page, *seq):
    for k in seq:
        page.keyboard.press(k)
        page.wait_for_timeout(250)


def typed(page, text):
    """OK يفتح الخانة، نكتب، وOK يقفلها."""
    keys(page, "Enter")
    page.keyboard.type(text)
    keys(page, "Enter")


def focused(page):
    return page.evaluate("cur && (cur.id || cur.dataset.act || cur.dataset.go || cur.dataset.t || cur.className)")


def until(page, key, cond, n=12):
    """نضغط الزر لين يتحقق الشرط (بحد أقصى n مرات)."""
    for _ in range(n):
        if cond():
            return
        keys(page, key)
    assert cond(), focused(page)


def in_rail(page):
    return page.evaluate("!!(cur && cur.closest('#rail'))")


def shot(page, name):
    page.wait_for_timeout(700)
    page.screenshot(path=str(OUT / f"{name}.png"))
    print("  صورة:", name)


try:
    with sync_playwright() as p:
        b = p.chromium.launch(channel="chrome")
        page = b.new_page(viewport={"width": 1920, "height": 1080})
        page.on("pageerror", lambda e: errors.append(str(e)))
        page.on("response", lambda r: r.status >= 400 and not r.url.endswith("favicon.ico") and errors.append(f"{r.status} {r.url}"))
        page.goto(f"http://127.0.0.1:{HTTP}/")
        page.wait_for_timeout(3300)

        print("1. أول تشغيل: صفحة الاشتراكات، نضيف حساب Xtream بالأسهم وOK")
        assert page.is_visible("#add"), "ما طلعت صفحة الاشتراكات"
        assert focused(page) == "fServer"
        typed(page, f"127.0.0.1:{MOCK}")
        keys(page, "ArrowDown")
        typed(page, "demo")
        keys(page, "ArrowLeft")
        typed(page, "demo")
        keys(page, "ArrowDown", "ArrowDown")
        assert focused(page) == "saveBtn", focused(page)
        keys(page, "Enter")
        page.wait_for_selector("#browse.on #rows .row", timeout=15000)
        assert page.inner_text("#info h1"), "الواجهة فاضية"
        shot(page, "1-home")

        print("2. الصفوف: نزول لصف المسلسلات")
        keys(page, "ArrowDown")
        shot(page, "2-home-rows")
        rows = page.eval_on_selector_all("#rows .row h2", "e => e.map(x => x.textContent)")
        assert "أفضل 10 في شاشة" in rows and "مسلسلات جديدة" in rows, rows
        keys(page, *["ArrowDown"] * rows.index("مسلسلات جديدة"))
        keys(page, "ArrowLeft")

        print("3. صفحة المسلسل والحلقات")
        keys(page, "Enter")
        page.wait_for_selector("#details.on #eps .card", timeout=15000)
        shot(page, "3-details")

        print("4. المشغّل: أول حلقة")
        keys(page, "Enter")
        page.wait_for_function("video.currentTime > 1", timeout=30000)
        shot(page, "4-player")
        keys(page, "ArrowUp")  # لو الأزرار اختفت (بعد 6 ثواني)، أول زر يرجّعها
        assert focused(page) == "pause", focused(page)
        keys(page, "Enter")
        assert page.evaluate("video.paused"), "OK على الإيقاف ما وقّف"
        keys(page, "Enter")

        print("5. آخر 3 دقائق: بطاقة الحلقة التالية")
        page.evaluate("video.currentTime = video.duration - 170")
        page.wait_for_selector("#cNext.show", timeout=10000)
        assert focused(page) == "nextnow"
        shot(page, "5-next-episode")
        keys(page, "Backspace")
        assert not page.is_visible("#cNext.show")

        print("6. الرجوع يحفظ وين وقفت")
        keys(page, "Backspace")
        page.wait_for_selector("#details.on")
        assert "كمّل الحلقة" in page.inner_text("#dinfo .actions"), page.inner_text("#dinfo .actions")
        keys(page, "Backspace")
        page.wait_for_selector("#browse.on")
        assert "كمّل المشاهدة" in page.inner_text("#rows")

        print("7. المباشر من الشريط الجانبي")
        keys(page, "Backspace")
        until(page, "ArrowRight", lambda: in_rail(page))
        until(page, "ArrowDown", lambda: focused(page) == "live")
        keys(page, "Enter")
        page.wait_for_selector("#live.on #chs .ch.f")
        keys(page, "ArrowDown", "ArrowDown")
        page.wait_for_timeout(800)
        shot(page, "6-live")
        keys(page, "Enter")
        page.wait_for_function("video.currentTime > 1", timeout=30000)
        shot(page, "7-player-live")
        keys(page, "PageUp")
        page.wait_for_function("video.currentTime > 1", timeout=30000)
        keys(page, "Backspace")
        page.wait_for_selector("#live.on")

        print("8. اشتراك M3U")
        until(page, "ArrowRight", lambda: in_rail(page))
        until(page, "ArrowDown", lambda: focused(page) == "add")
        keys(page, "Enter")
        page.wait_for_selector("#add.on")
        keys(page, "ArrowUp")
        until(page, "ArrowLeft", lambda: page.evaluate("cur.dataset.k") == "m")
        keys(page, "Enter", "ArrowDown")
        assert focused(page) == "fUrl", focused(page)
        typed(page, f"http://127.0.0.1:{MOCK}/playlist.m3u")
        keys(page, "ArrowDown", "ArrowDown")
        assert focused(page) == "saveBtn", focused(page)
        keys(page, "Enter")
        page.wait_for_selector("#browse.on #rows .row", timeout=15000)
        m3u_rows = page.eval_on_selector_all("#rows .row h2", "e => e.map(x => x.textContent)")
        assert "أفلام جديدة" in m3u_rows and "القنوات" in m3u_rows, m3u_rows
        shot(page, "8-home-m3u")
        b.close()
finally:
    for pr in procs:
        pr.terminate()

assert not errors, errors
print("كل شي تمام، الصور في", OUT)
