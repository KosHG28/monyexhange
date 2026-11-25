"""Fresh accessibility snapshots with a bounded retry while Android attaches a window."""
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET


def read_tree(adb, path):
    for attempt in range(5):
        # A failed dump must never silently reuse the preceding screen's XML.
        adb('shell', 'rm', '-f', path)
        try:
            adb('shell', 'uiautomator', 'dump', path)
        except subprocess.CalledProcessError:
            # Android can exit with code 255 while a dialog is replacing the active window.
            if attempt == 4:
                raise
        else:
            ready = adb('shell', 'if test -s '+shlex.quote(path)+'; then echo ready; fi').strip()
            if ready == 'ready':
                return ET.fromstring(adb('exec-out', 'cat', path))
        if attempt < 4:
            time.sleep(0.3)
    raise AssertionError('Android returned no accessibility root after five attempts: '+path)
