"""
Generate the two Play Store graphics that have no source file in the repo.

The app icon is reproduced from the actual launcher vectors
(res/drawable/ic_launcher_background.xml and ic_launcher_foreground.xml) at
their exact proportions, so the Store icon and the home-screen icon are the
same mark rather than two drawings that happen to look similar.
"""
import os
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
os.makedirs(OUT, exist_ok=True)

# Straight from the app's palette (ui/theme/Color.kt and the icon vectors).
ICON_BG = (0xCF, 0xE9, 0xE6)      # PrimaryContainerLight
PRIMARY = (0x1F, 0x6F, 0x6B)      # Primary
PRIMARY_DARK = (0x7F, 0xD3, 0xCB)  # PrimaryDarkMode
INK = (0x0E, 0x2B, 0x2A)
TEXT = (0xE8, 0xEC, 0xEB)
MUTED = (0xA8, 0xB4, 0xB2)

# The launcher vector is a 108x108 viewport: a filled square, and a ring
# centred at (54,54) with outer radius 24 and inner radius 12.
VIEWPORT = 108.0
RING_CX = RING_CY = 54.0
RING_OUTER = 24.0
RING_INNER = 12.0

SS = 4  # supersample factor, for clean circle edges


def draw_ring(draw, cx, cy, outer, inner, colour):
    draw.ellipse([cx - outer, cy - outer, cx + outer, cy + outer], fill=colour)
    return inner


def app_icon(size=512):
    s = size * SS
    img = Image.new("RGB", (s, s), ICON_BG)
    d = ImageDraw.Draw(img)
    k = s / VIEWPORT
    cx, cy = RING_CX * k, RING_CY * k
    outer, inner = RING_OUTER * k, RING_INNER * k
    d.ellipse([cx - outer, cy - outer, cx + outer, cy + outer], fill=PRIMARY)
    # Punch the hole with the background colour, exactly as the vector's
    # second subpath does.
    d.ellipse([cx - inner, cy - inner, cx + inner, cy + inner], fill=ICON_BG)
    img = img.resize((size, size), Image.LANCZOS)
    path = os.path.join(OUT, "app-icon-512.png")
    img.save(path, "PNG")
    return path


def font(name, size):
    for candidate in (name, "segoeui.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(r"C:\Windows\Fonts\%s" % candidate, size)
        except OSError:
            continue
    return ImageFont.load_default()


def feature_graphic(w=1024, h=500):
    s = SS
    img = Image.new("RGB", (w * s, h * s), INK)
    d = ImageDraw.Draw(img)

    # Soft horizontal gradient, dark on the left so the mark reads clearly.
    for x in range(w * s):
        t = x / float(w * s)
        r = int(INK[0] + (PRIMARY[0] - INK[0]) * t * 0.85)
        g = int(INK[1] + (PRIMARY[1] - INK[1]) * t * 0.85)
        b = int(INK[2] + (PRIMARY[2] - INK[2]) * t * 0.85)
        d.line([(x, 0), (x, h * s)], fill=(r, g, b))

    # The same ring mark as the icon, at the left.
    cx, cy = 168 * s, (h // 2) * s
    outer, inner = 86 * s, 43 * s
    d.ellipse([cx - outer, cy - outer, cx + outer, cy + outer], fill=PRIMARY_DARK)
    # Re-sample the gradient behind the hole so it blends instead of showing a
    # flat patch.
    hole = img.crop((int(cx - inner), int(cy - inner), int(cx + inner), int(cy + inner)))
    base = Image.new("RGB", hole.size, (0, 0, 0))
    bx = int(cx - inner)
    bd = ImageDraw.Draw(base)
    for x in range(base.width):
        t = (bx + x) / float(w * s)
        r = int(INK[0] + (PRIMARY[0] - INK[0]) * t * 0.85)
        g = int(INK[1] + (PRIMARY[1] - INK[1]) * t * 0.85)
        b = int(INK[2] + (PRIMARY[2] - INK[2]) * t * 0.85)
        bd.line([(x, 0), (x, base.height)], fill=(r, g, b))
    mask = Image.new("L", base.size, 0)
    ImageDraw.Draw(mask).ellipse([0, 0, base.size[0], base.size[1]], fill=255)
    img.paste(base, (int(cx - inner), int(cy - inner)), mask)

    title = font("segoeuib.ttf", 84 * s)
    sub = font("segoeui.ttf", 36 * s)
    d.text((330 * s, 192 * s), "FocusFlow", font=title, fill=TEXT)
    d.text((334 * s, 292 * s), "See what holds your attention", font=sub, fill=MUTED)

    img = img.resize((w, h), Image.LANCZOS)
    path = os.path.join(OUT, "feature-graphic-1024x500.png")
    img.save(path, "PNG")
    return path


print(app_icon())
print(feature_graphic())
