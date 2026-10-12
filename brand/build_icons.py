"""Builds every Zeul icon asset from the master artwork (brand/zeul-hamster-master.jpg).

    python brand/build_icons.py

Outputs (all in brand/out/):
  android/res/...            adaptive launcher icon (foreground + monochrome WebP per density, background colour, XML)
  play-store-icon-512.png    Google Play listing icon (Play applies its own rounded mask)
  web/                       favicon.ico, favicon-32.png, apple-touch-icon-180.png, icon-192.png, icon-512.png
  previews/                  how the launcher icon looks in common mask shapes (for checking only)
"""
import colorsys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

HERE = Path(__file__).parent
MASTER = HERE / "zeul-hamster-master.jpg"
OUT = HERE / "out"
BG = (0xFF, 0x4A, 0x01)          # background orange sampled from the master's edges
BG_HEX = "#FF4A01"

# Where the hamster sits on the 108 dp adaptive canvas (fractions of the canvas).
# The launcher shows the central 72 dp (66.7 %); the "always visible" safe circle is 66 dp.
ART_SCALE = 0.71                 # master width as a fraction of the canvas width
ART_BOTTOM = 0.845               # master's bottom edge (the body is cut there) just below the visible area

DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def foreground(size: int) -> Image.Image:
    """Opaque foreground: orange canvas with the scaled artwork; edges blend into the background."""
    master = Image.open(MASTER).convert("RGB")
    s = round(size * ART_SCALE)
    art = master.resize((s, s), Image.LANCZOS)
    canvas = Image.new("RGB", (size, size), BG)
    x0 = (size - s) // 2
    y0 = round(size * ART_BOTTOM) - s
    canvas.paste(art, (x0, y0))
    # Below the cut-off body: extend the body's bottom row down so no hard edge can show when
    # a launcher animates or scales the layer.
    strip = art.crop((0, s - 1, s, s)).resize((s, size - (y0 + s)))
    canvas.paste(strip, (x0, y0 + s))
    return canvas.convert("RGBA")


def hamster_mask(img: Image.Image) -> Image.Image:
    """White where the hamster is, black for the orange background (by saturation/hue)."""
    rgb = img.convert("RGB")
    w, h = rgb.size
    px = rgb.load()
    m = Image.new("L", (w, h), 0)
    mp = m.load()
    for y in range(h):
        for x in range(w):
            r, g, b = px[x, y]
            hh, ss, vv = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            is_bg = ss > 0.86 and 0.0 <= hh < 0.095 and vv > 0.85
            mp[x, y] = 0 if is_bg else 255
    m = m.filter(ImageFilter.MedianFilter(5))
    return m


def to_canvas(mx: float, my: float) -> tuple[float, float]:
    """Master-image fraction → adaptive-canvas fraction (same placement as foreground())."""
    return (1 - ART_SCALE) / 2 + mx * ART_SCALE, (ART_BOTTOM - ART_SCALE) + my * ART_SCALE


def monochrome(size: int) -> Image.Image:
    """Themed-icon layer (Android 13+): solid silhouette with round eyes and a nose cut out.
    The system tints it, so it must be one clean shape."""
    W = size * 4                                 # work at 4x for smooth edges
    sil = hamster_mask(foreground(W))
    # Fill everything enclosed by the outline (inner ears, glossy eye highlights...): flood the
    # background from the corners, then whatever the flood didn't reach is the hamster.
    flood = sil.copy()
    for corner in [(0, 0), (W - 1, 0), (0, int(W * 0.5)), (W - 1, int(W * 0.5))]:
        ImageDraw.floodfill(flood, corner, 128)
    sil = flood.point(lambda v: 0 if v == 128 else 255)
    sil = sil.filter(ImageFilter.MedianFilter(9)).filter(ImageFilter.GaussianBlur(W * 0.002)).point(lambda v: 255 if v > 127 else 0)
    # Crisp geometric holes at the master's eye and nose positions.
    d = ImageDraw.Draw(sil)
    eye_r = 0.031 * ART_SCALE * W
    for mx in (0.3516, 0.5908):
        cx, cy = to_canvas(mx, 0.481)
        d.ellipse([cx * W - eye_r, cy * W - eye_r * 1.08, cx * W + eye_r, cy * W + eye_r * 1.08], fill=0)
    nx, ny = to_canvas(0.4785, 0.556)
    nw, nh = 0.034 * ART_SCALE * W, 0.022 * ART_SCALE * W
    d.rounded_rectangle([nx * W - nw, ny * W - nh, nx * W + nw, ny * W + nh], radius=nh, fill=0)
    layer = Image.new("RGBA", (W, W), (255, 255, 255, 0))
    layer.putalpha(sil)
    return layer.resize((size, size), Image.LANCZOS)


