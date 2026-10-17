package com.ziacik.blocky.data

import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.model.ReceiptItem
import com.ziacik.blocky.normalization.ItemNormalizer
import org.json.JSONObject
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Reads the text displayed by the Austrian SPAR app's digital receipts.
 * Fails closed: incomplete OCR must not silently distort spending statistics.
 */
class SparReceiptParser(
	private val normalizer: ItemNormalizer,
) {
	fun parse(text: String): Receipt {
		val lines = text
			.replace('\u00a0', ' ')
			.replace('\u202f', ' ')
			.lines()
			.map(String::trim)
			.filter(String::isNotEmpty)

		val merchantIndex = lines.indexOfFirst { MERCHANT.matches(it) }
		require(merchantIndex >= 0) { "Súbor neobsahuje rakúsku účtenku SPAR." }

		val totalIndex = lines.indexOfFirst { TOTAL.matches(it) }
		require(totalIndex > merchantIndex) { "Na účtenke SPAR sa nepodarilo prečítať celkovú sumu." }
		val totalCents = cents(checkNotNull(TOTAL.matchEntire(lines[totalIndex])).groupValues[1])
		require(totalCents > 0) { "Účtenka SPAR nemá kladnú celkovú sumu." }

		val receiptDate = DATE.find(text)
			?: throw IllegalArgumentException("Na účtenke SPAR sa nepodarilo prečítať dátum.")
		val parts = receiptDate.groupValues
		val issuedAt = runCatching {
			LocalDateTime.of(
				parts[3].toInt(), parts[2].toInt(), parts[1].toInt(),
				parts[4].ifBlank { "0" }.toInt(),
				parts[5].ifBlank { "0" }.toInt(),
				parts[6].ifBlank { "0" }.toInt(),
			).atZone(ZoneId.of("Europe/Vienna")).toInstant().toEpochMilli()
		}.getOrElse {
			throw IllegalArgumentException("Dátum na účtenke SPAR je neplatný.", it)
		}

		val startIndex = lines.subList(merchantIndex + 1, totalIndex)
			.indexOfFirst { it.equals("EUR", ignoreCase = true) }
			.let { if (it < 0) merchantIndex + 1 else merchantIndex + 2 + it }
		val items = lines.subList(startIndex, totalIndex).mapNotNull { line ->
			val match = PRICE.matchEntire(line) ?: return@mapNotNull null
			val name = match.groupValues[1].trim()
			if (name.isBlank() || NON_ITEMS.containsMatchIn(name) || QUANTITY_ONLY.matches(name)) {
				return@mapNotNull null
			}
			val amount = cents(match.groupValues[2])
			val normalized = normalizer.normalize(name)
			ReceiptItem(
				originalName = name,
				canonicalName = normalized.canonicalName,
				category = normalized.category,
				subcategory = normalized.subcategory,
				quantity = 1.0,
				totalCents = amount,
				vatRate = null,
			)
		}

		require(items.isNotEmpty()) { "Na účtenke SPAR sa nepodarilo rozpoznať položky." }
		require(items.sumOf { it.totalCents } == totalCents) {
			"Položky a zľavy SPAR nesedia s celkovou sumou. Skús kvalitnejší súbor účtenky."
		}

		val merchant = MERCHANT.matchEntire(lines[merchantIndex])!!.groupValues[1].uppercase()
		val fingerprint = lines.joinToString("\n").lowercase().toByteArray(Charsets.UTF_8)
		val hash = MessageDigest.getInstance("SHA-256")
			.digest(fingerprint)
			.joinToString("") { "%02x".format(it) }

		return Receipt(
			id = "spar:" + hash,
			merchant = merchant,
			issuedAt = issuedAt,
			totalCents = totalCents,
			items = items,
			rawJson = JSONObject()
				.put("source", "spar")
				.put("currency", "EUR")
				.put("text", text)
				.toString(),
		)
	}

	private fun cents(value: String): Long = BigDecimal(value.removeSuffix("-").replace(',', '.'))
		.movePointRight(2)
		.longValueExact()
		.let { if (value.startsWith("-") || value.endsWith("-")) -kotlin.math.abs(it) else it }

	private companion object {
		val MERCHANT = Regex("""^(EUROSPAR|INTERSPAR|SPAR)(?:\s.*)?$""", RegexOption.IGNORE_CASE)
		val TOTAL = Regex(
			"""^(?:SUMME|GESAMTSUMME|GESAMTBETRAG|ZU\s+ZAHLEN)\s*(?:EUR|€)?\s*(\d+[,.]\d{2})\s*(?:EUR|€)?$""",
			RegexOption.IGNORE_CASE,
		)
		val PRICE = Regex("""^(.+?)\s+([+-]?\d+[,.]\d{2}-?)(?:\s+[A-D])?$""")
		val DATE = Regex("""\b(\d{2})\.(\d{2})\.(\d{4})(?:\s+(\d{2}):(\d{2})(?::(\d{2}))?)?\b""")
		val QUANTITY_ONLY = Regex("""\d+(?:[,.]\d+)?\s*(?:STK\.?|KG|G|L)?\s*[xX*]\s*\d+[,.]\d{2}""", RegexOption.IGNORE_CASE)
		val NON_ITEMS = Regex("""\b(?:UST|MWST|STEUER|NETTO|BRUTTO|ZWISCHENSUMME|RÜCKGELD|GEGEBEN)\b""", RegexOption.IGNORE_CASE)
	}
}
