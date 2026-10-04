import subprocess, re, time, xml.etree.ElementTree as ET
import adb

def edits():
    adb.sh("uiautomator", "dump", "/sdcard/ui.xml")
    x = subprocess.run([adb.ADB, "exec-out", "cat", "/sdcard/ui.xml"], capture_output=True).stdout
    return [list(map(int, re.findall(r"\d+", n.get("bounds")))) for n in ET.fromstring(x).iter("node")
            if "EditText" in n.get("class", "")]

def fill(i, s):
    b = edits()[i]
    adb.tap((b[0] + b[2]) // 2, (b[1] + b[3]) // 2, wait=0.8)
    adb.sh("input", "keycombination", "113", "29"); adb.sh("input", "keyevent", "67")
    adb.type_safe(s); time.sleep(0.5)
    if "mInputShown=true" in adb.sh("dumpsys", "input_method"):
        adb.back(wait=0.8)
