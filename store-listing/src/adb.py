"""Small adb helpers for driving the app and capturing screens."""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = str(Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk/platform-tools/adb.exe")
PKG = "com.satya.a12andriodtester"


def sh(*args, check=True):
    return subprocess.run([ADB, "shell", *args], capture_output=True, text=True, encoding="utf-8", errors="replace", check=check).stdout


def screencap(out):
    out = Path(out)
    out.parent.mkdir(parents=True, exist_ok=True)
    data = subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True, check=True).stdout
    out.write_bytes(data)
    return out


def nodes():
    sh("uiautomator", "dump", "/sdcard/ui.xml")
    xml = subprocess.run([ADB, "exec-out", "cat", "/sdcard/ui.xml"], capture_output=True, check=True).stdout
    for n in ET.fromstring(xml).iter("node"):
        b = list(map(int, re.findall(r"\d+", n.get("bounds", "[0,0][0,0]"))))
        yield n.get("text", ""), n.get("content-desc", ""), n.get("resource-id", ""), b


def find(text, exact=False):
    for t, d, _, b in nodes():
        for s in (t, d):
            if s and ((s == text) if exact else (text.lower() in s.lower())):
                return (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
    return None


def tap(x, y=None, wait=1.2):
    if y is None:
        x, y = x
    sh("input", "tap", str(x), str(y))
    time.sleep(wait)


def tap_text(text, exact=False, wait=1.5, tries=4):
    for _ in range(tries):
        p = find(text, exact)
        if p:
            tap(p, wait=wait)
            return p
        time.sleep(1)
    raise RuntimeError(f"not on screen: {text!r}")


def type_text(s):
    sh("input", "text", s.replace(" ", "%s").replace("#", "\\#").replace("&", "\\&"))


def back(wait=1.2):
    sh("input", "keyevent", "4")
    time.sleep(wait)


def swipe(x1, y1, x2, y2, ms=300, wait=1.0):
    sh("input", "swipe", *map(str, (x1, y1, x2, y2, ms)))
    time.sleep(wait)


def texts():
    return [t or d for t, d, _, _ in nodes() if t or d]


def launch(clear=False):
    if clear:
        sh("am", "force-stop", PKG)
    sh("monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(3)


def demo_mode(on=True):
    if on:
        sh("settings", "put", "global", "sysui_demo_allowed", "1")
        for cmd in (["enter"], ["clock", "-e", "hhmm", "0941"],
                    ["battery", "-e", "level", "100", "-e", "plugged", "false"],
                    ["network", "-e", "wifi", "show", "-e", "level", "4"],
                    ["network", "-e", "mobile", "show", "-e", "datatype", "none", "-e", "level", "4"],
                    ["notifications", "-e", "visible", "false"]):
            args = ["am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", cmd[0], *cmd[1:]]
            sh(*args)
    else:
        sh("am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "exit")
        sh("settings", "put", "global", "sysui_demo_allowed", "0")


def type_safe(s):
    """Types text, sending '.' as a key event so the keyboard doesn't auto-insert a space after it."""
    for i, part in enumerate(s.split(".")):
        if i:
            sh("input", "keyevent", "56")
        if part:
            type_text(part)
