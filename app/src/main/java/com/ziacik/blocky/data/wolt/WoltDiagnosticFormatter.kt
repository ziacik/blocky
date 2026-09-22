package com.ziacik.blocky.data.wolt

import org.json.JSONArray
import org.json.JSONObject

object WoltDiagnosticFormatter {
	fun detailSummary(json: String, maxItems: Int = 5): List<String> {
		val root = runCatching { JSONObject(json) }.getOrNull()
			?: return listOf("detail: invalid JSON")
		val items = root.optJSONArray("items") ?: JSONArray()
		val lines = mutableListOf<String>()
		lines += "detail: items=" + items.length() +
			", total_price=" + raw(root, "total_price") +
			", venue_name=" + raw(root, "venue_name")

		for (index in 0 until minOf(items.length(), maxItems)) {
			val item = items.optJSONObject(index) ?: continue
			lines += "item[" + index + "]: " +
				"name=" + raw(item, "name") +
				", count=" + raw(item, "count") +
				", price=" + raw(item, "price") +
				", end_amount=" + raw(item, "end_amount") +
				", line_total=" + raw(item, "line_total")
		}
		return lines
	}

	private fun raw(obj: JSONObject, key: String): String {
		val value = obj.opt(key)
		return when {
			value == null || value == JSONObject.NULL -> "<missing>"
			else -> value.toString().replace("\n", " ").take(120)
		}
	}
}
