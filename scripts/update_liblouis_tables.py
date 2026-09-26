#!/usr/bin/env python3
"""Copy the liblouis tables the keyboard needs into the app assets.

Only the tables named in res/xml/tablelist.xml, plus every file they pull in
through `include` directives, are bundled. The full liblouis table set is
~15 MB, most of which the keyboard never uses.

Usage:
    python scripts/update_liblouis_tables.py /path/to/liblouis-X.Y.Z/tables

After updating liblouis, bump LibLouis.TABLES_VERSION so installed copies of
the app re-extract the tables.
"""

import os
import re
import shutil
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TABLE_LIST = os.path.join(ROOT, 'app', 'src', 'main', 'res', 'xml',
                          'tablelist.xml')
DEST = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'liblouis',
                    'tables')
INCLUDE_RE = re.compile(r'^\s*include\s+(\S+)')


def root_tables():
    tree = ET.parse(TABLE_LIST)
    return sorted({t.get('fileName') for t in tree.getroot().iter('table')})


def closure(source_dir, tables):
    seen = set()
    pending = list(tables)
    while pending:
        name = pending.pop()
        if name in seen:
            continue
        path = os.path.join(source_dir, name)
        if not os.path.isfile(path):
            sys.exit('Missing table: %s' % name)
        seen.add(name)
        with open(path, encoding='utf-8', errors='replace') as f:
            for line in f:
                match = INCLUDE_RE.match(line)
                if match:
                    pending.append(match.group(1))
    return sorted(seen)


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    source_dir = sys.argv[1]
    files = closure(source_dir, root_tables())

    if os.path.isdir(DEST):
        shutil.rmtree(DEST)
    os.makedirs(DEST)
    total = 0
    for name in files:
        shutil.copy2(os.path.join(source_dir, name), DEST)
        total += os.path.getsize(os.path.join(DEST, name))
    print('Copied %d files (%d KB) to %s' % (len(files), total // 1024, DEST))


if __name__ == '__main__':
    main()
