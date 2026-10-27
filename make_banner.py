"""Generate the Android TV banner (320x180) and app icon (108x108) for Yuva.
   Original artwork — flat sakura-moon on indigo, matching the launcher palette."""
import os
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(__file__), "app", "src", "main", "res")


def banner():
    W, H = 320, 180
    img = Image.new("RGB", (W, H), (12, 10, 28))
    d = ImageDraw.Draw(img)
    # subtle vertical gradient
    for y in range(H):
        t = y / H
        r = int(12 + (35 - 12) * t); g = int(10 + (26 - 10) * t); b = int(28 + (70 - 28) * t)
        d.line([(0, y), (W, y)], fill=(r, g, b))
    # sakura moon (soft glow + body)
    for rr, a in [(60, 40), (46, 70), (36, 110)]:
        d.ellipse([W - 90 - rr, 30 - rr, W - 90 + rr, 30 + rr], fill=(255, 143, 179, a))
    d.ellipse([W - 115, 10, W - 65, 60], fill=(255, 241, 245))
    # wordmark
    try: font = ImageFont.truetype("arial.ttf", 44)
    except Exception: font = ImageFont.load_default()
    d.text((20, 68), "Yuva", fill=(242, 238, 248), font=font)
    try: sub = ImageFont.truetype("arial.ttf", 16)
    except Exception: sub = ImageFont.load_default()
    d.text((22, 122), "launcher", fill=(154, 148, 176), font=sub)
    out = os.path.join(OUT, "mipmap-hdpi", "ic_banner.png")
    img.save(out); print("banner ->", out)


def icon():
    S = 192
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # rounded square backdrop
    d.rounded_rectangle([8, 8, S - 8, S - 8], radius=28, fill=(23, 18, 51))
    # sakura moon motif centered
    cx, cy = S // 2, S // 2 - 8
    for rr, a in [(72, 30), (56, 55), (44, 90)]:
        d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=(255, 143, 179, a))
    d.ellipse([cx - 32, cy - 32, cx + 32, cy + 32], fill=(255, 241, 245))
    d.ellipse([cx - 20, cy - 28, cx + 28, cy + 20], fill=(23, 18, 51))  # crescent cut
    out = os.path.join(OUT, "mipmap-hdpi", "ic_launcher.png")
    img.save(out); print("icon ->", out)


if __name__ == "__main__":
    os.makedirs(os.path.join(OUT, "mipmap-hdpi"), exist_ok=True)
    banner(); icon()
