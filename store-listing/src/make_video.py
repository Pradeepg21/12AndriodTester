"""Builds the promo video from the phone clips recorded by record.py.

Outputs store-listing/out/video/promo-1920x1080.mp4 (Play / YouTube) and promo-1080x1920.mp4 (Shorts, WhatsApp).
Every visual is rendered from HTML here; the soundtrack is synthesised by ffmpeg — no third-party media.
Run from store-listing/src: python make_video.py
"""
import subprocess
from pathlib import Path

import imageio_ffmpeg

from render import render

SRC = Path(__file__).resolve().parent
RAW = SRC.parent / "raw"
OUT = SRC.parent / "out" / "video"
BUILD = SRC / "_build" / "video"
FF = imageio_ffmpeg.get_ffmpeg_exe()
FPS = 30
XF = 0.5  # crossfade seconds
STATUS_H, NAV_TOP = 121, 2274

# (kind, clip name or None, clip start, duration, headline, sub)
SCENES = [
    ("intro", None, 0, 3.5, "Twelve Testers", "Not a tester swap. 12 real testers."),
    ("card", None, 0, 3.2, "Need 12 testers<br>for 14 days?", "Skip the tester swaps and group requests. We bring the testers."),
    ("clip", "clip-welcome", 1.5, 8.5, "Real testing<br><em>real feedback</em>", "Testers who actually use your app — no favours"),
    ("clip", "clip-form", 0.8, 9.0, "Submit your app<br><em>in two minutes</em>", "App name, package and opt-in link"),
    ("clip", "clip-track", 1.0, 10.0, "Track every day<br><em>of your 14-day test</em>", "Clear status and what to do next"),
    ("clip", "clip-chat", 0.6, 6.5, "Real people<br><em>replies in 4 hours</em>", "Chat with support on every request"),
    ("outro", None, 0, 4.5, "Real testers.<br>Real feedback.", "Twelve Testers — download now"),
]

FONT = '<link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@500;700;800&display=block" rel="stylesheet">'
BASE_CSS = """* { box-sizing: border-box; margin: 0; }
body { width: %(w)dpx; height: %(h)dpx; overflow: hidden; font-family: "Plus Jakarta Sans", "Segoe UI", sans-serif; position: relative; }
.grad { position: absolute; inset: 0; background: linear-gradient(140deg, #4F46E5 0%%, #7C3AED 50%%, #DB2777 100%%); }
.blob { position: absolute; border-radius: 50%%; filter: blur(90px); }
h1 { color: #fff; font-weight: 800; letter-spacing: -.02em; line-height: 1.06; }
h1 em { font-style: normal; color: #FDE68A; }
p { color: #fff; opacity: .88; font-weight: 500; margin-top: .55em; line-height: 1.3; }
"""


class Layout:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.landscape = w > h
        if self.landscape:
            self.pw = 440
            self.px, self.py = round(w * 0.60), (h - self.phone_h) // 2
        else:
            self.pw = 620
            self.px, self.py = (w - self.pw) // 2, h - self.phone_h - 90

    bez = property(lambda s: round(s.pw * 0.028))
    sw = property(lambda s: s.pw - 2 * s.bez)
    sh = property(lambda s: round(s.sw * NAV_TOP / 1080 / 2) * 2)
    phone_h = property(lambda s: s.sh + 2 * s.bez)
    rad = property(lambda s: round(s.pw * 0.12))
    tag = property(lambda s: f"{s.w}x{s.h}")


def html(lay, body, extra_css=""):
    return (f'<!doctype html><html><head><meta charset="utf-8">{FONT}<style>'
            + BASE_CSS % dict(w=lay.w, h=lay.h) + extra_css + f"</style></head><body>{body}</body></html>")


def page(lay, name, body, extra_css="", opaque=True):
    f = BUILD / f"{name}-{lay.tag}.html"
    f.write_text(html(lay, body, extra_css), encoding="utf-8")
    return render(f, BUILD / f"{name}-{lay.tag}.png", lay.w, lay.h, opaque=opaque)


def background(lay):
    k = lay.w / 1920
    return page(lay, "bg", f'<div class="grad"></div>'
                f'<div class="blob" style="width:{900*k}px;height:{900*k}px;left:{-300*k}px;top:{-350*k}px;background:#22D3EE;opacity:.32"></div>'
                f'<div class="blob" style="width:{800*k}px;height:{800*k}px;right:{-250*k}px;bottom:{-350*k}px;background:#FDE68A;opacity:.25"></div>')