def build_android():
    res = OUT / "android" / "res"
    for name, k in DENSITIES.items():
        d = res / f"mipmap-{name}"
        d.mkdir(parents=True, exist_ok=True)
        size = round(108 * k)
        foreground(size).save(d / "ic_launcher_foreground.webp", "WEBP", quality=95, method=6)
        monochrome(size).save(d / "ic_launcher_monochrome.webp", "WEBP", lossless=True)
    # minSdk is 26, so one adaptive icon in mipmap-anydpi covers every device (the manifest uses it
    # for both icon and roundIcon). The background colour lives in the app's values/colors.xml
    # (ic_launcher_background = BG_HEX); it isn't written here to avoid a duplicate resource.
    anydpi = res / "mipmap-anydpi"
    anydpi.mkdir(parents=True, exist_ok=True)
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!-- Zeul hamster (P1). Generated by brand/build_icons.py; background colour in values/colors.xml. -->',
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">',
        '    <background android:drawable="@color/ic_launcher_background" />',
        '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />',
        '    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />',
        '</adaptive-icon>',
    ]
    (anydpi / "ic_launcher.xml").write_text("\n".join(lines) + "\n", encoding="utf-8")


def tight_crop(px: int) -> Image.Image:
    """The launcher's visible area (central 72 of 108 dp) as a plain square, for web/favicons."""
    big = foreground(1296)                       # 108 dp * 12
    m = round(1296 * 18 / 108)
    return big.crop((m, m, 1296 - m, 1296 - m)).resize((px, px), Image.LANCZOS)


def rounded(img: Image.Image, radius_frac: float) -> Image.Image:
    w, h = img.size
    mask = Image.new("L", (w * 4, h * 4), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w * 4 - 1, h * 4 - 1], radius=int(w * 4 * radius_frac), fill=255)
    out = img.copy()
    out.putalpha(mask.resize((w, h), Image.LANCZOS))
    return out


def build_store_and_web():
    OUT.mkdir(parents=True, exist_ok=True)
    Image.open(MASTER).convert("RGB").resize((512, 512), Image.LANCZOS).save(OUT / "play-store-icon-512.png", optimize=True)
    web = OUT / "web"
    web.mkdir(parents=True, exist_ok=True)
    for px, name in [(32, "favicon-32.png"), (180, "apple-touch-icon-180.png"), (192, "icon-192.png"), (512, "icon-512.png")]:
        img = tight_crop(px).convert("RGB")
        (rounded(img.convert("RGBA"), 0.22) if px <= 32 else img).save(web / name, optimize=True)
    rounded(tight_crop(256), 0.22).save(web / "favicon.ico", sizes=[(16, 16), (32, 32), (48, 48), (64, 64)])


def build_previews():
    """Launcher masks: circle, squircle, rounded square, teardrop + the themed (monochrome) look."""
    prev = OUT / "previews"
    prev.mkdir(parents=True, exist_ok=True)
    S = 432
    fg = foreground(S)
    bg = Image.new("RGBA", (S, S), BG + (255,))
    full = Image.alpha_composite(bg, fg)
    m0, m1 = 72, 360                              # visible 72 dp window on the 108 dp canvas
    shapes = {}
    for name in ["circle", "squircle", "rounded", "teardrop"]:
        mask = Image.new("L", (S * 4, S * 4), 0)
        d = ImageDraw.Draw(mask)
        box = [m0 * 4, m0 * 4, m1 * 4, m1 * 4]
        if name == "circle":
            d.ellipse(box, fill=255)
        elif name == "squircle":
            d.rounded_rectangle(box, radius=int((m1 - m0) * 4 * 0.38), fill=255)
        elif name == "rounded":
            d.rounded_rectangle(box, radius=int((m1 - m0) * 4 * 0.16), fill=255)
        else:
            d.rounded_rectangle(box, radius=int((m1 - m0) * 4 * 0.5), fill=255)
            d.rectangle([(m0 + m1) * 2, (m0 + m1) * 2, m1 * 4, m1 * 4], fill=255)
        shapes[name] = mask.resize((S, S), Image.LANCZOS)
    # Themed icon: tinted monochrome on a pale tint background
    mono = monochrome(S)
    tint_bg = Image.new("RGBA", (S, S), (233, 224, 255, 255))
    ink = Image.new("RGBA", (S, S), (62, 40, 120, 255))
    ink.putalpha(mono.getchannel("A"))
    themed = Image.alpha_composite(tint_bg, ink)
    sheet = Image.new("RGBA", (S * 5 + 60, S + 20), (245, 245, 245, 255))
    for i, (name, mask) in enumerate(list(shapes.items()) + [("themed", shapes["circle"])]):
        src = themed if name == "themed" else full
        tile = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        tile.paste(src, (0, 0), mask)
        sheet.alpha_composite(tile, (10 + i * (S + 10), 10))
    sheet = sheet.crop((0, 0, sheet.width, sheet.height))
    sheet.save(prev / "launcher-shapes.png")
    # Small sizes, as on a home screen
    small = Image.new("RGBA", (5 * 70, 80), (30, 30, 34, 255))
    for i, px in enumerate([48, 48, 40, 32, 24]):
        icon = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        icon.paste(full, (0, 0), shapes["squircle"])
        icon = icon.crop((m0, m0, m1, m1)).resize((px, px), Image.LANCZOS)
        small.alpha_composite(icon, (10 + i * 70, (80 - px) // 2))
    small.save(prev / "small-sizes.png")


if __name__ == "__main__":
    build_android()
    build_store_and_web()
    build_previews()
    print("done:", OUT)
