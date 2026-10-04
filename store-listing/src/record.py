"""Records the promo-video clips on a connected phone signed in as the demo user (see seed_demo.mjs).

Run from store-listing/src: python record.py   -> store-listing/raw/clip-*.mp4
"""
import subprocess
import time
from pathlib import Path

import adb
import fill

RAW = Path(__file__).resolve().parent.parent / "raw"


class Recording:
    def __init__(self, name, seconds):
        self.name, self.seconds = name, seconds

    def __enter__(self):
        self.p = subprocess.Popen([adb.ADB, "shell", "screenrecord", "--bit-rate", "16000000",
                                   "--time-limit", str(self.seconds), f"/sdcard/{self.name}.mp4"])
        time.sleep(1.0)

    def __exit__(self, *exc):
        self.p.wait()
        time.sleep(1.0)
        subprocess.run([adb.ADB, "pull", f"/sdcard/{self.name}.mp4", str(RAW / f"{self.name}.mp4")], check=True,
                       capture_output=True)
        adb.sh("rm", f"/sdcard/{self.name}.mp4")


def home():
    adb.launch(clear=True)
    time.sleep(2)


def tab(label):
    b = max((b for t, _, _, b in adb.nodes() if t == label), key=lambda b: b[1])
    adb.tap((b[0] + b[2]) // 2, (b[1] + b[3]) // 2, wait=1.5)


def main():
    adb.demo_mode(True)

    home()
    adb.tap_text("Help", exact=True, wait=1.5)
    adb.tap_text("Open welcome guide", wait=1.5)
    with Recording("clip-welcome", 11):
        time.sleep(2.5)
        for _ in range(3):
            adb.swipe(950, 1300, 150, 1300, ms=350, wait=2.3)

    home()
    with Recording("clip-track", 12):
        time.sleep(1.2)
        tab("In Progress")
        time.sleep(0.8)
        adb.tap_text("FitTrack", wait=2.2)
        adb.swipe(540, 1900, 540, 1100, ms=900, wait=1.5)
        adb.swipe(540, 1900, 540, 1300, ms=900, wait=1.5)

    adb.swipe(540, 1000, 540, 1900, ms=400, wait=0.3)
    adb.swipe(540, 1000, 540, 1900, ms=400, wait=0.8)
    chat = adb.find("Chat", exact=True)  # look up before recording: a UI dump takes seconds
    with Recording("clip-chat", 8):
        time.sleep(1.2)
        adb.tap(chat, wait=2.5)
        adb.swipe(540, 1100, 540, 1500, ms=900, wait=1.0)
        adb.swipe(540, 1500, 540, 1100, ms=900, wait=0.5)

    home()
    with Recording("clip-form", 11):
        time.sleep(1.0)
        adb.tap(800, 2230, wait=1.8)
        fill.fill(3, "Habit Garden")
        fill.fill(4, "com.riyasharma.habitgarden")
        fill.fill(5, "https://play.google.com/apps/testing/com.riyasharma.habitgarden")
        time.sleep(1.5)
    adb.back()


if __name__ == "__main__":
    main()
