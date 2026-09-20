#!/usr/bin/env python3
"""Rebuild the dashboard's PWA icons from the team's own logo art.

Same source as the Android launcher - scraped/logo-0.png, the 1080x1080 logo
the scraper pulls off thekcdiamonds.com - so the icon on a phone's home screen
is the icon in the app drawer. Re-run after the scraper picks up a new logo:

    python3 scripts/make-web-icons.py

Writes into docs/, which is what GitHub Pages publishes:
  icon-192.png            the manifest's small icon
  icon-512.png            the manifest's large icon, and what most launchers use
  icon-maskable-512.png   same mark inside a safe zone on a filled background
  icon-180.png            apple-touch-icon, which iOS uses on the home screen

On the two that are not maskable the mark keeps its transparent background.
iOS ignores transparency and composites onto black, so icon-180 is given the
navy plate explicitly rather than left to chance.
"""
from PIL import Image

SRC = 'scraped/logo-0.png'
OUT = 'docs'

# Sampled from the logo art, and the same navy the launcher script uses.
NAVY = (0x11, 0x14, 0x59, 255)

# A maskable icon may be cropped to a circle by the launcher, so the mark has to
# sit inside the centre 80% - the safe zone the spec guarantees. 72% leaves a
# little room beyond the minimum, which matters here because the crown tips and
# the shield's bottom point are the first things a round mask would clip.
MASKABLE_SAFE = 0.72
# The plain icons are shown as-is, so the mark can be larger.
PLAIN_SAFE = 0.92


def load_logo():
    """The logo cropped to its own ink, so padding is computed not inherited."""
    logo = Image.open(SRC).convert('RGBA')
    return logo.crop(logo.split()[3].getbbox())


def render(logo, size, fraction, background=None):
    box = size * fraction
    w, h = logo.size
    scale = min(box / w, box / h)
    art = logo.resize((max(1, round(w * scale)), max(1, round(h * scale))), Image.LANCZOS)
    canvas = Image.new('RGBA', (size, size), background or (0, 0, 0, 0))
    canvas.alpha_composite(art, ((size - art.width) // 2, (size - art.height) // 2))
    return canvas


def main():
    logo = load_logo()
    for name, size, fraction, bg in (
        ('icon-192.png', 192, PLAIN_SAFE, None),
        ('icon-512.png', 512, PLAIN_SAFE, None),
        ('icon-maskable-512.png', 512, MASKABLE_SAFE, NAVY),
        ('icon-180.png', 180, PLAIN_SAFE, NAVY),
    ):
        path = f'{OUT}/{name}'
        render(logo, size, fraction, bg).save(path)
        print(f'wrote {path} ({size}x{size})')


if __name__ == '__main__':
    main()
