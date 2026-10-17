# Bločky

Android appka na evidenciu a analýzu nákupov zo slovenských eKasa bločkov, rakúskych účteniek SPAR a Wolt objednávok.

## Aktuálny vertical slice

1. Naskenuje eKasa QR cez Google Code Scanner, importuje obrázok/PDF (eKasa alebo SPAR) alebo synchronizuje Wolt.
2. Načíta bloček vrátane jednotlivých položiek.
3. Pošle celý bloček na kategorizáciu: názov obchodníka + všetky položky naraz.
4. Uloží normalizovaný názov, zovšeobecnený názov produktu (bez značky), hlavnú kategóriu, podkategóriu a typ výdavku pre každú položku.
5. Umožní konkrétnu položku ručne opraviť; manuálna oprava má prednosť pred neskoršou AI kategorizáciou.
6. Ukáže výdavky, kategórie, zovšeobecnené produkty a históriu bločkov vrátane rozkliknutia jednotlivých nákupov.

## Rakúsky SPAR

Pri platení naskenuj v SPAR aplikácii svoj SPAR-Code. V SPAR aplikácii otvor **Rechnungen** (z domovskej obrazovky cez ikonu účtenky alebo cez **Mehr**) a ulož konkrétnu digitálnu účtenku. V Bločkoch stlač **+ → Importovať súbor (eKasa / SPAR)** a vyber jej PDF alebo obrázok.

Bločky lokálne extrahujú text priamo z digitálneho PDF (pri fotkách použijú OCR), rozpoznajú položky, množstvá, vratné obaly a záporné riadky zliav a zaradia nákup do príslušného mesiaca; následná AI kategorizácia je rovnaká ako pri eKase. Ak súčet rozpoznaných položiek a zliav nesedí s celkovou sumou, import odmietnu namiesto uloženia chybných štatistík. Každá účtenka má stabilný identifikátor, opätovný import identického súboru nevytvorí duplicitný nákup.

**Limity:** Zatiaľ len rakúsky SPAR/EUROSPAR/INTERSPAR, nie všeobecné rakúske bločky. Parser podporuje formát overený na reálnom digitálnom exporte zo SPAR Kittsee, vrátane dvojriadkových položiek a záloh. V PDF je logo EUROSPAR obrázok, preto samotný textový import môže názov obchodu uložiť len ako SPAR. Pri OCR fotiek a neznámych formátoch hrozí nesprávne rozpoznanie; pri nezhode sumy sa nič neuloží. Hromadný export zo SPAR aplikácie zatiaľ nie je podporovaný.

## Kategorizácia

Hardcoded pravidlá podľa názvov produktov sa nepoužívajú. Bez kontextu zostáva položka `Nezaradené`.

AI klasifikácia pracuje na úrovni celého bločku, takže dostáva aj názov obchodníka a ostatné položky. Výstup musí obsahovať:

- kanonický čitateľný názov konkrétneho produktu (`canonicalName`),
- zovšeobecnený názov na agregáciu naprieč značkami (`genericName`),
- hlavnú kategóriu,
- povinnú podkategóriu,
- typ výdavku: `ESSENTIAL`, `REGULAR` alebo `DISCRETIONARY`,
- confidence od 0 do 1.

Povolené kategórie a podkategórie sú definované v `ExpenseTaxonomy`. Odpoveď mimo taxonómie sa odmietne.

Manuálna oprava sa ukladá iba pre konkrétnu položku daného bločku, vrátane zovšeobecneného názvu. Nie je tu používateľský rule engine. Kategória nie je totožná s produktom: napríklad „Eidam“ zostáva „Eidam“, nie všeobecným „Syrom“. Pri starej databáze sa zachovajú všetky položky a novo doplnené `genericName` sa pri dostupnej AI postupne dopočítava (maximálne 50 bločkov pri jednom spustení). Kým nie je dopočítané, súhrny používajú existujúci `canonicalName`.

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
			"genericName": "Rožok",
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
