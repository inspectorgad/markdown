#!/usr/bin/env python3
"""Rebuild the launcher icon from the team's own logo art.

Input is scraped/logo-0.png, the 1080x1080 "KC Diamonds Logo.png" the scraper
pulls off thekcdiamonds.com — a gold crown over a navy-and-cyan diamond shield
with a KC monogram. Re-run this after the scraper picks up a new logo:

    python3 scripts/make-launcher-icons.py

Writes:
  mipmap-<d>/ic_launcher_foreground.png   adaptive foreground (API 26+)
  mipmap-<d>/ic_launcher_monochrome.png   themed-icon silhouette (API 33+)
  mipmap-<d>/ic_launcher.webp             legacy square icon
  mipmap-<d>/ic_launcher_round.webp       legacy round icon
"""
import os
from PIL import Image, ImageDraw

SRC = 'scraped/logo-0.png'
RES = 'app/src/main/res'

# Sampled straight out of the logo art.
NAVY = (0x11, 0x14, 0x59, 255)

# Density buckets and their scale factor relative to mdpi.
DENSITIES = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}

# An adaptive icon is a 108dp canvas whose centre 66dp is the safe zone — the
# only part guaranteed to survive every launcher mask. The mark is tall and
# narrow, so fitting it to 62% of the canvas keeps the crown tips and the
# bottom point inside that zone.
ADAPTIVE_DP = 108
SAFE = 0.62
# Legacy icons are 48dp and get masked far less aggressively.
LEGACY_DP = 48
LEGACY_INSET = 0.78


def load_logo():
    logo = Image.open(SRC).convert('RGBA')
    return logo.crop(logo.split()[3].getbbox())


def fit(logo, canvas_px, fraction):
    """Scale the mark to `fraction` of a square canvas, centred."""
    box = canvas_px * fraction
    w, h = logo.size
    s = min(box / w, box / h)
    art = logo.resize((max(1, round(w * s)), max(1, round(h * s))), Image.LANCZOS)
    im = Image.new('RGBA', (canvas_px, canvas_px), (0, 0, 0, 0))
    im.alpha_composite(art, ((canvas_px - art.width) // 2,
                             (canvas_px - art.height) // 2))
    return im


def inkify(im):
    """Keep only the navy linework and gold crown as opaque 'ink'.

    A themed icon is filled with a single colour, so an alpha mask of the whole
    mark would flatten to a featureless blob. Treating the dark and gold pixels
    as ink and the cyan/white fills as holes preserves the shield outline and
    the KC monogram.
    """
    out = Image.new('RGBA', im.size, (0, 0, 0, 0))
    src, dst = im.load(), out.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = src[x, y]
            if a < 40:
                continue
            brightness = (r + g + b) / 3
            is_navy = brightness < 110
            is_gold = r > 110 and g > 80 and b < 110 and r > b + 50
            if is_navy or is_gold:
                dst[x, y] = (0, 0, 0, a)
    return out


def circle_mask(im):
    m = Image.new('L', im.size, 0)
    ImageDraw.Draw(m).ellipse((0, 0, im.size[0] - 1, im.size[1] - 1), fill=255)
    out = Image.new('RGBA', im.size, (0, 0, 0, 0))
    out.paste(im, (0, 0), m)
    return out


def main():
    logo = load_logo()
    for bucket, scale in DENSITIES.items():
        d = os.path.join(RES, f'mipmap-{bucket}')
        os.makedirs(d, exist_ok=True)

        px = round(ADAPTIVE_DP * scale)
        fg = fit(logo, px, SAFE)
        fg.save(os.path.join(d, 'ic_launcher_foreground.png'))
        inkify(fg).save(os.path.join(d, 'ic_launcher_monochrome.png'))

        # Legacy icons carry their own navy background, since pre-26 launchers
        # draw a single flat bitmap with no separate background layer.
        lpx = round(LEGACY_DP * scale)
        square = Image.new('RGBA', (lpx, lpx), NAVY)
        square.alpha_composite(fit(logo, lpx, LEGACY_INSET))
        square.save(os.path.join(d, 'ic_launcher.webp'), lossless=True)

        round_bg = circle_mask(Image.new('RGBA', (lpx, lpx), NAVY))
        round_bg.alpha_composite(fit(logo, lpx, LEGACY_INSET * 0.86))
        round_bg.save(os.path.join(d, 'ic_launcher_round.webp'), lossless=True)

        print(f'{bucket}: adaptive {px}px, legacy {lpx}px')


if __name__ == '__main__':
    main()
