# Bločky

Android appka na evidenciu a analýzu reálnych nákupov zo slovenských eKasa bločkov.

## Aktuálny vertical slice

1. Naskenuje QR kód bločku cez Google Code Scanner.
2. Podľa QR načíta detail dokladu z eKasa vrátane položiek.
3. Uloží bloček a jednotlivé položky lokálne do SQLite.
4. Normalizuje známe názvy položiek a priradí kategóriu a podkategóriu.
5. Ukáže celkové výdavky, najväčšie kategórie, najdrahšie produkty a posledné bločky.

## Normalizácia a AI

`ItemNormalizer` je oddelený od importu a persistence. Aktuálny `HeuristicItemNormalizer` je offline fallback pre jasné prípady.

Produkčná AI normalizácia bude dávkovať iba neznáme názvy cez vlastný backend, aby AI API kľúč nebol uložený v APK. Výsledok sa bude cacheovať podľa normalizovaného názvu a obchodníka. Cieľom nie je iba kategória, ale aj kanonický produkt: napr. `TEHLA EIDAM 30%` a `EIDAM BLOK 400G` majú skončiť pod `Eidam`.

## Build

```bash
./gradlew assembleDebug
```

CI beží na push a pull request do `master`, spúšťa unit testy, build a ukladá debug APK ako GitHub Actions artifact.
