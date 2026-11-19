#!/usr/bin/env python3
"""Check ownership and the frozen schema-only initial baseline."""
from pathlib import Path
import re
root = Path(__file__).resolve().parents[1] / 'src/main/resources/db'
assert not (root / 'migrations').exists(), 'Parallel migration tree is forbidden'
files = sorted((root / 'migration/mysql').rglob('*.sql'))
assert files, 'No migrations found'
versions = set()
for path in files:
    match = re.fullmatch(r'([BV])(\d+(?:\.\d+)*)__([A-Za-z0-9_]+)\.sql', path.name)
    assert match, f'Invalid migration name: {path.name}'
    kind, version, description = match.groups()
    assert version not in versions, f'Duplicate version: {version}'
    versions.add(version)
    sql = re.sub(r'--[^\n]*|/\*.*?\*/', '', path.read_text(), flags=re.S)
    if kind == 'B':
        assert path.parent.name == 'baseline' and version == '1.0.0'
        assert not re.search(r'\b(INSERT|UPDATE|DELETE|USE|CREATE DATABASE)\b', sql, re.I), 'Initial baseline is schema-only'
    else:
        assert tuple(map(int, version.split('.'))) > (1, 0, 0)
        assert path.parent.name in ('ddl', 'dml')
        assert description.startswith(path.parent.name + '_')
        forbidden = r'\b(INSERT|UPDATE|DELETE)\b' if path.parent.name == 'ddl' else r'\b(CREATE|ALTER|DROP|TRUNCATE)\b'
        assert not re.search(forbidden, sql, re.I), 'Mixed DDL/DML migration'
print(f'Validated {len(files)} MySQL migrations')
