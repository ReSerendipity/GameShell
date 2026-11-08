#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""拼一套给 box64 用的 aarch64-linux-gnu（glibc）sysroot，供 build_box64_android.sh 使用。

为什么需要它：Android NDK 只带 bionic 的 sysroot，而 rootfs 是 glibc 的，产物必须是
`aarch64-linux-gnu` 的 glibc ELF（见 docs/box64-wine-launch-chain.md §2）。包里只有运行库，
头文件、crt 对象、kernel 头都得自己拼。

**版本必须 ≤ 设备 rootfs 的 glibc**：用比 rootfs 新的 sysroot 编出来的二进制，上机第一句就是
`version 'GLIBC_2.44' not found`（真机实测：rootfs 是 2.39）。查 rootfs 版本：
    adb shell "run-as com.winlator sh -c 'files/rootfs/lib/libc.so.6 | head -1'"

用法：
    python fetch_gnu_sysroot.py --dest D:\\sysroot --glibc 2.39

产物布局（脚本只认这些路径）：
    <dest>/usr/include/{linux,asm-generic,aarch64-linux-gnu/asm}   # linux-libc-dev
    <dest>/usr/include + usr/lib/aarch64-linux-gnu                  # libc6 / libc6-dev
    <dest>/usr/lib/gcc/aarch64-linux-gnu/12/{crtbegin.o,crtend.o,libgcc.a,libgcc_eh.a}
    <dest>/lib -> usr/lib/aarch64-linux-gnu                        # 链接脚本里的 /lib/... 要靠它命中
    <dest>/lib/ld-linux-aarch64.so.1                               # libc.so 脚本 AS_NEEDED 引用
"""
import argparse
import io
import lzma
import os
import re
import shutil
import subprocess
import sys
import tarfile
import urllib.request

UA = {"User-Agent": "curl/8"}
UBUNTU_PORTS = "http://ports.ubuntu.com/ubuntu-ports/pool/main/g/glibc/"
DEBIAN = "https://deb.debian.org/debian/pool/main/"


def fetch(url, timeout=180):
    print("GET", url)
    return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=timeout).read()


def deb_members(data):
    pos, out = 8, []
    while pos < len(data):
        hdr = data[pos:pos + 60]
        if not hdr.strip() or hdr.startswith(b"\x00"):
            break
        name = hdr[0:16].decode("ascii", "replace").strip()
        size = int(hdr[48:58].decode("ascii", "replace").strip() or 0)
        out.append((name.rstrip("/"), data[pos + 60:pos + 60 + size]))
        pos += 60 + size
        pos += (-pos) % 2
    return out


def deb_extract(url, dest):
    data = fetch(url)
    payload = [m for m in deb_members(data) if m[0].startswith("data.tar")][0]
    raw = payload[1]
    if payload[0].endswith(".xz"):
        raw = lzma.decompress(raw)
    elif payload[0].endswith(".zst"):
        import zstandard
        raw = zstandard.ZstdDecompressor().decompress(raw, max_output_size=1 << 30)
    tarfile.open(fileobj=io.BytesIO(raw), mode="r:").extractall(dest)
    print("  -> 解开", os.path.basename(url))


def pool_names(pool):
    html = fetch(pool).decode("utf-8", "replace")
    return sorted(set(re.findall(r'href="([^"]+_arm64\.deb)"', html)))


def pick_pair(pool, glibc):
    names = pool_names(pool)
    libc = [n for n in names if re.match(rf"libc6_{re.escape(glibc)}", n)]
    dev = [n for n in names if re.match(rf"libc6-dev_{re.escape(glibc)}", n)]
    for a in sorted(libc, reverse=True):
        for b in dev:
            if a.split("_")[1] == b.split("_")[1]:
                return a, b
    raise SystemExit(f"镜像里找不到 glibc {glibc} 的 libc6 / libc6-dev 同版本对")


def link_layout(dest):
    """lib/ 必须能让链接脚本里的 /lib/... 命中；Windows 用 junction，失败就复制。"""
    arch = os.path.join(dest, "usr", "lib", "aarch64-linux-gnu")
    libdir = os.path.join(dest, "lib")
    os.makedirs(libdir, exist_ok=True)
    dst = os.path.join(libdir, "aarch64-linux-gnu")
    if os.path.isdir(dst) or os.path.islink(dst):
        return
    cmd = os.path.join(os.environ.get("SystemRoot", r"C:\Windows"), "System32", "cmd.exe")
    try:
        subprocess.run([cmd, "/c", "mklink", "/J", dst, arch], check=True, capture_output=True)
        print("  junction:", dst, "->", arch)
    except Exception:
        shutil.copytree(arch, dst)
        print("  junction 失败，改为复制:", dst)
    ld = os.path.join(arch, "ld-linux-aarch64.so.1")
    if os.path.exists(ld):
        shutil.copy2(ld, os.path.join(libdir, "ld-linux-aarch64.so.1"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dest", required=True, help="sysroot 输出目录")
    ap.add_argument("--glibc", default="2.39", help="glibc 版本前缀，必须 ≤ 设备 rootfs（默认 2.39）")
    a = ap.parse_args()

    os.makedirs(a.dest, exist_ok=True)
    pair = pick_pair(UBUNTU_PORTS, a.glibc)
    print("选定 libc 包:", pair)
    for name in pair:
        deb_extract(UBUNTU_PORTS + name, a.dest)

    deb_extract(DEBIAN + "l/linux/linux-libc-dev_6.1.176-1_arm64.deb", a.dest)
    deb_extract(DEBIAN + "g/gcc-12/libgcc-12-dev_12.5.0-11_arm64.deb", a.dest)

    # Debian 的 asm/ 头只在 aarch64-linux-gnu/ 下，clang 的多 arch 目录会命中；
    # 顶层 usr/include/asm 不存在是正常的，不要伪造。
    link_layout(a.dest)

    checks = [
        "usr/lib/aarch64-linux-gnu/libc.so.6",
        "usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1",
        "lib/aarch64-linux-gnu/libm.so.6",
        "lib/ld-linux-aarch64.so.1",
        "usr/include/linux/errno.h",
        "usr/include/aarch64-linux-gnu/asm/sigcontext.h",
        "usr/lib/gcc/aarch64-linux-gnu/12/crtbegin.o",
        "usr/lib/gcc/aarch64-linux-gnu/12/libgcc.a",
    ]
    bad = [c for c in checks if not os.path.exists(os.path.join(a.dest, c.replace("/", os.sep)))]
    for c in checks:
        print(("  OK   " if c not in bad else "  MISS ") + c)
    if bad:
        raise SystemExit("sysroot 不完整，缺上列路径")
    print("sysroot 就绪:", a.dest)
    print("接着: BOX64_SRC=... NDK=... GLSYSROOT=%s bash tools/box64/build_box64_android.sh <ref> <ver>" % a.dest)


if __name__ == "__main__":
    sys.exit(main())