def caption(lay, i, title, sub):
    if lay.landscape:
        box = f"left:130px;width:{lay.px - 230}px;top:50%;transform:translateY(-50%)"
        size, psize = 84, 36
    else:
        box = f"left:80px;right:80px;top:{lay.py // 2}px;transform:translateY(-50%);text-align:center"
        size, psize = 86, 38
    return page(lay, f"cap{i}", f'<div style="position:absolute;{box}"><h1 style="font-size:{size}px">{title}</h1>'
                f'<p style="font-size:{psize}px">{sub}</p></div>', "body{background:transparent}", opaque=False)


def card(lay, i, kind, title, sub):
    big = kind != "card"
    icon = f'<img src="../../icon-rounded.svg" style="width:{180 if big else 0}px;border-radius:40px;box-shadow:0 20px 60px rgba(30,0,80,.4);margin-bottom:40px">' if big else ""
    size = 110 if kind == "intro" else 96
    pill = '<div style="display:inline-block;margin-top:40px;padding:14px 30px;border-radius:99px;background:#fff;color:#4F46E5;font-weight:800;font-size:34px">Get it on your phone today</div>' if kind == "outro" else ""
    return page(lay, f"card{i}", f'<div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;padding:0 90px">'
                f'{icon}<h1 style="font-size:{size}px">{title}</h1><p style="font-size:42px">{sub}</p>{pill}</div>',
                "body{background:transparent}", opaque=False)


def phone_frame(lay):
    """Bezel with a transparent screen hole, plus a clean status bar; and an alpha mask of the phone outline."""
    sb_h = lay.sw * STATUS_H / 1080
    fs = round(lay.sw * 34 / 1080)
    frame = (f'<div style="position:absolute;left:0;top:0;width:{lay.pw}px;height:{lay.phone_h}px;border:{lay.bez}px solid #0F0B1E;'
             f'border-radius:{lay.rad}px;box-shadow:0 0 0 2px rgba(255,255,255,.12)"></div>'
             f'<div style="position:absolute;left:{lay.bez}px;top:{lay.bez}px;width:{lay.sw}px;height:{sb_h:.1f}px;display:flex;'
             f'justify-content:space-between;align-items:center;padding:0 7%;color:#fff;font:600 {fs}px Segoe UI,sans-serif">'
             f'<span>9:41</span><svg viewBox="0 0 66 16" height="{fs * 0.9:.0f}"><g fill="#fff"><path d="M8 13.5 0 5.2a11.3 11.3 0 0 1 16 0z"/>'
             f'<path d="M26 15h3V4h-3zm-5 0h3V7h-3zm10 0h3V1h-3z"/><rect x="42" y="2" width="21" height="12" rx="3" fill="none" stroke="#fff" stroke-width="1.6"/>'
             f'<rect x="44.5" y="4.5" width="16" height="7" rx="1.5"/><rect x="64" y="6" width="2" height="4" rx="1"/></g></svg></div>'
             f'<div style="position:absolute;left:50%;top:{lay.bez + sb_h * 0.5:.0f}px;width:{lay.sw * 0.032:.0f}px;aspect-ratio:1;border-radius:50%;background:#0F0B1E;transform:translate(-50%,-50%)"></div>')
    small = Layout.__new__(Layout)
    small.__dict__.update(lay.__dict__)
    small.w, small.h = lay.pw, lay.phone_h
    f = page(small, "frame", frame, "body{background:transparent}", opaque=False)
    m = page(small, "mask", f'<div style="position:absolute;inset:0;background:#fff;border-radius:{lay.rad}px"></div>', "body{background:#000}")
    return f, m


def ff(*args):
    subprocess.run([FF, "-v", "error", "-y", *map(str, args)], check=True)


