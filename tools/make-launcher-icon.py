"""Render the launcher icon from the real Rive scene.

The adaptive-icon foreground is a headless render of `rive/duo/scene.rml` — the same drawing the
module ships — keyed from the renderer's black background to transparency and placed inside the
adaptive icon's safe zone, so a launcher's mask never clips the ring.

Usage (from the repository root):

    python tools/make-launcher-icon.py

Needs the Rive CLI on PATH or at %USERPROFILE%\\.rive\\bin\\rive.exe, and Pillow.
"""

import os
import subprocess
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROJECT = os.path.join(ROOT, "rive", "duo")
RENDER = os.path.join(PROJECT, "build", "icon-fg.png")
PREVIEW = os.path.join(PROJECT, "build", "icon-preview.png")
RES = os.path.join(ROOT, "app", "src", "main", "res")
RIVE = os.environ.get("RIVE_CLI") or os.path.join(
    os.path.expanduser("~"), ".rive", "bin", "rive.exe"
)

DENSITIES = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}

# The artwork is kept inside 58% of the 108dp canvas: the adaptive safe zone is the centre 66dp,
# and the ring is the widest part, so this leaves margin against an aggressive OEM mask.
SAFE = 0.58

# Red Wine, the same colour as the adaptive-icon background (FR-11).
BACKGROUND = (0x7B, 0x1E, 0x3A, 0xFF)

# The icon state: a 74 % ring, strong Wi-Fi and a full four-sphere cellular, matching the README art.
DATA = [
    "middleMode=1",
    "wifiLevel=3",
    "cellLevel=4",
    "percentText=74",
    "trimLeftEnd=0.2379",
    "trimRightEnd=0.1142",
]


def render():
    cmd = [
        RIVE, ".", f"--screenshot={RENDER}", "--viewport=512x512", "--advance=2s",
    ] + [f"--data={item}" for item in DATA]
    subprocess.run(
        cmd, cwd=PROJECT, check=True,
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )


def keyed_artwork():
    """The white element on transparency, cropped to its bounds.

    The CLI renders on an opaque near-black background, so luminance becomes the alpha with a floor:
    anything darker than the floor is background, and the white element keeps its anti-aliased edge.
    """
    image = Image.open(RENDER).convert("RGB")
    floor = 32
    alpha = image.convert("L").point(
        lambda v: 0 if v <= floor else min(255, int((v - floor) * 255 / (255 - floor)))
    )
    box = alpha.getbbox()
    if box is None:
        raise SystemExit("render was empty; check the Rive CLI output")
    art = Image.new("RGBA", image.size, (255, 255, 255, 0))
    art.putalpha(alpha)
    return art.crop(box)


def main():
    if not os.path.exists(RIVE):
        raise SystemExit(f"Rive CLI not found at {RIVE}; set RIVE_CLI")
    render()
    art = keyed_artwork()

    for folder, size in DENSITIES.items():
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        target = int(size * SAFE)
        width, height = art.size
        scale = target / max(width, height)
        scaled = art.resize(
            (max(1, round(width * scale)), max(1, round(height * scale))), Image.LANCZOS
        )
        canvas.alpha_composite(
            scaled, ((size - scaled.width) // 2, (size - scaled.height) // 2)
        )
        path = os.path.join(RES, folder, "ic_launcher_foreground.png")
        canvas.save(path)
        print(f"wrote {os.path.relpath(path, ROOT)} ({size}x{size})")

    # A composited preview on the icon background, so the result can be looked at before shipping.
    preview = Image.new("RGBA", (432, 432), BACKGROUND)
    preview.alpha_composite(Image.open(os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher_foreground.png")))
    preview.convert("RGB").save(PREVIEW)
    print(f"wrote {os.path.relpath(PREVIEW, ROOT)}")


if __name__ == "__main__":
    main()
