#!/usr/bin/env python3
"""
Сверка перечня валют ЦБ РФ с пометкой «курс ЦБ» в справочнике приложения (Currency.cbr).

    python3 -I tools/cbr-check.py

Берёт сегодняшний ответ ЦБ (XML_daily.asp, при ошибке — зеркало cbr-xml-daily.ru) и сравнивает коды валют
с теми, что помечены cbr=true в Currency.kt. СДР (XDR) — не наличные, его в приложении нет.
Расхождение — код выхода 1 и что поправить: список CBR в tools/currencies/gen.py, потом перегенерировать.
Запускается раз в неделю в CI (.github/workflows/cbr-list.yml).
"""
import json
import re
import sys
import urllib.request

KT = "app/src/main/java/app/dirhamrub/domain/wallet/Currency.kt"
XML = "https://www.cbr.ru/scripts/XML_daily.asp"
MIRROR = "https://www.cbr-xml-daily.ru/daily_json.js"
NOT_CASH = {"XDR"}


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "KursPoCheku-ci/1.0"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read()


def cbr_codes():
    try:
        xml = get(XML).decode("cp1251")
        codes = set(re.findall(r"<CharCode>([A-Z]{3})</CharCode>", xml))
        date = re.search(r'<ValCurs[^>]*Date="([^"]+)"', xml)
        if codes:
            return codes, "cbr.ru", date.group(1) if date else "?"
    except Exception as e:  # cbr.ru из-за границы бывает недоступен — тогда зеркало
        print(f"cbr.ru не ответил ({e}), беру зеркало", file=sys.stderr)
    data = json.loads(get(MIRROR).decode("utf-8"))
    return set(data["Valute"]), "cbr-xml-daily.ru", data.get("Date", "?")


def app_codes():
    kt = open(KT, encoding="utf-8").read()
    rows = re.findall(r"^\s+([A-Z]{3})\(.*?, (true|false), NumberStyle", kt, re.M)
    if not rows:
        sys.exit(f"не нашёл валют в {KT}")
    return {c for c, _ in rows}, {c for c, f in rows if f == "true"}


def main():
    published, source, date = cbr_codes()
    published -= NOT_CASH
    known, flagged = app_codes()
    unmarked = sorted((published & known) - flagged)
    unknown = sorted(published - known)
    dropped = sorted(flagged - published)
    print(f"ЦБ ({source}, {date}): {len(published)} валют; в приложении с пометкой «курс ЦБ»: {len(flagged)}")
    ok = True
    if unmarked:
        ok = False
        print("ЦБ публикует, а в приложении «без ЦБ»:", " ".join(unmarked))
    if unknown:
        ok = False
        print("ЦБ публикует, а в справочнике валюты нет:", " ".join(unknown))
    if dropped:
        ok = False
        print("В приложении «курс ЦБ», а ЦБ больше не публикует:", " ".join(dropped))
    if not ok:
        print("Поправить: список CBR в tools/currencies/gen.py, перегенерировать Currency.kt и тест в CurrencyTest.")
        sys.exit(1)
    print("Совпадает.")


if __name__ == "__main__":
    main()
