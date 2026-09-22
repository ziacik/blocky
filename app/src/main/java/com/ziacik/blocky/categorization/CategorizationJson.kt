package com.ziacik.blocky.categorization

import com.ziacik.blocky.model.SpendingType
import org.json.JSONArray
import org.json.JSONObject

object CategorizationJson {
	fun encodeRequest(request: CategorizationRequest): String = JSONObject()
		.put("merchant", request.merchant)
		.put(
			"items",
			JSONArray().apply {
				request.items.forEach { item ->
					put(
						JSONObject()
							.put("index", item.index)
							.put("name", item.name)
							.put("totalCents", item.totalCents)
							.put("quantity", item.quantity)
					)
				}
			},
		)
		.toString()

	fun decodeResponse(json: String): List<CategorizedItem> {
		val root = JSONObject(json)
		val items = root.getJSONArray("items")
		return buildList {
			for (index in 0 until items.length()) {
				val item = items.getJSONObject(index)
				val category = item.getString("category")
				val subcategory = if (item.isNull("subcategory")) {
					null
				} else {
					item.getString("subcategory")
				}
				ExpenseTaxonomy.requireValid(category, subcategory)
				val confidence = item.getDouble("confidence")
				require(confidence in 0.0..1.0) { "Confidence must be between 0 and 1" }

				add(
					CategorizedItem(
						index = item.getInt("index"),
						canonicalName = item.getString("canonicalName").also {
							require(it.isNotBlank()) { "canonicalName must not be blank" }
						},
						category = category,
						subcategory = subcategory,
						spendingType = SpendingType.valueOf(item.getString("spendingType")),
						confidence = confidence,
					)
				)
			}
		}
	}
}
