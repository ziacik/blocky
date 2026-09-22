# Bločky

Android appka na evidenciu a analýzu reálnych nákupov zo slovenských eKasa bločkov a Wolt objednávok.

## Aktuálny vertical slice

1. Naskenuje QR kód bločku cez Google Code Scanner alebo synchronizuje Wolt.
2. Načíta bloček vrátane jednotlivých položiek.
3. Pošle celý bloček na kategorizáciu: názov obchodníka + všetky položky naraz.
4. Uloží hlavnú kategóriu, podkategóriu a typ výdavku pre každú položku.
5. Umožní konkrétnu položku ručne opraviť; manuálna oprava má prednosť pred neskoršou AI kategorizáciou.
6. Ukáže výdavky, kategórie, produkty a históriu bločkov.

## Kategorizácia

Hardcoded pravidlá podľa názvov produktov sa nepoužívajú. Bez kontextu zostáva položka `Nezaradené`.

AI klasifikácia pracuje na úrovni celého bločku, takže dostáva aj názov obchodníka a ostatné položky. Výstup musí obsahovať:

- kanonický názov produktu,
- hlavnú kategóriu,
- povinnú podkategóriu,
- typ výdavku: `ESSENTIAL`, `REGULAR` alebo `DISCRETIONARY`,
- confidence od 0 do 1.

Povolené kategórie a podkategórie sú definované v `ExpenseTaxonomy`. Odpoveď mimo taxonómie sa odmietne.

Manuálna oprava sa ukladá iba pre konkrétnu položku daného bločku. Nie je tu používateľský rule engine.

## AI backend

API kľúč sa neukladá do APK. Android volá vlastný backend cez endpoint nakonfigurovaný build-time hodnotou:

```bash
export BLOCKY_CATEGORIZATION_ENDPOINT="https://example.com/categorize"
./gradlew assembleDebug
```

Alternatívne sa dá použiť Gradle property rovnakého mena.

Ak endpoint nie je nastavený alebo backend zlyhá, import bločku pokračuje a položky zostanú nezaradené namiesto pádu aplikácie. Po nakonfigurovaní endpointu sa pri štarte do-kategorizujú aj existujúce nezaradené bločky.

### Request

```json
{
	"merchant": "Lidl Slovenská republika",
	"items": [
		{
			"index": 0,
			"name": "ROHLÍK BIELY 50G",
			"totalCents": 49,
			"quantity": 1.0
		}
	]
}
```

### Response

```json
{
	"items": [
		{
			"index": 0,
			"canonicalName": "Biely rožok",
			"category": "Potraviny",
			"subcategory": "Pečivo",
			"spendingType": "ESSENTIAL",
			"confidence": 0.97
		}
	]
}
```

## Build

```bash
./gradlew assembleDebug
```

CI beží na push a pull request do `master`, spúšťa unit testy, build a ukladá debug APK ako GitHub Actions artifact.
