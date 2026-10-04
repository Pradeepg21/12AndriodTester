"""Renders an HTML/SVG file to an exact-size PNG with headless Chrome.

Usage: python render.py <input.html|svg> <output.png> <width> <height> [--opaque]
"""
import subprocess
import sys
from pathlib import Path

CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"


def render(src, out, w, h, opaque=False):
    src, out = Path(src).resolve(), Path(out).resolve()
    out.parent.mkdir(parents=True, exist_ok=True)
    args = [CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
            f"--window-size={w},{h}", f"--screenshot={out}", "--virtual-time-budget=4000",
            "--allow-file-access-from-files"]
    if not opaque:
        args.append("--default-background-color=00000000")
    subprocess.run(args + [src.as_uri()], check=True, capture_output=True)
    if opaque:  # Play rejects alpha on feature graphics / screenshots
        from PIL import Image
        Image.open(out).convert("RGB").save(out)
    return out


if __name__ == "__main__":
    a = sys.argv[1:]
    print(render(a[0], a[1], int(a[2]), int(a[3]), "--opaque" in a))
