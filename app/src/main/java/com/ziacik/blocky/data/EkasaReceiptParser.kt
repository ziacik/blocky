package com.ziacik.blocky.data

import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.model.ReceiptItem
import com.ziacik.blocky.normalization.ItemNormalizer
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Locale

class EkasaReceiptParser(
	private val normalizer: ItemNormalizer,
) : ReceiptParser {
	override fun parse(json: String): Receipt {
		val root = JSONObject(json)
		if (root.optInt("returnValue", -1) != 0) {
			val message = root.optString("errorDescription").ifBlank { "Doklad sa v eKase nenašiel." }
			error(message)
		}

		val receipt = root.getJSONObject("receipt")
		val organization = receipt.optJSONObject("organization")
		val merchant = organization?.optString("name")
			?.takeIf(String::isNotBlank)
			?: receipt.optJSONObject("unit")?.optString("name")?.takeIf(String::isNotBlank)
			?: "Neznámy obchodník"
		val id = receipt.getString("receiptId")
		val issuedAt = parseDate(receipt.optString("issueDate"))
		val itemsJson = receipt.optJSONArray("items")
		val items = buildList {
			if (itemsJson != null) {
				for (index in 0 until itemsJson.length()) {
					val item = itemsJson.getJSONObject(index)
					val originalName = item.optString("name").ifBlank { "Položka" }
					val normalized = normalizer.normalize(originalName)
					add(
						ReceiptItem(
							originalName = originalName,
							canonicalName = normalized.canonicalName,
							category = normalized.category,
							subcategory = normalized.subcategory,
							quantity = item.optDouble("quantity", 1.0),
							totalCents = toCents(item.optDouble("price", 0.0)),
							vatRate = item.optDouble("vatRate").takeUnless { it.isNaN() },
						)
					)
				}
			}
		}
		val totalCents = if (receipt.has("totalPrice")) {
			toCents(receipt.optDouble("totalPrice", 0.0))
		} else {
			items.sumOf(ReceiptItem::totalCents)
		}

		return Receipt(
			id = id,
			merchant = merchant,
			issuedAt = issuedAt,
			totalCents = totalCents,
			items = items,
			rawJson = json,
		)
	}

	private fun parseDate(value: String): Long = runCatching {
		SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.ROOT).parse(value)?.time
	}.getOrNull() ?: System.currentTimeMillis()

	private fun toCents(value: Double): Long = BigDecimal.valueOf(value)
		.movePointRight(2)
		.setScale(0, RoundingMode.HALF_UP)
		.longValueExact()
}
