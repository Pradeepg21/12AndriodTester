"""Builds Play Store screenshots from raw phone captures in store-listing/raw.

1. Cleans each capture: repaints the status bar (removes notification icons) and crops the nav bar.
2. Lays them out with a headline on a brand background and renders with headless Chrome:
   phone 1080x1920, 7-inch tablet 1920x1080, 10-inch tablet 2560x1440.
Run from anywhere: python store-listing/src/make_screens.py
"""
from pathlib import Path

from PIL import Image

from render import render

SRC = Path(__file__).resolve().parent
RAW = SRC.parent / "raw"
OUT = SRC.parent / "out"
BUILD = SRC / "_build"
STATUS_H, NAV_TOP = 121, 2274  # Pixel 8a insets (dumpsys window)

# (raw capture, headline, sub-headline, dark background?)
SLIDES = [
    ("welcome-1", "Not a tester swap<br><em>12 real testers</em>", "Bugs, crashes and UX feedback from a real 14-day test", True),
    ("home-inprogress", "Every app<br><em>tracked live</em>", "Run several closed tests at once", False),
    ("detail-top", "<em>Day 9 of 14</em><br>at a glance", "Clear status and what to do next", True),
    ("form", "Submit in<br><em>two minutes</em>", "App name, package and opt-in link — done", False),
    ("detail-progress", "Every step<br><em>crystal clear</em>", "From payment to final report", True),
    ("chat", "Real people<br><em>Replies in 4 hours</em>", "Chat with support on every request", False),
]


def clean(name):
    im = Image.open(RAW / f"{name}.png").convert("RGB")
    w = im.width
    band = im.crop((0, STATUS_H + 2, w, STATUS_H + 3)).resize((w, STATUS_H))
    im.paste(band, (0, 0))
    out = BUILD / f"{name}.png"
    im.crop((0, 0, w, NAV_TOP)).save(out)
    return out


STATUS_SVG = """<div class="sbrow"><span>9:41</span><svg viewBox="0 0 66 16" height="15"><g fill="currentColor">
<path d="M8 13.5 0 5.2a11.3 11.3 0 0 1 16 0z"/><path d="M22 14h3V3h-3zm-5 0h3V6h-3zm10 0h3V0h-3z" transform="translate(4 1)"/>
<rect x="42" y="2" width="21" height="12" rx="3" fill="none" stroke="currentColor" stroke-width="1.6"/>
<rect x="44.5" y="4.5" width="16" height="7" rx="1.5"/><rect x="64" y="6" width="2" height="4" rx="1"/></g></svg></div>"""

CSS = """
* { box-sizing: border-box; margin: 0; }
body { width: %(w)dpx; height: %(h)dpx; overflow: hidden; font-family: "Plus Jakarta Sans", "Segoe UI", sans-serif; position: relative; }
.bg-dark { background: linear-gradient(150deg, #4F46E5 0%%, #7C3AED 50%%, #DB2777 100%%); color: #fff; }
.bg-light { background: linear-gradient(160deg, #EEF2FF 0%%, #F5F3FF 55%%, #FDF2F8 100%%); color: #1E1B4B; }
.blob { position: absolute; border-radius: 50%%; filter: blur(70px); }
.bg-dark .blob { background: #22D3EE; opacity: .35; }
.bg-light .blob { background: #C4B5FD; opacity: .55; }
h1 { font-weight: 800; letter-spacing: -.02em; line-height: 1.08; }
h1 em { font-style: normal; }
.bg-dark h1 em { color: #FDE68A; }
.bg-light h1 em { background: linear-gradient(90deg, #4F46E5, #C026D3); -webkit-background-clip: text; color: transparent; }
p.sub { font-weight: 500; opacity: .85; margin-top: .6em; }
.phone { position: absolute; background: #0F0B1E; padding: %(bez)dpx; border-radius: %(rad)dpx;
  box-shadow: 0 40px 90px rgba(30, 10, 80, .35), 0 0 0 2px rgba(255,255,255,.08) inset; }
.screen { position: relative; border-radius: %(irad)dpx; overflow: hidden; }
.screen img { display: block; width: 100%%; }
.sb { position: absolute; left: 0; right: 0; top: 0; display: flex; justify-content: space-between; align-items: center;
  padding: 0 7%%; color: #fff; font: 600 %(sbf)dpx "Segoe UI", sans-serif; }
.sbrow { display: flex; width: 100%%; justify-content: space-between; align-items: center; }
.light-top .sb { color: #1E1B4B; }
.cam { position: absolute; top: 2.2%%; left: 50%%; width: 3.2%%; aspect-ratio: 1; border-radius: 50%%; background: #0F0B1E; transform: translateX(-50%%); }
"""

