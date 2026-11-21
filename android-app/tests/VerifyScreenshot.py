#!/usr/bin/env python3
"""Contrôle d'écran Android PNG sans dépendance Python externe.
Vérifie que l'émulateur affiche réellement une interface contrastée dorée,
et pas simplement une activité lancée sur un écran noir.
"""
import sys
import struct
import zlib
from pathlib import Path

path = Path(sys.argv[1])
raw = path.read_bytes()
assert raw.startswith(b"\x89PNG\r\n\x1a\n"), "Capture Android absente ou invalide"
off, image, compressed = 8, None, bytearray()
while off < len(raw):
    length = struct.unpack_from(">I", raw, off)[0]
    kind = raw[off + 4:off + 8]
    data = raw[off + 8:off + 8 + length]
    if kind == b"IHDR":
        width, height, depth, color, *_ = struct.unpack(">IIBBBBB", data)
        assert 400 <= width <= 6000 and 700 <= height <= 7000, "Résolution d'écran inattendue"
        assert depth == 8 and color in (2, 6), "Format de capture PNG inattendu"
        image = (width, height, 3 if color == 2 else 4)
    elif kind == b"IDAT":
        compressed.extend(data)
    elif kind == b"IEND":
        break
    off += 12 + length
assert image and compressed, "Capture PNG sans image"
width, height, bpp = image
pixels = zlib.decompress(bytes(compressed))
row_width = width * bpp
previous = bytearray(row_width)
position = 0
colors, gold, pale = set(), 0, 0

def paeth(a, b, c):
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    return a if pa <= pb and pa <= pc else b if pb <= pc else c

for y in range(height):
    mode = pixels[position]
    position += 1
    row = bytearray(pixels[position:position + row_width])
    position += row_width
    assert len(row) == row_width, "Capture PNG tronquée"
    for i in range(row_width):
        left = row[i - bpp] if i >= bpp else 0
        up = previous[i]
        upper_left = previous[i - bpp] if i >= bpp else 0
        if mode == 1: add = left
        elif mode == 2: add = up
        elif mode == 3: add = (left + up) // 2
        elif mode == 4: add = paeth(left, up, upper_left)
        elif mode == 0: add = 0
        else: raise AssertionError("Filtre PNG inconnu")
        row[i] = (row[i] + add) & 255
    if y % 12 == 0:
        for x in range(0, width, 12):
            k = x * bpp
            r, g, b = row[k], row[k + 1], row[k + 2]
            colors.add((r // 16, g // 16, b // 16))
            if r > 140 and 95 < g < 230 and b < 150 and r > b + 60:
                gold += 1
            if r > 135 and g > 135 and b > 135:
                pale += 1
    previous = row

print(f"TerraNova screenshot {width}x{height}, "
      f"{len(colors)} nuances, {gold} échantillons dorés, {pale} clairs")
assert len(colors) >= 15, "Écran presque uniforme : interface probablement non visible"
assert gold >= 10, "Or TerraNova introuvable : accueil possiblement non affiché"
assert pale >= 10, "Textes/contrastes TerraNova absents : écran suspect"
print("Affichage Android contrôlé : OK")
