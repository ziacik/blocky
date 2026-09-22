package com.ziacik.blocky.data.wolt

import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.model.ReceiptItem
import com.ziacik.blocky.normalization.ItemNormalizer
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

data class WoltHistoryOrder(
	val purchaseId: String,
	val issuedAt: Long?,
	val merchant: String?,
)

class WoltOrderParser(
	private val normalizer: ItemNormalizer,
	private val zoneId: ZoneId = ZoneId.of("Europe/Bratislava"),
) {
	fun latestPurchaseId(json: String): String? =
		latestHistoryOrder(json)?.purchaseId

	fun latestHistoryOrder(json: String): WoltHistoryOrder? {
		val root = JSONObject(json)
		val orders = root.optJSONArray("orders") ?: return null
		if (orders.length() == 0) return null

		var fallback: WoltHistoryOrder? = null
		var newest: WoltHistoryOrder? = null
		var newestTimestamp = Long.MIN_VALUE

		for (index in 0 until orders.length()) {
			val order = orders.optJSONObject(index) ?: continue
			val purchaseId = firstString(order, "purchase_id", "order_id", "id") ?: continue
			val candidate = WoltHistoryOrder(
				purchaseId = purchaseId,
				issuedAt = parseTimestamp(order),
				merchant = firstString(order, "venue_name", "merchant_name"),
			)
			if (fallback == null) fallback = candidate

			val timestamp = candidate.issuedAt ?: continue
			if (timestamp > newestTimestamp) {
				newestTimestamp = timestamp
				newest = candidate
			}
		}

		return newest ?: fallback
	}

	fun historyOrdersSince(json: String, sinceMillis: Long): List<WoltHistoryOrder> {
		val root = JSONObject(json)
		val orders = root.optJSONArray("orders") ?: JSONArray()

		return buildList {
			for (index in 0 until orders.length()) {
				val order = orders.optJSONObject(index) ?: continue
				val purchaseId = firstString(order, "purchase_id", "order_id", "id") ?: continue
				val issuedAt = parseTimestamp(order)
				if (issuedAt != null && issuedAt < sinceMillis) {
					continue
				}
				add(
					WoltHistoryOrder(
						purchaseId = purchaseId,
						issuedAt = issuedAt,
						merchant = firstString(order, "venue_name", "merchant_name"),
					)
				)
			}
		}
	}

	fun parseHistoryPurchaseIds(json: String, sinceMillis: Long): List<String> {
		val root = JSONObject(json)
		val orders = root.optJSONArray("orders") ?: JSONArray()

		return buildList {
			for (index in 0 until orders.length()) {
				val order = orders.optJSONObject(index) ?: continue
				val purchaseId = firstString(order, "purchase_id", "order_id", "id") ?: continue
				val timestamp = parseTimestamp(order)
				if (timestamp == null || timestamp >= sinceMillis) {
					add(purchaseId)
				}
			}
		}
	}

	fun parseOrderDetail(
		json: String,
		fallbackPurchaseId: String? = null,
		fallbackIssuedAt: Long? = null,
		fallbackMerchant: String? = null,
	): Receipt? {
		val order = JSONObject(json)
		val issuedAt = parseTimestamp(order) ?: fallbackIssuedAt ?: return null
		val id = firstString(order, "purchase_id", "order_id", "id")
			?: fallbackPurchaseId
			?: return null
		val merchant = firstString(order, "venue_name")
			?: order.optJSONObject("venue")?.optString("name")?.takeIf(String::isNotBlank)
			?: fallbackMerchant
			?: "Wolt"
		val totalCents = parseOrderDetailTotal(order)
		val items = parseItems(order.optJSONArray("items") ?: JSONArray())

		return Receipt(
			id = "wolt:$id",
			merchant = merchant,
			issuedAt = issuedAt,
			totalCents = totalCents,
			items = items,
			rawJson = order.toString(),
		)
	}

	fun parseOrders(json: String, sinceMillis: Long): List<Receipt> =
		extractOrders(json)
			.mapNotNull(::parseLegacyOrder)
			.filter { it.issuedAt >= sinceMillis }

	private fun extractOrders(json: String): List<JSONObject> {
		val trimmed = json.trim()
		if (trimmed.startsWith("[")) {
			return objects(JSONArray(trimmed))
		}

		val root = JSONObject(trimmed)
		root.optJSONArray("sections")?.let { sections ->
			return buildList {
				for (index in 0 until sections.length()) {
					val section = sections.optJSONObject(index) ?: continue
					addAll(objects(section.optJSONArray("items") ?: JSONArray()))
				}
			}
		}

		for (key in listOf("results", "orders", "items")) {
			root.optJSONArray(key)?.let { return objects(it) }
		}
		return emptyList()
	}

	private fun objects(array: JSONArray): List<JSONObject> = buildList {
		for (index in 0 until array.length()) {
			array.optJSONObject(index)?.let(::add)
		}
	}

	private fun parseLegacyOrder(order: JSONObject): Receipt? {
		val issuedAt = parseTimestamp(order) ?: return null
		val id = firstString(order, "id", "order_id", "purchase_id")
			?: order.optJSONObject("telemetry")?.let { firstString(it, "order_id", "id") }
			?: stableId(order.toString())
		val merchant = order.optJSONObject("venue")?.optString("name")?.takeIf(String::isNotBlank)
			?: firstString(order, "venue_name", "title", "name")
			?: "Wolt"
		val totalCents = parseLegacyTotalCents(order)
		val items = parseItems(order.optJSONArray("items") ?: JSONArray())

		return Receipt(
			id = "wolt:$id",
			merchant = merchant,
			issuedAt = issuedAt,
			totalCents = totalCents,
			items = items,
			rawJson = order.toString(),
		)
	}

	private fun parseItems(items: JSONArray): List<ReceiptItem> = buildList {
		for (index in 0 until items.length()) {
			val item = items.optJSONObject(index) ?: continue
			val name = item.optString("name").takeIf(String::isNotBlank) ?: continue
			val quantity = firstNumber(item, "count", "qty", "quantity")?.toDouble() ?: 1.0
			val lineTotalCents = parseMoneyValue(item.opt("end_amount"))
				?: parseMoneyValue(item.opt("line_total"))
				?: parseMoneyValue(item.opt("total_price"))
				?: parseMoneyValue(item.opt("price") ?: item.opt("baseprice"))
					?.let { unitPrice -> (unitPrice * quantity).roundToLong() }
				?: 0L
			val normalized = normalizer.normalize(name)
			add(
				ReceiptItem(
					originalName = name,
					canonicalName = normalized.canonicalName,
					category = normalized.category,
					subcategory = normalized.subcategory,
					quantity = quantity,
					totalCents = lineTotalCents,
					vatRate = null,
				)
			)
		}
	}

	private fun parseOrderDetailTotal(order: JSONObject): Long =
		parseMoneyValue(order.opt("total_price"))
			?: parseMoneyValue(order.opt("total"))
			?: parseLegacyTotalCents(order)

	private fun parseLegacyTotalCents(order: JSONObject): Long {
		val telemetryAmount = order.optJSONObject("telemetry")?.opt("end_amount")
		parseCentsNumber(telemetryAmount)?.let { return it }

		for (key in listOf("total", "total_price", "price")) {
			parseMoneyValue(order.opt(key))?.let { return it }
		}
		return 0L
	}

	private fun parseTimestamp(order: JSONObject): Long? {
		val topLevelKeys = listOf(
			"creation_time",
			"received_at",
			"timestamp",
			"created_at",
			"placed_time",
			"submitted_at",
			"delivery_time",
			"payment_time_ts",
			"payment_time",
			"time",
		)

		for (key in topLevelKeys) {
			parseTimestampValue(order.opt(key))?.let { return it }
		}

		val payments = order.optJSONArray("payments")
		if (payments != null) {
			for (index in 0 until payments.length()) {
				val payment = payments.optJSONObject(index) ?: continue
				for (key in listOf("payment_time", "payment_time_ts", "created_at", "timestamp")) {
					parseTimestampValue(payment.opt(key))?.let { return it }
				}
			}
		}

		return null
	}

	private fun parseTimestampValue(raw: Any?): Long? {
		if (raw == null || raw == JSONObject.NULL) return null

		if (raw is Number) {
			val value = raw.toLong()
			return if (value < 10_000_000_000L) value * 1000 else value
		}

		if (raw is JSONObject) {
			for (key in listOf("iso", "$" + "date", "epoch", "value")) {
				parseTimestampValue(raw.opt(key))?.let { return it }
			}
			return null
		}

		val text = raw.toString().trim()
		if (text.isEmpty()) return null

		text.toLongOrNull()?.let { value ->
			return if (value < 10_000_000_000L) value * 1000 else value
		}
		runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()?.let { return it }
		runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()?.let { return it }

		for (formatter in TIMESTAMP_FORMATTERS) {
			runCatching {
				LocalDateTime.parse(text, formatter)
					.atZone(zoneId)
					.toInstant()
					.toEpochMilli()
			}.getOrNull()?.let { return it }
		}
		return null
	}

	private fun parseMoneyValue(value: Any?): Long? = when (value) {
		null, JSONObject.NULL -> null
		is JSONObject -> parseMoneyValue(
			value.opt("amount")
				.takeUnless { it == null || it == JSONObject.NULL }
				?: value.opt("value"),
		)
		is Byte, is Short, is Int, is Long -> (value as Number).toLong()
		is Float, is Double -> ((value as Number).toDouble() * 100.0).roundToLong()
		is String -> {
			val clean = value
				.replace("€", "")
				.replace("$", "")
				.replace("\u00a0", "")
				.replace(" ", "")
				.replace(",", ".")
				.trim()
			clean.toDoubleOrNull()?.let { (it * 100.0).roundToLong() }
		}
		else -> null
	}

	private fun parseCentsNumber(value: Any?): Long? = when (value) {
		is Number -> value.toLong()
		else -> null
	}

	private fun firstString(obj: JSONObject, vararg keys: String): String? =
		keys.firstNotNullOfOrNull { key -> obj.optString(key).takeIf(String::isNotBlank) }

	private fun firstNumber(obj: JSONObject, vararg keys: String): Number? =
		keys.firstNotNullOfOrNull { key -> obj.opt(key) as? Number }

	private fun stableId(value: String): String {
		val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
		return digest.take(12).joinToString("") { byte -> "%02x".format(byte) }
	}

	private companion object {
		val TIMESTAMP_FORMATTERS = listOf(
			DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm"),
			DateTimeFormatter.ofPattern("d/M/yyyy, H:mm"),
			DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
			DateTimeFormatter.ofPattern("d/M/yyyy H:mm"),
			DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm"),
			DateTimeFormatter.ofPattern("d.M.yyyy, H:mm"),
			DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"),
			DateTimeFormatter.ofPattern("d.M.yyyy H:mm"),
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
			DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
		)
	}
}
