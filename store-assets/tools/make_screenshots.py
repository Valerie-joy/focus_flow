"""
Turn raw 1080x2400 emulator captures into Play-compliant 1080x1920 files.

Play states a 16:9 or 9:16 aspect ratio. The emulator panel is 1080x2400
(9:20) — taller than 9:16 — so each capture is scaled to fit the 1920 height
and the leftover width is filled with the screen's own background colour,
sampled from its top-left pixel. Nothing is cropped: padding keeps the whole
screen visible, where a centre-crop would cut the app bar off the top and the
primary action off the bottom.
"""
import os
import sys
from PIL import Image

# Pass the folder of raw `adb exec-out screencap -p` captures as argv[1].
SRC = sys.argv[1] if len(sys.argv) > 1 else "raw-shots"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "screenshots")
TARGET = (1080, 1920)

os.makedirs(OUT, exist_ok=True)

for name in sorted(os.listdir(SRC)):
    if not name.endswith(".png"):
        continue
    im = Image.open(os.path.join(SRC, name)).convert("RGB")
    scale = min(TARGET[0] / im.width, TARGET[1] / im.height)
    new = (max(1, int(im.width * scale)), max(1, int(im.height * scale)))
    im_s = im.resize(new, Image.LANCZOS)

    # The app's own background, so the padding reads as part of the screen
    # rather than as a letterbox.
    bg = im.getpixel((4, 4))
    canvas = Image.new("RGB", TARGET, bg)
    canvas.paste(im_s, ((TARGET[0] - new[0]) // 2, (TARGET[1] - new[1]) // 2))

    dest = os.path.join(OUT, name)
    canvas.save(dest, "PNG", optimize=True)
    kb = os.path.getsize(dest) / 1024
    print("%-28s %s  bg=%s  %4.0f KB" % (name, "x".join(map(str, TARGET)), bg, kb))
