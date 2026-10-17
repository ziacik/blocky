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
 * SPAR PDF receipts contain item rows with VAT letter suffixes and sometimes
 * a product name on one line followed by "2 x 0,99  1,98 A" on the next.
 * The logo can be graphical: the first extractable "SPAR" may be in the footer.
 * Incomplete extraction is rejected rather than saved as an incorrect expense.
 */
class SparReceiptParser(private val normalizer: ItemNormalizer) {
	fun parse(text: String): Receipt {
		val lines = text
			.replace('\u00a0', ' ')
			.replace('\u202f', ' ')
			.lines()
			.map(String::trim)
			.filter(String::isNotEmpty)

		val merchant = lines.firstOrNull { MERCHANT.matches(it) }?.uppercase()
			?: if (lines.any { it.contains("rksv.spar.at", ignoreCase = true) }) "SPAR" else null
		require(merchant != null) { "Súbor neobsahuje rakúsku účtenku SPAR." }

		val totalIndex = lines.indexOfFirst { TOTAL.matches(it) }
		require(totalIndex >= 0) { "Na účtenke SPAR sa nepodarilo prečítať celkovú sumu." }
		val totalCents = cents(TOTAL.matchEntire(lines[totalIndex])!!.groupValues[1])
		require(totalCents > 0) { "Účtenka SPAR nemá kladnú celkovú sumu." }

		// Purchase time comes before the card-terminal transaction time.
		val date = PURCHASE_DATE.find(text)
			?: DATE_TIME.find(text)
			?: DATE_ONLY.find(text)
			?: throw IllegalArgumentException("Na účtenke SPAR sa nepodarilo prečítať dátum.")
		val parts = date.groupValues
		val issuedAt = runCatching {
			LocalDateTime.of(
				parts[3].toInt(),
				parts[2].toInt(),
				parts[1].toInt(),
				parts.getOrElse(4) { "" }.ifBlank { "0" }.toInt(),
				parts.getOrElse(5) { "" }.ifBlank { "0" }.toInt(),
				parts.getOrElse(6) { "" }.ifBlank { "0" }.toInt(),
			).atZone(ZoneId.of("Europe/Vienna")).toInstant().toEpochMilli()
		}.getOrElse {
			throw IllegalArgumentException("Dátum na účtenke SPAR je neplatný.", it)
		}

		val beforeTotal = lines.subList(0, totalIndex)
		val currencyIndex = beforeTotal.indexOfLast { it.equals("EUR", ignoreCase = true) }
		val headerIndex = beforeTotal.indexOfFirst {
			it.startsWith("Ihr Einkauf am", ignoreCase = true)
		}
		val merchantHeaderIndex = beforeTotal.indexOfFirst { MERCHANT.matches(it) }
		val startIndex = when {
			currencyIndex >= 0 -> currencyIndex + 1
			headerIndex >= 0 -> headerIndex + 1
			merchantHeaderIndex >= 0 -> merchantHeaderIndex + 1
			else -> 0
		}

		var pendingName: String? = null
		val items = buildList {
			for (index in startIndex until totalIndex) {
				val line = lines[index]
				val match = PRICE.matchEntire(line)
				if (match == null) {
					// Only the immediately preceding unpriced line can provide
					// the product name for an ensuing quantity/price line.
					pendingName = line.takeIf {
						QUANTITY_PRICE_ROW.matches(lines.getOrNull(index + 1).orEmpty())
					}
					continue
				}
				val rawName = match.groupValues[1].trim()
				val multiplier = MULTIPLIER.matchEntire(rawName)
				val name = if (multiplier != null) {
					require(!pendingName.isNullOrBlank()) {
						"Na účtenke SPAR chýba názov položky s množstvom."
					}
					pendingName
				} else {
					rawName
				}
				pendingName = null
				if (name.isBlank() || NON_ITEMS.containsMatchIn(name)) continue

				val originalName = if (name.startsWith("PFAND", ignoreCase = true)) {
					name.replace(DEPOSIT_UNIT_PRICE, "")
				} else {
					name
				}
				val quantity = multiplier?.groupValues?.get(1)
					?.replace(',', '.')?.toDouble() ?: 1.0
				require(quantity > 0.0) { "Neplatné množstvo na účtenke SPAR." }
				val normalized = normalizer.normalize(originalName)
				add(
					ReceiptItem(
						originalName = originalName,
						canonicalName = normalized.canonicalName,
						category = normalized.category,
						subcategory = normalized.subcategory,
						quantity = quantity,
						totalCents = cents(match.groupValues[2]),
						vatRate = null,
					)
				)
			}
		}

		require(items.isNotEmpty()) { "Na účtenke SPAR sa nepodarilo rozpoznať položky." }
		require(items.sumOf(ReceiptItem::totalCents) == totalCents) {
			"Položky a zľavy SPAR nesedia s celkovou sumou. Skús kvalitnejší súbor účtenky."
		}

		// The long printed receipt barcode is stable across repeated exports.
		// When absent (e.g. screenshot), fingerprint the parsed purchase only.
		val printedId = lines.firstOrNull { RECEIPT_NUMBER.matches(it) }
		val fingerprint = buildString {
			append(merchant).append('|').append(issuedAt).append('|').append(totalCents)
			items.forEach { item ->
				append('|').append(item.originalName.lowercase())
					.append(':').append(item.quantity).append(':').append(item.totalCents)
			}
		}
		val digest = MessageDigest.getInstance("SHA-256")
			.digest(fingerprint.toByteArray(Charsets.UTF_8))
			.joinToString("") { "%02x".format(it) }

		return Receipt(
			id = "spar:" + (printedId ?: digest),
			merchant = merchant,
			issuedAt = issuedAt,
			totalCents = totalCents,
			items = items,
			// Payment terminal identifiers and card details must not enter storage.
			rawJson = JSONObject()
				.put("source", "spar")
				.put("currency", "EUR")
				.put("purchaseText", lines.subList(startIndex, totalIndex + 1).joinToString("\n"))
				.toString(),
		)
	}

