#!/usr/bin/env python3
"""Проверка root URI на IQOO: am start + фактический top task. Не содержит личных данных."""
import collections
import datetime
import re
import subprocess
from pathlib import Path

ADB = ['/home/netgt/tc/sdk/platform-tools/adb', '-d']
DUMPSYS = Path('/tmp/iqoo_dumpsys_package.txt')
LAUNCHER = Path('/tmp/iqoo_launcher.txt')
OUT = Path(__file__).resolve().parents[1] / 'docs' / 'URI-VERIFY-RESULTS.tsv'
CALLBACK_PARTS = ('auth', 'recaptcha', 'spaysdk', 'bank100', 'fbconnect', 'esia',
                  'oauth', 'callback', 'redirect', 'login', 'payment', 'sbp', 'plus')
SPECIAL = {'tel', 'mailto', 'smsto', 'sms', 'geo'}


def sh(args):
    result = subprocess.run(ADB + args, capture_output=True, text=True, timeout=15)
    return result.stdout + result.stderr


def packages():
    text = LAUNCHER.read_text(encoding='utf-8')
    return sorted(set(re.findall(r'^\s+([A-Za-z0-9_.]+)/', text, re.M)))


def schemes():
    lines = DUMPSYS.read_text(encoding='utf-8').splitlines()
    start = next(i for i, line in enumerate(lines) if line.strip() == 'Schemes:')
    end = next(i for i in range(start + 1, len(lines)) if lines[i].strip() == 'Non-Data Actions:')
    out = collections.defaultdict(set)
    scheme = None
    for line in lines[start + 1:end]:
        m = re.match(r'^      (\S+):\s*$', line)
        if m:
            scheme = m.group(1)
            continue
        m = re.match(r'^\s+[0-9a-f]+\s+([\w.]+)/(\S+)', line)
        if m and scheme:
            out[m.group(1)].add(scheme)
    return out


def main():
    rows = ['package\turi\tstatus\ttop']
    for pkg in packages():
        for scheme in sorted(schemes().get(pkg, set())):
            if scheme in {'http', 'https', 'content', 'file'}:
                continue
            uri = scheme + (':' if scheme in SPECIAL else '://')
            kind = 'CALLBACK' if any(part in scheme.lower() for part in CALLBACK_PARTS) else 'TEST'
            try:
                launch = sh(['shell', 'am', 'start', '-a', 'android.intent.action.VIEW', '-d', uri, '-p', pkg])
            except Exception as exc:
                launch = str(exc)
            try:
                import time
                time.sleep(1.0)
                recent = sh(['shell', 'dumpsys', 'activity', 'recents'])
                top = recent.split('Recent #0', 1)[-1].split('Recent #1', 1)[0]
            except Exception as exc:
                top = str(exc)
            if pkg in top:
                status = 'OK'
            elif kind == 'CALLBACK':
                status = 'CALLBACK'
            else:
                status = 'FAIL'
            top_clean = re.sub(r'\s+', ' ', top).strip()[:350]
            rows.append(f'{pkg}\t{uri}\t{status}\t{top_clean}')
            print(rows[-1], flush=True)
    OUT.write_text('\n'.join(rows) + '\n', encoding='utf-8')
    print(f'WROTE {OUT} rows={len(rows)-1}', flush=True)


if __name__ == '__main__':
    main()
