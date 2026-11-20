"""Видео-демонстрация для RuStore/VK Видео: 1080×1920, 30 к/с, ~60 с.
Сцены — те же, что на скриншотах (make_screens.py): заметка с заголовком, телефон «въезжает» снизу,
экран приложения плавно прокручивается; между сценами — растворение. В начале и в конце — заставка.
Запуск: python make_video.py <папка_снимков> → store/rustore/demo.mp4"""
import math, os, subprocess, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import imageio_ffmpeg

RAW = sys.argv[1]
HERE = os.path.dirname(os.path.abspath(__file__))
FONTS = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "font")
W, H, FPS = 1080, 1920, 30
INK = (46, 42, 37)
COL = {"mint": (168, 230, 207), "lemon": (255, 240, 160), "rose": (255, 183, 197), "sky": (217, 242, 255),
       "lav": (199, 202, 255), "peach": (255, 214, 182), "board": (233, 226, 212)}
TAPE = [(255, 214, 90, 200), (120, 200, 255, 190), (255, 160, 180, 190), (150, 220, 190, 200)]
hand = lambda s: ImageFont.truetype(os.path.join(FONTS, "amatic_sc_bold.ttf"), s)
body = lambda s: ImageFont.truetype(os.path.join(FONTS, "nunito_bold.ttf"), s)

# (снимок или список снимков подряд, цвет, заголовок, подпись, прокрутка от, прокрутка до, секунд)
SCENES = [
    (["results.png"], "lemon", "Проверка класса\nза пару минут", "Снимите пачку бланков — у каждого оценка и ошибки", 0, 0, 6),
    (["work_detail.png"], "mint", "Ошибки видны\nна каждом бланке", "Верно — зелёным, ошибка — розовым", 140, 140, 5),
    (["named_list.png"], "sky", "Фамилия\nподставится сама", "Именные бланки: номер ученика на листе", 0, 700, 6),
    (["preview.png"], "peach", "Тест по шагам", "Лист в предпросмотре — ровно как на бумаге", 0, 300, 5),
    (["rep_overview.png", "rep_overview2.png"], "lav", "Отчёт по тесту", "Средняя оценка, успеваемость, классы, варианты", 150, 1500, 7),
    (["rep_q.png", "rep_q2.png"], "rose", "Трудные вопросы\nкак на ладони", "Карта вопросов и частые ошибки", 735, 1800, 7),
    (["journals.png"], "mint", "Журналы классов", "Ввод списком или загрузка из Excel", 0, 200, 5),
    (["menu.png"], "board", "Без интернета\nи без рекламы", "Фото и оценки остаются на вашем телефоне", 0, 0, 5),
]
FADE = 0.45


def ease(t):
    return 0.5 - 0.5 * math.cos(math.pi * max(0.0, min(1.0, t)))


def board(color):
    im = Image.new("RGB", (W, H), color)
    d = ImageDraw.Draw(im)
    dot = tuple(max(0, c - 22) for c in color)
    for y in range(6, H, 14):
        for x in range(6, W, 14):
            d.ellipse((x - 1, y - 1, x + 1, y + 1), fill=dot)
    return im


