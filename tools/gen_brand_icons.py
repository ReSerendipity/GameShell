#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 GameShell 品牌图标位图（无需 Pillow，纯标准库光栅化 + PNG 编码）。

图形来源（2026-11-01 换图）：
- 母题是 DraftPeek 的定稿启动图标 **G34O 偏心窥圆**：九弦裁入圆、雾墨三级色阶
  （#33475F）、电光蓝强调弦（#3178C6）、朱砂红眼（#C41E3A），底色 #F5F7FA（日夜一致）。
- 几何数据逐字取自 `DraftPeek/docs/icon-design/icon-export-g34o/geometry.json`，落盘为
  `tools/icon-geometry-g34o.json`；本脚本只做 100→108 空间换算（×1.08），不改一个坐标。
  要换图请改这个 JSON（或重新从 DraftPeek 拷贝），不要在本文件里手调点位。
- 桌面实际生效的是矢量层：`res/drawable/ic_launcher_foreground.xml` + `ic_launcher_monochrome.xml`
  经 `res/mipmap-anydpi-v26/ic_launcher.xml` 组装成自适应图标。

为什么不再产出各密度 mipmap PNG（2026-11-01 精简）：
- minSdk 30 起 `mipmap-anydpi-v26` 恒定匹配，密度位图在系统里永远取不到。
- 原先三处引用均已失效：About 对话框已删除；桌面快捷方式（只在游戏 exe 图标提取失败时
  才回落到 R.mipmap.ic_launcher）与 manifest 的 android:icon（被 anydpi-v26 抢占）实测走不到。
- 与 DraftPeek 同构：工程内只留矢量，另存一份 512 位图作商店/文档素材。

本脚本现在只产出两样：
    app/src/main/res/drawable/icon_notification.png   通知栏小图标（48px 白剪影 + 透明底）
    docs/icon/ic_launcher-512.png                     512 满幅归档图（商店/README 用）

用法：
    python tools/gen_brand_icons.py            # 覆盖写出
    python tools/gen_brand_icons.py --check    # 只校验目标文件是否已存在

