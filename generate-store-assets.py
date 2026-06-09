#!/usr/bin/env python3
"""Generate Google Play Store and Android TV listing assets from in-app icon sources."""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "store-assets"
FOREGROUND = ROOT / "app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground.png"
BANNER = ROOT / "app/src/main/res/drawable-xxxhdpi/app_banner.png"

APP_ICON_SIZE = (512, 512)
FEATURE_SIZE = (1024, 500)
TV_BANNER_SIZE = (1280, 720)
BLACK = (0, 0, 0)


def make_app_icon() -> Path:
    src = Image.open(FOREGROUND).convert("RGBA")
    icon = src.resize(APP_ICON_SIZE, Image.Resampling.LANCZOS)
    out = OUT / "app-icon-512.png"
    icon.save(out, optimize=True)
    return out


def make_feature_graphic() -> Path:
    src = Image.open(BANNER).convert("RGBA")
    canvas = Image.new("RGB", FEATURE_SIZE, BLACK)

    scale = FEATURE_SIZE[1] / src.height
    target_w = round(src.width * scale)
    target_h = FEATURE_SIZE[1]
    banner = src.resize((target_w, target_h), Image.Resampling.LANCZOS)

    x = (FEATURE_SIZE[0] - target_w) // 2
    canvas.paste(banner, (x, 0), banner)

    out = OUT / "feature-graphic-1024x500.png"
    canvas.save(out, optimize=True)
    return out


def make_tv_banner() -> Path:
    src = Image.open(BANNER).convert("RGBA")
    banner = src.resize(TV_BANNER_SIZE, Image.Resampling.LANCZOS)
    out = OUT / "android-tv-banner-1280x720.png"
    banner.save(out, optimize=True)
    return out


def main() -> None:
    OUT.mkdir(exist_ok=True)

    app_icon = make_app_icon()
    feature = make_feature_graphic()
    tv_banner = make_tv_banner()

    for path in (app_icon, feature, tv_banner):
        size_kb = path.stat().st_size / 1024
        with Image.open(path) as img:
            print(f"{path.name}: {img.size[0]}x{img.size[1]}, {size_kb:.1f} KB")


if __name__ == "__main__":
    main()
