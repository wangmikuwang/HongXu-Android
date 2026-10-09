"""Builds the bundled default font from the original LXGW WenKai Regular.

Keeps every non-ideograph glyph, the GB2312 and Big5 common ideographs, and every ideograph used by the app's own
text, then renames the font: the OFL reserves the original names for unmodified versions.

    pip install fonttools
    python third_party/lxgw-wenkai/subset.py        (run from the app root, after changing the font or UI text)
"""
import re
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path(__file__).with_name('LXGWWenKai-Regular.ttf')
TARGET = ROOT / 'app/src/main/res/font/bundled_kai_regular.ttf'
FAMILY = 'Bundled Kai'
# The app's own text; FontCoverageTest checks the same files so new UI text cannot fall outside the subset.
APP_TEXT = ['app/src/main/java/**/*.kt', 'app/src/main/res/values*/strings.xml', 'app/src/main/assets/**/*.md', 'app/src/main/assets/**/*.json']


def is_ideograph(u):
    return 0x3400 <= u <= 0x9FFF or 0xF900 <= u <= 0xFAFF or 0xAC00 <= u <= 0xD7AF or u >= 0x20000


def two_byte(encoding, leads, trails):
    chars = set()
    for a in leads:
        for b in trails:
            try:
                chars.add(bytes([a, b]).decode(encoding))
            except UnicodeDecodeError:
                pass
    return {ord(c) for c in chars if len(c) == 1}


def app_text():
    return {ord(c) for pattern in APP_TEXT for f in ROOT.glob(pattern) for c in f.read_text(encoding='utf-8')}


def main():
    font = TTFont(SOURCE, recalcTimestamp=False)
    available = set(font.getBestCmap())
    gb2312 = two_byte('gb2312', range(0xB0, 0xF8), range(0xA1, 0xFF))
    big5_common = two_byte('cp950', range(0xA4, 0xC7), [*range(0x40, 0x7F), *range(0xA1, 0xFF)])
    keep = {u for u in available if not is_ideograph(u)} | ((gb2312 | big5_common | app_text()) & available)

    options = subset.Options()
    options.layout_features = ['*']
    options.name_IDs = ['*']
    options.hinting = False
    options.notdef_outline = True
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=keep)
    subsetter.subset(font)

    names = font['name']
    for record in list(names.names):
        if record.nameID in (1, 3, 4, 6, 16, 17, 21, 22):
            names.removeNames(nameID=record.nameID)
    for name_id, value in {1: FAMILY, 3: f'{FAMILY} Regular subset', 4: f'{FAMILY} Regular', 6: re.sub(r'\W', '', FAMILY) + '-Regular',
                           10: 'Modified (subsetted) version of the original font under the SIL Open Font License 1.1.'}.items():
        names.setName(value, name_id, 3, 1, 0x409)
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    font.save(TARGET)
    print(f'{TARGET.relative_to(ROOT)}: {len(keep)} characters, {TARGET.stat().st_size / 1e6:.1f} MB')


if __name__ == '__main__':
    main()
