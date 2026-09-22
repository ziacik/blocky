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

## AI kategorizácia

Pre osobný build môže appka volať OpenAI priamo. Nastav `OPENAI_API_KEY` pri builde:

```bash
OPENAI_API_KEY="tvoj-api-key" ./gradlew assembleDebug
```

alebo pri deployi:

```bash
OPENAI_API_KEY="tvoj-api-key" ./scripts/deploy.sh
```

Používa sa OpenAI Responses API so Structured Outputs a model `gpt-5.6-luna`. Názov obchodníka aj celý zoznam položiek idú v jednom requeste.

**Pozor:** pri tomto režime je API key súčasťou výsledného APK. Je to určené na vlastný build do vlastného telefónu; APK s vloženým kľúčom nezverejňuj.

Ak `OPENAI_API_KEY` nie je nastavený, zostáva podporovaný vlastný backend cez endpoint:

```bash
export BLOCKY_CATEGORIZATION_ENDPOINT="https://example.com/categorize"
./gradlew assembleDebug
```

Alternatívne sa dá použiť Gradle property rovnakého mena.

Ak nie je nastavený ani API key ani endpoint, alebo kategorizácia zlyhá, import bločku pokračuje a položky zostanú nezaradené namiesto pádu aplikácie. Po nakonfigurovaní endpointu sa pri štarte do-kategorizujú aj existujúce nezaradené bločky.

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
