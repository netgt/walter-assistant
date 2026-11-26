#!/usr/bin/env python3
"""Собрать capability-матрицу по dumpsys с IQOO. Не содержит пользовательских данных."""
import collections
import datetime
import re
from pathlib import Path

ROOT = Path('/tmp')
LAUNCHER = ROOT / 'iqoo_launcher.txt'
DUMPSYS = ROOT / 'iqoo_dumpsys_package.txt'
LABELS = ROOT / 'iqoo_app_labels.tsv'
OUT = Path(__file__).resolve().parents[1] / 'docs' / 'APP-ACTIONS.md'

MANUAL = {
 'com.android.mms': 'Сообщения', 'com.android.settings': 'Настройки',
 'com.android.vending': 'Google Play', 'com.avito.android': 'Авито',
 'com.cloudedge.smarteye': 'CloudEdge', 'com.deepseek.chat': 'DeepSeek',
 'com.example.dispatcher': 'Walter', 'com.google.android.apps.authenticator2': 'Authenticator',
 'com.google.android.apps.docs.editors.docs': 'Документы',
 'com.google.android.apps.docs.editors.sheets': 'Таблицы',
 'com.google.android.apps.docs.editors.slides': 'Презентации',
 'com.google.android.apps.maps': 'Карты', 'com.google.android.apps.nbu.files': 'Files',
 'com.google.android.apps.photos': 'Фото', 'com.google.android.apps.safetyhub': 'Безопасность',
 'com.google.android.apps.tachyon': 'Meet', 'com.google.android.calendar': 'Календарь',
 'com.google.android.gm': 'Gmail', 'com.google.android.googlequicksearchbox': 'Google',
 'com.google.android.youtube': 'YouTube', 'com.huawei.appmarket': 'AppGallery',
 'com.idamob.tinkoff.android': 'Т-Банк', 'com.kaspersky.passwordmanager': 'Пароли',
 'com.moonshot.kimichat': 'Kimi', 'com.niksoftware.snapseed': 'Snapseed',
 'com.rosseti.rossetimr': 'Россети МР', 'com.tongyi.intl': 'Qwen', 'com.tplink.iot': 'Tapo',
 'com.tumblr': 'Tumblr', 'com.vivo.browser': 'Браузер', 'com.vk.vkvideo': 'VK Видео',
 'com.xiaomi.smarthome': 'Xiaomi Home', 'com.yandex.aliceapp': 'Алиса AI',
 'com.yandex.browser': 'Яндекс Браузер', 'com.yandex.calendar.app': 'Я Календарь',
 'io.github.zapretkvn.android': 'Zapret KVN', 'net.openvpn.openvpn': 'OpenVPN Connect',
 'org.amnezia.vpn': 'AmneziaVPN', 'org.telegram.messenger': 'Telegram',
 'ru.alfabank.mobile.android': 'Альфа-Банк', 'ru.loop.app': 'Loop',
 'ru.megafon.mlk': 'МегаФон', 'ru.nspk.mirpay': 'Mir Pay', 'ru.oneme.app': 'MAX',
 'ru.ozon.app.android': 'OZON', 'ru.rshb.dbo': 'РСХБ', 'ru.sberbankmobile': 'СберБанк',
 'ru.tinkoff.mvno': 'Т-Мобайл', 'ru.vk.store': 'RuStore', 'ru.vkusvill': 'ВкусВилл',
 'ru.yandex.androidkeyboard': 'Яндекс Клавиатура', 'ru.yandex.disk': 'Яндекс Диск',
 'ru.yandex.key': 'Яндекс ID (Ключ)', 'ru.yandex.mail': 'Яндекс Почта',
 'ru.yandex.music': 'Яндекс Музыка', 'ru.yandex.taxi': 'ЯндексGo',
 'ru.yandex.yandexmaps': 'Яндекс Карты'
}

def labels():
    out = dict(MANUAL)
    if LABELS.exists():
        for line in LABELS.read_text(encoding='utf-8').splitlines():
            if '\t' in line:
                pkg, label = line.split('\t', 1)
                if label.strip(): out[pkg] = label.strip()
    return out

def launcher():
    text = LAUNCHER.read_text(encoding='utf-8')
    out = collections.defaultdict(list)
    for pkg, activity in re.findall(r'^\s+([\w.]+)/(\S+)', text, re.M):
        out[pkg].append(activity)
    return out

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

def action(pkg, values):
    if 'mailto' in values: return 'Входящие и новое письмо через mailto:'
    if 'smsto' in values: return 'Новое SMS через smsto:'
    if 'tel' in values: return 'Набор номера через tel:'
    if 'geo' in values: return 'Карта/точка через geo:'
    custom = sorted(v for v in values if v not in {'http', 'https', 'content', 'file', 'geo'})
    if custom: return 'Deep link: ' + ', '.join(custom[:8]) + ('…' if len(custom) > 8 else '')
    return 'Главный экран приложения'

def main():
    labs, launch, sch = labels(), launcher(), schemes()
    lines = [
        '# Реальные действия приложений на IQOO 13', '',
        f'Снято с устройства: {datetime.date.today().isoformat()}.',
        'Основание: `cmd package query-activities` и `dumpsys package` (intent-filters).',
        'Для остальных действий — только главный экран. Платежи и необратимые шаги не автоматизируются.', '',
        f'Launcher-пакетов: **{len(launch)}**. Схемы найдены у **{sum(1 for p in launch if sch.get(p))}** пакетов.', '',
        '| Приложение | Пакет | Launcher activity | Действие по intent-filters |',
        '|---|---|---|---|'
    ]
    for pkg in sorted(launch, key=lambda p: labs.get(p, p).lower()):
        values = sch.get(pkg, set())
        acts = '<br>'.join(launch[pkg])
        lines.append(f'| {labs.get(pkg, pkg)} | `{pkg}` | `{acts}` | {action(pkg, values)} |')
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print(OUT)

if __name__ == '__main__':
    main()
