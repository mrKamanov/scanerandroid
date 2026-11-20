"""Картинки для README: docs/readme/banner.png, screens.png (полоса скриншотов), demo.gif (из demo.mp4).
Запуск из корня проекта: python store/rustore/make_readme_media.py
Нужны: store/rustore/screens/*.png и store/rustore/demo.mp4 (их делают make_screens.py и make_video.py)."""
import os, subprocess
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import imageio_ffmpeg

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
FONTS = os.path.join(ROOT, "app", "src", "main", "res", "font")
STORE = os.path.join(ROOT, "store", "rustore")
OUT = os.path.join(ROOT, "docs", "readme")
os.makedirs(OUT, exist_ok=True)
INK = (46, 42, 37)
BOARD = (233, 226, 212)
hand = lambda s: ImageFont.truetype(os.path.join(FONTS, "amatic_sc_bold.ttf"), s)
body = lambda s: ImageFont.truetype(os.path.join(FONTS, "nunito_bold.ttf"), s)


def dotted(w, h, color):
    im = Image.new("RGB", (w, h), color)
    d = ImageDraw.Draw(im)
    dot = tuple(max(0, c - 20) for c in color)
    for y in range(5, h, 12):
        for x in range(5, w, 12):
            d.ellipse((x - 1, y - 1, x + 1, y + 1), fill=dot)
    return im


def shadowed(im, radius=12, off=(10, 14), alpha=80):
    w, h = im.size
    sh = Image.new("RGBA", (w + 60, h + 60), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle((30 + off[0], 30 + off[1], 30 + off[0] + w, 30 + off[1] + h), radius, fill=(0, 0, 0, alpha))
    sh = sh.filter(ImageFilter.GaussianBlur(12))
    sh.paste(im, (30, 30), im if im.mode == "RGBA" else None)
    return sh


def banner():
    W, H = 1600, 640
    im = dotted(W, H, BOARD)
    # иконка
    ic = Image.open(os.path.join(STORE, "icon-512.png")).convert("RGBA").resize((170, 170), Image.LANCZOS)
    m = Image.new("L", ic.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, 169, 169), 40, fill=255)
    ic.putalpha(m)
    s = shadowed(ic, 40, (6, 8), 70)
    im.paste(s, (70, 50), s)
    # заголовок на бумажке
    f = hand(140)
    tw = ImageDraw.Draw(Image.new("RGB", (1, 1))).textlength("Сканер тестов", font=f)
    note = Image.new("RGBA", (int(tw) + 80, 185), (255, 255, 255, 255))
    ImageDraw.Draw(note).text((40, 12), "Сканер тестов", font=f, fill=INK)
    note = shadowed(note.rotate(-2, expand=True, resample=Image.BICUBIC), 4, (8, 10), 60)
    im.paste(note, (250, 25), note)
    tape = Image.new("RGBA", (160, 40), (255, 214, 90, 210)).rotate(-4, expand=True, resample=Image.BICUBIC)
    im.paste(tape, (450, 30), tape)
    d = ImageDraw.Draw(im)
    fb = body(34)
    lines = ["Проверка тестовых бланков по фото —", "оценки, ошибки и отчёт по классу", "прямо на телефоне, без интернета."]
    y = 290
    for l in lines:
        d.text((90, y), l, font=fb, fill=INK)
        y += 50
    chips = [("Android 8+", (168, 230, 207)), ("без рекламы", (255, 240, 160)), ("офлайн", (217, 242, 255)), ("GPL-3.0", (255, 183, 197))]
    x = 90
    fc = body(26)
    for t, c in chips:
        w = d.textlength(t, font=fc) + 40
        d.rounded_rectangle((x, 470, x + w, 520), 25, fill=c, outline=INK, width=3)
        d.text((x + 20, 478), t, font=fc, fill=INK)
        x += w + 16
    # три экрана веером
    shots = ["01.png", "03.png", "05.png"]
    pos = [(960, 80, -7), (1150, 50, 0), (1340, 90, 7)]
    for (fn, (px, py, ang)) in zip(shots, pos):
        sc = Image.open(os.path.join(STORE, "screens", fn)).convert("RGB")
        sc = sc.resize((270, 480), Image.LANCZOS).convert("RGBA")
        mk = Image.new("L", sc.size, 0)
        ImageDraw.Draw(mk).rounded_rectangle((0, 0, 269, 479), 24, fill=255)
        sc.putalpha(mk)
        sc = shadowed(sc.rotate(ang, expand=True, resample=Image.BICUBIC), 26, (8, 12), 90)
        im.paste(sc, (px - 60, py), sc)
    im.save(os.path.join(OUT, "banner.png"), optimize=True)


def strip():
    fs = ["01.png", "02.png", "03.png", "04.png", "05.png", "06.png", "07.png", "08.png"]
    w, h = 300, 533
    S = dotted(4 * w + 5 * 20, 2 * h + 3 * 20, BOARD)
    for i, fn in enumerate(fs):
        sc = Image.open(os.path.join(STORE, "screens", fn)).convert("RGB").resize((w, h), Image.LANCZOS)
        S.paste(sc, (20 + (i % 4) * (w + 20), 20 + (i // 4) * (h + 20)))
    S.save(os.path.join(OUT, "screens.jpg"), quality=86, optimize=True)


def gif():
    ff = imageio_ffmpeg.get_ffmpeg_exe()
    src = os.path.join(STORE, "demo.mp4")
    pal = os.path.join(OUT, "_pal.png")
    vf = "fps=8,scale=240:-1:flags=lanczos"
    subprocess.run([ff, "-y", "-loglevel", "error", "-ss", "3", "-t", "20", "-i", src, "-vf", vf + ",palettegen=max_colors=96", pal], check=True)
    subprocess.run([ff, "-y", "-loglevel", "error", "-ss", "3", "-t", "20", "-i", src, "-i", pal,
                    "-lavfi", vf + "[x];[x][1:v]paletteuse=dither=bayer:bayer_scale=4", os.path.join(OUT, "demo.gif")], check=True)
    os.remove(pal)


banner(); strip(); gif()
for f in os.listdir(OUT):
    print(f, os.path.getsize(os.path.join(OUT, f)) // 1024, "КБ")