HEAD = '<!doctype html><html><head><meta charset="utf-8"><link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@500;700;800&display=block" rel="stylesheet">'


def phone_html(img, width, left, top):
    bez = round(width * 0.028)
    inner = width - 2 * bez
    sb_h = inner * STATUS_H / 1080
    return (f'<div class="phone" style="left:{left}px;top:{top}px;width:{width}px">'
            f'<div class="screen"><img src="{img.name}"><div class="sb" style="height:{sb_h:.1f}px">{STATUS_SVG}</div>'
            f'<div class="cam"></div></div></div>').replace('<div class="sb" style', '<div class="sbwrap" style', 1) \
        .replace('<div class="sbwrap" style', '<div class="sb" style', 1)


def css(w, h, phone_w):
    bez = round(phone_w * 0.028)
    return "<style>" + CSS % dict(w=w, h=h, bez=bez, rad=round(phone_w * 0.12), irad=round(phone_w * 0.12) - bez,
                                  sbf=round((phone_w - 2 * bez) * 34 / 1080)) + "</style>"


def status_bar_dark(img):
    """True when the screen top is light, so status bar text should be dark."""
    px = Image.open(img).convert("L").crop((0, 0, 1080, STATUS_H)).resize((1, 1)).getpixel((0, 0))
    return px > 170


def build_phone(i, name, title, sub, dark, img):
    W, H, PW = 1080, 1920, 760
    left = (W - PW) // 2
    cls = "bg-dark" if dark else "bg-light"
    html = (HEAD + css(W, H, PW) + f'</head><body class="{cls}">'
            f'<div class="blob" style="width:700px;height:700px;left:-260px;top:-260px"></div>'
            f'<div class="blob" style="width:600px;height:600px;right:-240px;bottom:200px;background:#F472B6;opacity:.3"></div>'
            f'<div style="position:absolute;left:80px;right:80px;top:110px;text-align:center">'
            f'<h1 style="font-size:84px">{title}</h1><p class="sub" style="font-size:38px">{sub}</p></div>'
            f'<div class="{"light-top" if status_bar_dark(img) else ""}">{phone_html(img, PW, left, 560)}</div>'
            '</body></html>')
    page = BUILD / f"phone-{i}.html"
    page.write_text(html, encoding="utf-8")
    render(page, OUT / "phone" / f"{i:02d}-{name}.png", W, H, opaque=True)


def build_tablet(i, group, size_name, W, H):
    """Two phones side by side with the first slide's headline on the left."""
    name, title, sub, dark, _ = group[0]
    PW = round(H * 0.38)
    cls = "bg-dark" if dark else "bg-light"
    k = W / 1920
    phones = ""
    for j, (_, _, _, _, img) in enumerate(group):
        left = round(W * 0.43 + j * (PW + W * 0.035))
        top = round(H * 0.09)
        phones += f'<div class="{"light-top" if status_bar_dark(img) else ""}" style="position:relative;z-index:{2 - j}">{phone_html(img, PW, left, top)}</div>'
    html = (HEAD + css(W, H, PW) + f'</head><body class="{cls}">'
            f'<div class="blob" style="width:{900*k}px;height:{900*k}px;left:{-300*k}px;top:{-300*k}px"></div>'
            f'<div style="position:absolute;left:{110*k}px;width:{W*0.33}px;top:50%;transform:translateY(-50%)">'
            f'<h1 style="font-size:{96*k}px">{title}</h1><p class="sub" style="font-size:{40*k}px">{sub}</p></div>'
            f'{phones}</body></html>')
    page = BUILD / f"tablet-{size_name}-{i}.html"
    page.write_text(html, encoding="utf-8")
    render(page, OUT / f"tablet-{size_name}" / f"{i:02d}-{name}.png", W, H, opaque=True)


def main():
    BUILD.mkdir(exist_ok=True)
    slides = []
    for i, (name, title, sub, dark) in enumerate(SLIDES, 1):
        img = clean(name)
        slides.append((name, title, sub, dark, img))
        build_phone(i, name, title, sub, dark, img)
        print("phone", i, name)
    groups = [[slides[0], slides[1]], [slides[2], slides[4]], [slides[3], slides[0]], [slides[5], slides[2]]]
    for i, g in enumerate(groups, 1):
        build_tablet(i, g, "7in", 1920, 1080)
        build_tablet(i, g, "10in", 2560, 1440)
        print("tablet", i)


if __name__ == "__main__":
    main()
