#!/usr/bin/env python3
"""Import a quiesced IQC asset snapshot into an empty, migrated IAC database."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

TABLES = {
    'iqc_quality_agent': 'iac_agent',
    'iqc_quality_agent_version': 'iac_agent_version',
    'iqc_model_profile': 'iac_model_profile',
    'iqc_mcp_server': 'iac_mcp_server',
    'iqc_skill': 'iac_skill',
    'iqc_skill_version': 'iac_skill_version',
    'iqc_agent_chat_session': 'iac_agent_chat_session',
}


def identifier(value):
    if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*', value):
        raise ValueError('Invalid database/table identifier')
    return '`' + value + '`'


def query(client, credentials, database, sql):
    command = [client, '--defaults-extra-file=' + str(credentials), '--batch',
               '--skip-column-names', '--default-character-set=utf8mb4', database]
    result = subprocess.run(command, input=sql.encode(), capture_output=True)
    if result.returncode:
        # Never print driver stderr: statements and credentials may contain secrets.
        raise RuntimeError('Database operation failed; no SQL or credentials logged')
    return result.stdout


def columns(args, side, table):
    database = getattr(args, side + '_database')
    rows = query(args.mysql, getattr(args, side + '_defaults_file'), database,
                 'SHOW COLUMNS FROM ' + identifier(table) + ';').decode().splitlines()
    return [row.split('\t')[0] for row in rows]


def fingerprint(args, side, table, fields):
    sql = 'SELECT JSON_ARRAY(' + ','.join(map(identifier, fields)) + ') FROM ' + identifier(table) + ' ORDER BY `id`;'
    payload = query(args.mysql, getattr(args, side + '_defaults_file'),
                    getattr(args, side + '_database'), sql)
    count = int(query(args.mysql, getattr(args, side + '_defaults_file'),
                      getattr(args, side + '_database'),
                      'SELECT COUNT(*) FROM ' + identifier(table) + ';').strip())
    return {'rows': count, 'sha256': hashlib.sha256(payload).hexdigest()}


def migrate(args):
    if not args.confirm_source_writes_stopped:
        raise ValueError('Stop source management writes and explicitly confirm before import')
    if args.source_database == args.target_database:
        raise ValueError('Source and target database names must differ')
    for side in ['source', 'target']:
        identifier(getattr(args, side + '_database'))
        credentials = getattr(args, side + '_defaults_file')
        if not credentials.is_file() or credentials.stat().st_mode & 0o077:
            raise ValueError('Credential files must exist with owner-only permissions')
    fields = {}
    before = {}
    for old, new in TABLES.items():
        source_fields, target_fields = columns(args, 'source', old), columns(args, 'target', new)
        if source_fields != target_fields:
            raise RuntimeError('Source/target schema column mismatch: ' + old)
        if old == 'iqc_model_profile' and 'pricing_json' in source_fields:
            compatibility_fields = (
                'pricing_json', 'max_output_tokens', 'output_token_parameter',
                'context_window_tokens', 'deployment_ref',
            )
            populated = ' OR '.join(identifier(column) + ' IS NOT NULL' for column in compatibility_fields)
            count = query(args.mysql, args.source_defaults_file, args.source_database,
                          'SELECT COUNT(*) FROM ' + identifier(old) + ' WHERE ' + populated + ';')
            if int(count):
                raise RuntimeError('Source model has compatibility fields that IAC does not yet support')
        fields[old] = source_fields
        before[old] = fingerprint(args, 'source', old, source_fields)
        if fingerprint(args, 'target', new, target_fields)['rows']:
            raise RuntimeError('Target asset tables must all be empty; never overwrite or merge')
    command = [args.mysqldump, '--defaults-extra-file=' + str(args.source_defaults_file),
               '--single-transaction', '--no-create-info', '--complete-insert', '--skip-add-locks',
               '--skip-disable-keys', '--skip-lock-tables', '--set-gtid-purged=OFF', '--hex-blob',
               '--default-character-set=utf8mb4', args.source_database, *TABLES]
    dump = subprocess.run(command, capture_output=True)
    if dump.returncode:
        raise RuntimeError('Source export failed; export content not logged')
    for old in TABLES:
        if fingerprint(args, 'source', old, fields[old]) != before[old]:
            raise RuntimeError('Source changed during export; no target writes performed')
    # Replace table tokens only in INSERT statements, never inside stored JSON/messages/secrets.
    text = dump.stdout.decode()
    pattern = re.compile(r'(?m)^(INSERT INTO )`(' + '|'.join(TABLES) + r')`')
    text = pattern.sub(lambda match: match.group(1) + identifier(TABLES[match.group(2)]), text)
    query(args.mysql, args.target_defaults_file, args.target_database,
          'START TRANSACTION;\n' + text + '\nCOMMIT;\n')
    proof = {}
    for old, new in TABLES.items():
        actual = fingerprint(args, 'target', new, fields[old])
        if actual != before[old]:
            raise RuntimeError('Imported table fingerprint mismatch: ' + new)
        proof[new] = actual
    args.report.write_text(json.dumps({'source': args.source_database, 'target': args.target_database,
                                      'tables': proof, 'verified': True}, indent=2) + '\n')
    os.chmod(args.report, 0o600)
    print('Verified import of 7 asset tables; IDs, versions, configuration, ownership and history preserved')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-defaults-file', type=Path, required=True)
    parser.add_argument('--target-defaults-file', type=Path, required=True)
    parser.add_argument('--source-database', default='iqc_platform')
    parser.add_argument('--target-database', default='iac_platform')
    parser.add_argument('--mysql', default='mysql')
    parser.add_argument('--mysqldump', default='mysqldump')
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--confirm-source-writes-stopped', action='store_true')
    args = parser.parse_args()
    try:
        migrate(args)
    except (ValueError, RuntimeError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
