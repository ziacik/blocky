package com.ziacik.blocky.normalization

interface ItemNormalizer {
	fun normalize(rawName: String): NormalizedItem
}

data class NormalizedItem(
	val canonicalName: String,
	val category: String,
	val subcategory: String? = null,
)

class HeuristicItemNormalizer : ItemNormalizer {
	override fun normalize(rawName: String): NormalizedItem = NormalizedItem(
		canonicalName = rawName,
		category = "Nezaradené",
	)
}