def header(im, i, title, sub):
    d = ImageDraw.Draw(im)
    lines = title.split("\n")
    f = hand(132 if len(lines) == 1 else 120)
    tw = max(d.textlength(l, font=f) for l in lines)
    th = sum(f.getbbox(l)[3] for l in lines) + 10 * (len(lines) - 1)
    note = Image.new("RGBA", (int(tw) + 110, th + 70), (255, 255, 255, 255))
    nd = ImageDraw.Draw(note)
    y = 25
    for l in lines:
        nd.text(((note.width - nd.textlength(l, font=f)) / 2, y), l, font=f, fill=INK)
        y += f.getbbox(l)[3] + 10
    ang = -2.5 if i % 2 == 0 else 2.0
    shadow = Image.new("RGBA", (note.width + 30, note.height + 30), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rectangle((18, 18, note.width + 12, note.height + 12), fill=(0, 0, 0, 60))
    shadow = shadow.filter(ImageFilter.GaussianBlur(8)).rotate(ang, expand=True, resample=Image.BICUBIC)
    note = note.rotate(ang, expand=True, resample=Image.BICUBIC)
    nx, ny = (W - note.width) // 2, 70
    im.paste(shadow, (nx - 6, ny - 4), shadow)
    im.paste(note, (nx, ny), note)
    t = Image.new("RGBA", (180, 46), TAPE[i % len(TAPE)]).rotate(3 if i % 2 else -4, expand=True, resample=Image.BICUBIC)
    im.paste(t, (W // 2 - 90, ny - 26), t)
    fb = body(40)
    sy = ny + note.height + 26
    words, rows, cur = sub.split(), [], ""
    for w_ in words:
        tt = (cur + " " + w_).strip()
        if d.textlength(tt, font=fb) > W - 140:
            rows.append(cur); cur = w_
        else:
            cur = tt
    rows.append(cur)
    for r in rows:
        d.text(((W - d.textlength(r, font=fb)) / 2, sy), r, font=fb, fill=INK)
        sy += 54
    return sy + 40


def tall(shots):
    """Склеить снимки, убрав повтор: второй снимок — продолжение прокрутки (стык по шапке)."""
    ims = [Image.open(os.path.join(RAW, s)).convert("RGB") for s in shots]
    if len(ims) == 1:
        return ims[0]
    a, b = ims
    # найти, где начало второго снимка (ниже его шапки) совпадает с первым
    A = np.asarray(a.convert("L"), dtype=np.int16)
    B = np.asarray(b.convert("L"), dtype=np.int16)
    probe = B[700:760]
    best, by = 1e18, 0
    for y in range(200, A.shape[0] - 60, 4):
        d = np.abs(A[y:y + 60] - probe).mean()
        if d < best:
            best, by = d, y
    out = Image.new("RGB", (a.width, by + (B.shape[0] - 700)))
    out.paste(a.crop((0, 0, a.width, by)), (0, 0))
    out.paste(b.crop((0, 700, b.width, b.height)), (0, by))
    return out


def phone_frame(content, scroll, view_h, width=780):
    k = (width - 44) / content.width
    src_h = int(view_h / k)
    crop = content.crop((0, scroll, content.width, min(content.height, scroll + src_h)))
    s = crop.resize((width - 44, int(crop.height * k)), Image.BILINEAR)
    ph = Image.new("RGBA", (width, view_h + 44), (0, 0, 0, 0))
    ImageDraw.Draw(ph).rounded_rectangle((0, 0, width - 1, view_h + 43), 64, fill=INK)
    mask = Image.new("L", s.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, s.width - 1, s.height - 1), 44, fill=255)
    ph.paste(s, (22, 22), mask)
    return ph


def scene_frames(i, sc):
    shots, color, title, sub, s0, s1, secs = sc
    bg = board(COL[color])
    py = header(bg, i, title, sub)
    content = tall(shots)
    view_h = H - py + 80
    s1 = min(s1, max(0, content.height - int(view_h * content.width / 736)))
    shadow = Image.new("RGBA", (840, view_h + 104), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((30, 40, 810, view_h + 74), 64, fill=(0, 0, 0, 70))
    shadow = shadow.filter(ImageFilter.GaussianBlur(14))
    n = int(secs * FPS)
    for f in range(n):
        t = f / FPS
        rise = ease(t / 0.8)
        y = int(py + (1 - rise) * 500)
        sc_t = ease((t - 1.2) / max(0.1, secs - 2.2))
        scroll = int(s0 + (s1 - s0) * sc_t)
        fr = bg.copy()
        ph = phone_frame(content, scroll, view_h)
        fr.paste(shadow, (120 - 30, y - 30), shadow)
        fr.paste(ph, (150, y), ph)
        yield fr


def card(lines, sub, color, icon=None, secs=3.0):
    bg = board(COL[color])
    d = ImageDraw.Draw(bg)
    y = 560
    if icon:
        ic = Image.open(icon).convert("RGB").resize((300, 300), Image.LANCZOS)
        m = Image.new("L", ic.size, 0)
        ImageDraw.Draw(m).rounded_rectangle((0, 0, 299, 299), 70, fill=255)
        bg.paste(ic, ((W - 300) // 2, 360), m)
        y = 720
    f = hand(170)
    for l in lines:
        d.text(((W - d.textlength(l, font=f)) / 2, y), l, font=f, fill=INK)
        y += 170
    fb = body(46)
    for l in sub:
        d.text(((W - d.textlength(l, font=fb)) / 2, y + 30), l, font=fb, fill=INK)
        y += 62
    n = int(secs * FPS)
    for f_ in range(n):
        k = 0.94 + 0.06 * ease(f_ / (FPS * 0.7))
        fr = bg.resize((int(W * k), int(H * k)), Image.BILINEAR)
        out = Image.new("RGB", (W, H), COL[color])
        out.paste(fr, ((W - fr.width) // 2, (H - fr.height) // 2))
        yield out


def main():
    icon = os.path.join(HERE, "icon-512.png")
    parts = [card(["Сканер тестов"], ["Проверка тестовых бланков", "по фото — прямо на телефоне"], "board", icon, 3.5)]
    parts += [scene_frames(i, s) for i, s in enumerate(SCENES)]
    parts += [card(["Проверяйте", "быстрее"], ["Бесплатно, без рекламы", "Скачайте в RuStore"], "mint", icon, 4.0)]
    out = os.path.join(HERE, "demo.mp4")
    cmd = [imageio_ffmpeg.get_ffmpeg_exe(), "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS),
           "-i", "-", "-c:v", "libx264", "-preset", "medium", "-crf", "20", "-pix_fmt", "yuv420p", "-movflags", "+faststart", out]
    p = subprocess.Popen(cmd, stdin=subprocess.PIPE, stderr=subprocess.DEVNULL)
    prev_tail = []
    nf = int(FADE * FPS)
    total = 0
    for gen in parts:
        frames = gen
        first = True
        buf = []
        for fr in frames:
            if first and prev_tail:
                # растворение: последние кадры прошлой сцены поверх первых этой
                for k, old in enumerate(prev_tail):
                    a = (k + 1) / (len(prev_tail) + 1)
                    p.stdin.write(Image.blend(old, fr, a).tobytes()); total += 1
                first = False
            first = False
            buf.append(fr)
            if len(buf) > nf:
                p.stdin.write(buf.pop(0).tobytes()); total += 1
        prev_tail = buf
    for fr in prev_tail:
        p.stdin.write(fr.tobytes()); total += 1
    p.stdin.close(); p.wait()
    print(out, total / FPS, "с")


main()
