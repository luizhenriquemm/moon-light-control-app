"""Renders the Google Play graphics from the app's crescent (same path as the launcher icon).

    python tools/store_graphics.py

Writes docs/store/icon-512.png (512x512) and docs/store/feature-graphic.png (1024x500):
a white crescent on black, as decided for the launcher icon. Needs Pillow; fonts from Windows.
"""
import math
import os

from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(__file__), '..', 'docs', 'store')
SS = 4  # supersampling for smooth edges


def arc_points(x1, y1, rx, large, sweep, x2, y2, steps=200):
    """Points of an SVG circular arc (endpoint parameterization, rx == ry, no rotation)."""
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    r = max(rx, math.hypot(dx, dy))
    sign = -1 if large == sweep else 1
    d2 = dx * dx + dy * dy
    coef = sign * math.sqrt(max(0.0, (r * r - d2) / d2))
    cx = coef * dy + (x1 + x2) / 2
    cy = coef * -dx + (y1 + y2) / 2
    a1 = math.atan2(y1 - cy, x1 - cx)
    a2 = math.atan2(y2 - cy, x2 - cx)
    delta = a2 - a1
    if sweep and delta < 0:
        delta += 2 * math.pi
    if not sweep and delta > 0:
        delta -= 2 * math.pi
    return [(cx + r * math.cos(a1 + delta * t / steps), cy + r * math.sin(a1 + delta * t / steps)) for t in range(steps + 1)]


# favicon.svg / ic_launcher_foreground.xml: M40,12 a22,22 0 1 0 12,36 A18,18 0 0 1 40,12 z (64x64 box)
CRESCENT = arc_points(40, 12, 22, 1, 0, 52, 48) + arc_points(52, 48, 18, 0, 1, 40, 12)
MIN_X = min(p[0] for p in CRESCENT)
MAX_X = max(p[0] for p in CRESCENT)
MIN_Y = min(p[1] for p in CRESCENT)
MAX_Y = max(p[1] for p in CRESCENT)


def draw_crescent(draw, cx, cy, height):
    """Draws the crescent with its bounding box centred on (cx, cy), [height] px tall."""
    scale = height / (MAX_Y - MIN_Y)
    ox = cx - (MIN_X + MAX_X) / 2 * scale
    oy = cy - (MIN_Y + MAX_Y) / 2 * scale
    draw.polygon([(ox + x * scale, oy + y * scale) for x, y in CRESCENT], fill='white')


def font(size, bold=False):
    name = 'segoeuib.ttf' if bold else 'segoeui.ttf'
    return ImageFont.truetype(os.path.join(os.environ.get('WINDIR', r'C:\Windows'), 'Fonts', name), size)


def icon():
    size = 512 * SS
    img = Image.new('RGB', (size, size), 'black')
    draw_crescent(ImageDraw.Draw(img), size / 2, size / 2, size * 0.56)
    img.resize((512, 512), Image.LANCZOS).save(os.path.join(OUT, 'icon-512.png'))


def feature_graphic():
    w, h = 1024 * SS, 500 * SS
    img = Image.new('RGB', (w, h), 'black')
    draw = ImageDraw.Draw(img)
    moon_h = h * 0.50
    moon_w = moon_h * (MAX_X - MIN_X) / (MAX_Y - MIN_Y)
    gap = h * 0.08
    text = 'Moon Light Control'
    # Largest title size so that crescent + gap + title fit in 86% of the width.
    size = 120 * SS
    while True:
        title = font(size, bold=True)
        left, _, right, _ = draw.textbbox((0, 0), text, font=title)
        if moon_w + gap + (right - left) <= w * 0.86 or size <= 20:
            break
        size -= SS
    text_w = right - left
    x0 = (w - (moon_w + gap + text_w)) / 2
    draw_crescent(draw, x0 + moon_w / 2, h / 2, moon_h)
    draw.text((x0 + moon_w + gap - left, h / 2), text, font=title, fill='white', anchor='lm')
    img.resize((1024, 500), Image.LANCZOS).save(os.path.join(OUT, 'feature-graphic.png'))


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    icon()
    feature_graphic()
    print('written to', os.path.abspath(OUT))