	private fun cents(value: String): Long = BigDecimal(value.removeSuffix("-").replace(',', '.'))
		.movePointRight(2)
		.longValueExact()
		.let { if (value.startsWith("-") || value.endsWith("-")) -kotlin.math.abs(it) else it }

	private companion object {
		val MERCHANT = Regex("""^(?:EUROSPAR|INTERSPAR|SPAR)$""", RegexOption.IGNORE_CASE)
		val TOTAL = Regex(
			"""^(?:SUMME|GESAMTSUMME|GESAMTBETRAG|ZU\s+ZAHLEN)\s*:?\s*(?:EUR|€)?\s*(\d+[,.]\d{2})\s*(?:EUR|€)?$""",
			RegexOption.IGNORE_CASE,
		)
		val PRICE = Regex("""^(.+?)\s+([+-]?\d+[,.]\d{2}-?)(?:\s+[A-Z])?$""")
		val QUANTITY_PRICE_ROW = Regex("""^\d+(?:[,.]\d+)?\s*[xX*]\s*\d+[,.]\d{2}\s+[+-]?\d+[,.]\d{2}(?:\s+[A-Z])?$""")
		val MULTIPLIER = Regex("""^(\d+(?:[,.]\d+)?)\s*[xX*]\s*\d+[,.]\d{2}$""")
		val DEPOSIT_UNIT_PRICE = Regex("""\s+\d+[,.]\d{2}$""")
		val RECEIPT_NUMBER = Regex("""^\d{20,40}$""")
		val PURCHASE_DATE = Regex(
			"""Ihr\s+Einkauf\s+am\s+(\d{2})\.(\d{2})\.(\d{4})\s+um\s+(\d{2}):(\d{2})(?::(\d{2}))?""",
			RegexOption.IGNORE_CASE,
		)
		val DATE_TIME = Regex("""\b(\d{2})\.(\d{2})\.(\d{4})\s+(\d{2}):(\d{2})(?::(\d{2}))?\b""")
		val DATE_ONLY = Regex("""\b(\d{2})\.(\d{2})\.(\d{4})\b""")
		val NON_ITEMS = Regex(
			"""\b(?:UST|MWST|STEUER|NETTO|BRUTTO|ZWISCHENSUMME|RÜCKGELD|GEGEBEN)\b""",
			RegexOption.IGNORE_CASE,
		)
	}
}
