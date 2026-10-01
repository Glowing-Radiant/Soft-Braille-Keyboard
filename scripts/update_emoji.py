#!/usr/bin/env python3
"""Generate the emoji names and keywords used by emoji search.

For every language of a braille table in res/xml/tablelist.xml, the Unicode
CLDR emoji annotations are written to app/src/main/assets/emoji/<lang>.txt,
one emoji per line in the Unicode emoji order:

    emoji<TAB>name<TAB>keyword|keyword|...

Only fully qualified emoji are kept, without skin tone variants. Symbols
CLDR also names, such as brackets, are left out.

Usage:
    python scripts/update_emoji.py
"""

import json
import os
import urllib.request
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TABLE_LIST = os.path.join(ROOT, 'app', 'src', 'main', 'res', 'xml',
                          'tablelist.xml')
DEST = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'emoji')

CLDR_VERSION = '48.2.3'
CLDR_URL = ('https://raw.githubusercontent.com/unicode-org/cldr-json/'
            + CLDR_VERSION + '/cldr-json/%s/%s/annotations.json')
CLDR_PACKAGES = (('cldr-annotations-full', 'annotations'),
                 ('cldr-annotations-derived-full', 'annotationsDerived'))
EMOJI_TEST_URL = 'https://unicode.org/Public/emoji/latest/emoji-test.txt'
LICENSE_URL = 'https://www.unicode.org/license.txt'

# Asset names for table languages CLDR names differently, and other CLDR
# locales whose keywords are added, such as Serbian in Latin letters.
CLDR_LOCALES = {
    'nb': ['no'],
    'sr': ['sr', 'sr-Latn'],
}
# Table locales with their own asset, as CLDR has a separate locale.
EXTRA_ASSETS = {
    'zh_TW': ('zh-Hant', ['zh-Hant']),
}
SKIN_TONES = {chr(c) for c in range(0x1F3FB, 0x1F400)}
VARIATION_SELECTOR = '️'


def fetch(url):
    with urllib.request.urlopen(url) as response:
        return response.read().decode('utf-8')


def table_languages():
    tree = ET.parse(TABLE_LIST)
    assets = {}
    for table in tree.getroot().iter('table'):
        locale = table.get('locale')
        if locale in EXTRA_ASSETS:
            name, cldr = EXTRA_ASSETS[locale]
        else:
            name = locale.split('_')[0]
            cldr = CLDR_LOCALES.get(name, [name])
        assets[name] = cldr
    return assets


# The fully qualified emoji in order, without skin tones or components.
def emoji_order():
    order = []
    for line in fetch(EMOJI_TEST_URL).splitlines():
        if ';' not in line or line.startswith('#'):
            continue
        codes, status = line.split('#')[0].split(';')
        if status.strip() != 'fully-qualified':
            continue
        emoji = ''.join(chr(int(c, 16)) for c in codes.split())
        if not SKIN_TONES.intersection(emoji):
            order.append(emoji)
    return order


# CLDR names and keywords keyed by emoji without variation selectors.
def annotations(locale):
    names = {}
    keywords = {}
    for package, kind in CLDR_PACKAGES:
        data = json.loads(fetch(CLDR_URL % (package + '/' + kind, locale)))
        for emoji, info in data[kind]['annotations'].items():
            key = emoji.replace(VARIATION_SELECTOR, '')
            tts = info.get('tts', [])
            if tts and key not in names:
                names[key] = tts[0]
            keywords.setdefault(key, [])
            for word in info.get('default', []):
                if word not in keywords[key]:
                    keywords[key].append(word)
    return names, keywords


def clean(text):
    return text.replace('\t', ' ').replace('|', ' ').replace('\n', ' ')


def write_language(name, locales, order):
    names = {}
    keywords = {}
    for locale in locales:
        more_names, more_keywords = annotations(locale)
        for key, value in more_names.items():
            names.setdefault(key, value)
        for key, words in more_keywords.items():
            merged = keywords.setdefault(key, [])
            merged.extend(w for w in words if w not in merged)

    lines = []
    for emoji in order:
        key = emoji.replace(VARIATION_SELECTOR, '')
        if key not in names:
            continue
        words = [clean(w) for w in keywords.get(key, [])
                 if w != names[key]]
        lines.append('%s\t%s\t%s' % (emoji, clean(names[key]),
                                     '|'.join(words)))
    with open(os.path.join(DEST, name + '.txt'), 'w', encoding='utf-8',
              newline='\n') as f:
        f.write('\n'.join(lines) + '\n')
    return len(lines)


def main():
    os.makedirs(DEST, exist_ok=True)
    for old in os.listdir(DEST):
        os.remove(os.path.join(DEST, old))
    order = emoji_order()
    for name, locales in sorted(table_languages().items()):
        print('%s: %d emoji' % (name, write_language(name, locales, order)))
    with open(os.path.join(DEST, 'LICENSE'), 'w', encoding='utf-8',
              newline='\n') as f:
        f.write('Emoji names and keywords from the Unicode CLDR %s.\n\n'
                % CLDR_VERSION)
        f.write(fetch(LICENSE_URL))


if __name__ == '__main__':
    main()
