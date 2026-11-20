"""Скриншоты для RuStore: 1080×1920 (9:16), фон-«доска» цвета стиля приложения, рукописный заголовок,
подзаголовок и телефон с настоящим экраном приложения (низ телефона уходит за край).
Запуск: python make_screens.py <папка_с_снимками_экрана>  → store/rustore/screens/*.png"""
import os, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

RAW = sys.argv[1]
HERE = os.path.dirname(os.path.abspath(__file__))
FONTS = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "font")
OUT = os.path.join(HERE, "screens")
os.makedirs(OUT, exist_ok=True)

INK = (46, 42, 37)
COLORS = {
    "mint": (168, 230, 207), "lemon": (255, 240, 160), "rose": (255, 183, 197),
    "sky": (217, 242, 255), "lav": (199, 202, 255), "peach": (255, 214, 182), "board": (233, 226, 212),
}
TAPE = [(255, 214, 90, 200), (120, 200, 255, 190), (255, 160, 180, 190), (150, 220, 190, 200)]

W, H = 1080, 1920
hand = lambda s: ImageFont.truetype(os.path.join(FONTS, "amatic_sc_bold.ttf"), s)
body = lambda s: ImageFont.truetype(os.path.join(FONTS, "nunito_bold.ttf"), s)

SLIDES = [
    ("results.png", "lemon", "Проверка класса\nза пару минут", "Снимите пачку бланков — у каждого оценка и ошибки", 0),
    ("work_detail.png", "mint", "Ошибки видны\nна каждом бланке", "Верно — зелёным, ошибка — розовым", 140),
    ("named_list.png", "sky", "Фамилия\nподставится сама", "Именные бланки на класс: номер ученика на листе", 160),
    ("preview.png", "peach", "Тест по шагам", "Лист в предпросмотре — ровно как на бумаге", 0),
    ("rep_overview.png", "lav", "Отчёт по тесту", "Средняя оценка, успеваемость, качество, классы", 150),
    ("rep_q.png", "rose", "Трудные вопросы\nкак на ладони", "Карта вопросов и частые ошибки", 735),
    ("journals.png", "mint", "Журналы классов", "Ввод списком или загрузка из Excel", 0),
    ("menu.png", "board", "Без интернета\nи без рекламы", "Фото и оценки остаются только на вашем телефоне", 0),
]


def board(color):
    im = Image.new("RGB", (W, H), color)
    d = ImageDraw.Draw(im)
    dot = tuple(max(0, c - 22) for c in color)
    for y in range(6, H, 14):
        for x in range(6, W, 14):
            d.ellipse((x - 1, y - 1, x + 1, y + 1), fill=dot)
    return im


def tape(im, x, y, w, h, angle, col):
    t = Image.new("RGBA", (w, h), col)
    t = t.rotate(angle, expand=True, resample=Image.BICUBIC)
    im.paste(t, (x, y), t)


def phone(shot, scroll, width=780):
    s = Image.open(os.path.join(RAW, shot)).convert("RGB")
    s = s.crop((0, scroll, s.width, min(s.height, scroll + 2200)))  # строки состояния на снимках приложения нет
    k = (width - 44) / s.width
    s = s.resize((width - 44, int(s.height * k)), Image.LANCZOS)
    ph_h = s.height + 44
    ph = Image.new("RGBA", (width, ph_h), (0, 0, 0, 0))
    d = ImageDraw.Draw(ph)
    d.rounded_rectangle((0, 0, width - 1, ph_h - 1), 64, fill=INK)
    mask = Image.new("L", s.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, s.width - 1, s.height - 1), 44, fill=255)
    ph.paste(s, (22, 22), mask)
    return ph


def make(i, shot, color, title, sub, scroll):
    c = COLORS[color]
    im = board(c)
    d = ImageDraw.Draw(im)
    # заголовок: бумажка со скотчем
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
    shadow = Image.new("RGBA", (note.width + 30, note.height + 30), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rectangle((18, 18, note.width + 12, note.height + 12), fill=(0, 0, 0, 60))
    shadow = shadow.filter(ImageFilter.GaussianBlur(8))
    ang = -2.5 if i % 2 == 0 else 2.0
    note_r = note.rotate(ang, expand=True, resample=Image.BICUBIC)
    sh_r = shadow.rotate(ang, expand=True, resample=Image.BICUBIC)
    nx = (W - note_r.width) // 2
    ny = 70
    im.paste(sh_r, (nx - 6, ny - 4), sh_r)
    im.paste(note_r, (nx, ny), note_r)
    tape(im, W // 2 - 90, ny - 26, 180, 46, 3 if i % 2 else -4, TAPE[i % len(TAPE)])
    # подзаголовок
    fb = body(40)
    sy = ny + note_r.height + 26
    words, rows, cur = sub.split(), [], ""
    for w_ in words:
        t = (cur + " " + w_).strip()
        if d.textlength(t, font=fb) > W - 140:
            rows.append(cur); cur = w_
        else:
            cur = t
    rows.append(cur)
    for r in rows:
        d.text(((W - d.textlength(r, font=fb)) / 2, sy), r, font=fb, fill=INK)
        sy += 54
    # телефон
    ph = phone(shot, scroll)
    py = sy + 40
    sh = Image.new("RGBA", (ph.width + 60, ph.height + 60), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle((30, 40, ph.width + 30, ph.height + 30), 64, fill=(0, 0, 0, 70))
    sh = sh.filter(ImageFilter.GaussianBlur(14))
    px = (W - ph.width) // 2
    im.paste(sh, (px - 30, py - 30), sh)
    im.paste(ph, (px, py), ph)
    fn = os.path.join(OUT, "%02d.png" % (i + 1))
    im.save(fn, optimize=True)
    return fn


for i, s in enumerate(SLIDES):
    print(make(i, *s))
