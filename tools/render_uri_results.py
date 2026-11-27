#!/usr/bin/env python3
"""Преобразование URI-VERIFY-RESULTS.tsv в читаемую таблицу и сводку."""
import collections
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'docs' / 'URI-VERIFY-RESULTS.tsv'
OUT = ROOT / 'docs' / 'URI-VERIFY.md'
EXTRA_CALLBACK = ('growing.', 'okhttp', 'browser', 'passport', 'yxc', 'login',
                  'auth', 'recaptcha', 'spaysdk', 'bank100', 'fbconnect', 'esia',
                  'oauth', 'callback', 'redirect', 'payment', 'sbp', 'plus', 'sberbankidlogin')


def callback_scheme(uri):
    value = uri.lower()
    return value.startswith(EXTRA_CALLBACK)


def main():
    rows = [line.split('\t') for line in SRC.read_text(encoding='utf-8').splitlines()[1:] if line.strip()]
    prepared = []
    for pkg, uri, status, _ in rows:
        shown = 'CALLBACK' if callback_scheme(uri) else status
        prepared.append((pkg, uri, shown))
    counts = collections.Counter(row[2] for row in prepared)
    lines = [
        '# Проверка root URI на IQOO',
        '',
        '`OK` означает, что открылось целевое приложение. Это не доказательство выполнения операции внутри него.',
        '`FAIL` — root URI не открыл целевое приложение. `CALLBACK` — служебная схема возврата/авторизации, а не пользовательское действие.',
        '',
        f'Всего проверено: **{len(rows)}**. OK: **{counts["OK"]}**, FAIL: **{counts["FAIL"]}**, CALLBACK: **{counts["CALLBACK"]}**.',
        '',
        '| Пакет | URI | Результат |',
        '|---|---|---|'
    ]
    for pkg, uri, shown in prepared:
        lines.append(f'| `{pkg}` | `{uri}` | {shown} |')
    OUT.write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print(OUT)


if __name__ == '__main__':
    main()