产物均为 git 跟踪文件，`git checkout -- <path>` 即可回退。
"""
import json
import os
import struct
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
DOCS_ICON = os.path.join(ROOT, "docs", "icon")
GEOMETRY_PATH = os.path.join(ROOT, "tools", "icon-geometry-g34o.json")

WHITE = (0xFF, 0xFF, 0xFF)

STORE_SIZE = 512            # 归档位图边长
NOTIF_SIZE = 48             # 通知小图标边长

CANVAS = 108.0              # 自适应图标画布边长（dp），与前景矢量同尺度
NOTIF_SPAN = 0.92           # 通知图标里母题占画布的比例（留出安全边距）


def hex_rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


# ---- 几何基元（一律在 108 空间描述）----

def segment(x1, y1, x2, y2, half_w):
    """线段 + 两端圆帽（点到线段距离 <= half_w）：对应 PIL 的 line + 端点 ellipse。"""
    dx, dy = x2 - x1, y2 - y1
    len_sq = dx * dx + dy * dy

    def inside(x, y):
        t = ((x - x1) * dx + (y - y1) * dy) / len_sq if len_sq else 0.0
        if t < 0.0:
            t = 0.0
        elif t > 1.0:
            t = 1.0
        cx, cy = x1 + t * dx, y1 + t * dy
        return (x - cx) ** 2 + (y - cy) ** 2 <= half_w * half_w
    return inside


def circle(cx, cy, r):
    def inside(x, y):
        return (x - cx) ** 2 + (y - cy) ** 2 <= r * r
    return inside


def rrect(x0, y0, x1, y1, r):
    def inside(x, y):
        dx = max(x0 + r - x, x - (x1 - r), 0.0)
        dy = max(y0 + r - y, y - (y1 - r), 0.0)
        return dx * dx + dy * dy <= r * r
    return inside


def load_geometry(path):
    """读 geometry.json，按 1.08 换算到 108 空间。

    返回 (layers, bg, stroke_half, motif_box)：
      layers 为 [(inside_fn, (r, g, b)), ...]，后画者压在前者之上；
      motif_box 为母题包围盒（含线宽外扩），供通知图标放大用。
    """
    with open(path, encoding="utf-8") as f:
        geo = json.load(f)
    k = CANVAS / float(geo["size"])          # 100 空间 -> 108 空间
    half_w = geo["sw"] * k / 2.0
    layers = []
    xs, ys = [], []
    for c in geo["chords"]:
        p1 = (c["x1"] * k, c["y1"] * k)
        p2 = (c["x2"] * k, c["y2"] * k)
        layers.append((segment(p1[0], p1[1], p2[0], p2[1], half_w), hex_rgb(c["col"])))
        xs += [p1[0], p2[0]]
        ys += [p1[1], p2[1]]
    eye = geo["eye"]
    ecx, ecy, er = eye["cx"] * k, eye["cy"] * k, eye["r"] * k
    layers.append((circle(ecx, ecy, er), hex_rgb(eye["col"])))
    xs += [ecx - er, ecx + er]
    ys += [ecy - er, ecy + er]
    motif_box = (min(xs) - half_w, min(ys) - half_w, max(xs) + half_w, max(ys) + half_w)
    return layers, hex_rgb(geo["bg"]), half_w, motif_box


LAYERS, BG, STROKE_HALF, MOTIF_BOX = load_geometry(GEOMETRY_PATH)
# 通知栏小图标：系统只取 alpha 自行着色，故所有层一律用同一颜色（保留层序与形状）。
MONO_LAYERS = [(fn, WHITE) for fn, _ in LAYERS]


# ---- 光栅化 ----

def resolve(gx, gy, layers, bg_shape, bg_color):
    """该采样点的颜色：最上层命中的图形 > 底盘 > 全透明。"""
    for inside, color in reversed(layers):
        if inside(gx, gy):
            return color[0], color[1], color[2], 255
    if bg_shape is not None and bg_shape(gx, gy):
        return bg_color[0], bg_color[1], bg_color[2], 255
    return 0, 0, 0, 0


def render(size, layers, bg_shape=None, bg_color=BG, ss=4, fit=None, span=1.0):
    """把 108 空间的图形映射到 size×size 画布。

    fit=None 时 108 画布满幅映射，与前景矢量同尺度；给出源包围盒 fit 时把该子区域放大
    填满 span*size 并居中——通知图标需要，否则母题只占画布中心一小块。
    超采样按面积平均，并用预乘 alpha 累加再除回，避免透明底边缘压暗。
    """
    if fit is None:
        scale = size / CANVAS
        off_x = off_y = 0.0
    else:
        sx0, sy0, sx1, sy1 = fit
        box = size * span
        scale = min(box / max(sx1 - sx0, 1e-6), box / max(sy1 - sy0, 1e-6))
        off_x = (size - (sx1 - sx0) * scale) / 2.0 - sx0 * scale
        off_y = (size - (sy1 - sy0) * scale) / 2.0 - sy0 * scale

    step = 1.0 / ss
    rows = []
    for py in range(size):
        row = bytearray()
        for px in range(size):
            sr = sg = sb = sa = 0.0
            n = 0
            for sy in range(ss):
                gy = (py + (sy + 0.5) * step - off_y) / scale
                for sx in range(ss):
                    gx = (px + (sx + 0.5) * step - off_x) / scale
                    r, g, b, a = resolve(gx, gy, layers, bg_shape, bg_color)
                    af = a / 255.0
                    sr += r * af
                    sg += g * af
                    sb += b * af
                    sa += af
                    n += 1
            alpha = sa / n
            if alpha > 0.0:
                r = int(round(sr / sa))
                g = int(round(sg / sa))
                b = int(round(sb / sa))
            else:
                r = g = b = 0
            row += struct.pack("BBBB", r, g, b, int(round(alpha * 255)))
        rows.append(bytes(row))
    return rows


def write_png(path, size, rows):
    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    raw = b"".join(b"\x00" + r for r in rows)
    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    blob = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(blob)


def render_store(size=STORE_SIZE, corner=0.22):
    """512 归档图：满幅 G34O + 22% 圆角（与 DraftPeek store-png 同规则）。"""
    return render(size, LAYERS, rrect(0, 0, CANVAS, CANVAS, CANVAS * corner), BG)


def write_notification(path, size=NOTIF_SIZE):
    """通知小图标：纯白弦线剪影 + 透明底（系统只取 alpha 做遮罩并自行着色）。"""
    rows = render(size, MONO_LAYERS, None, WHITE, fit=MOTIF_BOX, span=NOTIF_SPAN)
    write_png(path, size, rows)


def targets():
    return [
        os.path.join(RES, "drawable", "icon_notification.png"),
        os.path.join(DOCS_ICON, "ic_launcher-512.png"),
    ]


def main():
    check = "--check" in sys.argv
    made, missing = [], []

    notif = os.path.join(RES, "drawable", "icon_notification.png")
    store = os.path.join(DOCS_ICON, "ic_launcher-512.png")

    for path in targets():
        if check:
            (made if os.path.isfile(path) else missing).append(path)
            continue
        d = os.path.dirname(path)
        if not os.path.isdir(d):
            os.makedirs(d)
        if path == notif:
            write_notification(path)
        else:
            write_png(path, STORE_SIZE, render_store())
        made.append(path)

    if check:
        print("已存在 %d / 缺失 %d" % (len(made), len(missing)))
        for p in missing:
            print("  缺失：%s" % p)
        return 1 if missing else 0

    print("G34O 偏心窥圆：弦 %d 条 + 眼 1 枚，底色 #%s" % (
        len(LAYERS) - 1, "%02X%02X%02X" % BG))
    print("已生成 %d 个位图：" % len(made))
    for p in made:
        print("  %s" % os.path.relpath(p, ROOT))
    return 0


if __name__ == "__main__":
    sys.exit(main())