def scene(lay, i, sc, bg, frame, mask):
    kind, clip, start, dur, title, sub = sc
    out = BUILD / f"scene{i}-{lay.tag}.mp4"
    common = ["-r", FPS, "-c:v", "libx264", "-pix_fmt", "yuv420p", "-preset", "medium", "-crf", 18, "-t", dur, out]
    if kind != "clip":
        txt = card(lay, i, kind, title, sub)
        y = "(H-h)/2+40*pow(1-min(t/0.8\\,1)\\,3)"
        ff("-loop", 1, "-t", dur, "-i", bg, "-loop", 1, "-t", dur, "-i", txt,
           "-filter_complex", f"[1]format=rgba,fade=in:st=0.15:d=0.7:alpha=1[t];[0][t]overlay=0:'{y}'", *common)
        return out
    txt = caption(lay, i, title, sub)
    ease = "pow(1-min(t/0.7\\,1)\\,3)"
    fc = (f"[2]fps={FPS},tpad=stop_mode=clone:stop_duration=4,trim=start={start}:duration={dur},setpts=PTS-STARTPTS,split[a][b];"
          f"[a]crop=1080:2:0:{STATUS_H + 2},scale=1080:{STATUS_H + 1}[sb];[b]crop=1080:{NAV_TOP - STATUS_H - 1}:0:{STATUS_H + 1}[body];"
          f"[sb][body]vstack,scale={lay.sw}:{lay.sh}[scr];"
          f"color=black:s={lay.pw}x{lay.phone_h}:r={FPS}:d={dur}[base];[base][scr]overlay={lay.bez}:{lay.bez}[p1];"
          f"[p1][3]overlay=0:0[p2];[4]format=gray[m];[p2][m]alphamerge[ph];"
          f"[1]format=rgba,fade=in:st=0.2:d=0.6:alpha=1[t];[0][t]overlay=0:0[b1];"
          f"[b1][ph]overlay={lay.px}:'{lay.py}+{lay.h * 0.5:.0f}*{ease}'")
    ff("-loop", 1, "-t", dur, "-i", bg, "-loop", 1, "-t", dur, "-i", txt, "-i", RAW / f"{clip}.mp4",
       "-loop", 1, "-t", dur, "-i", frame, "-loop", 1, "-t", dur, "-i", mask, "-filter_complex", fc, *common)
    return out


def soundtrack(total):
    """Soft C–Am–F–G pad, 4 s per chord, synthesised (royalty-free by construction)."""
    out = BUILD / "music.m4a"
    roots = [(261.63, 329.63, 392.00), (220.00, 261.63, 329.63), (174.61, 220.00, 261.63), (196.00, 246.94, 293.66)]

    def pick(n):
        e = str(roots[3][n])
        for k in (2, 1, 0):
            e = f"if(lt(mod(t\\,16)\\,{4 * (k + 1)})\\,{roots[k][n]}\\,{e})"
        return e

    env = "pow(sin(PI*mod(t\\,4)/4)\\,0.6)"
    voices = "+".join(f"sin(2*PI*{pick(n)}*t)+0.35*sin(4*PI*{pick(n)}*t)" for n in range(3))
    bass = f"0.8*sin(PI*{pick(0)}*t)"
    expr = f"0.07*{env}*({voices}+{bass})"
    ff("-f", "lavfi", "-i", f"aevalsrc='{expr}':s=44100:d={total}",
       "-af", f"aecho=0.8:0.7:120|260:0.25|0.15,lowpass=f=2400,afade=t=in:d=1.5,afade=t=out:st={total - 2.5}:d=2.5",
       "-c:a", "aac", "-b:a", "192k", out)
    return out


def build(w, h):
    lay = Layout(w, h)
    bg = background(lay)
    frame, mask = phone_frame(lay)
    parts = [scene(lay, i, sc, bg, frame, mask) for i, sc in enumerate(SCENES)]
    durs = [sc[3] for sc in SCENES]
    total = sum(durs) - XF * (len(durs) - 1)
    chain, prev, offset = [], "[0]", 0.0
    for i in range(1, len(parts)):
        offset += durs[i - 1] - XF
        label = f"[x{i}]" if i < len(parts) - 1 else "[v]"
        chain.append(f"{prev}[{i}]xfade=transition=fade:duration={XF}:offset={offset:.2f}{label}")
        prev = label
    music = soundtrack(total)
    inputs = [a for p in parts for a in ("-i", p)]
    OUT.mkdir(parents=True, exist_ok=True)
    out = OUT / f"promo-{lay.tag}.mp4"
    ff(*inputs, "-i", music, "-filter_complex", ";".join(chain), "-map", "[v]", "-map", f"{len(parts)}:a",
       "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", 18, "-preset", "slow", "-c:a", "copy", "-shortest",
       "-movflags", "+faststart", out)
    print(out, f"{total:.1f}s")


if __name__ == "__main__":
    BUILD.mkdir(parents=True, exist_ok=True)
    build(1920, 1080)
    build(1080, 1920)
