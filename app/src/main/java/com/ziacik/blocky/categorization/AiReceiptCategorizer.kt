package com.ziacik.blocky.categorization

import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.model.SpendingType

data class CategorizationRequest(
	val merchant: String,
	val items: List<CategorizationRequestItem>,
)

data class CategorizationRequestItem(
	val index: Int,
	val name: String,
	val totalCents: Long,
	val quantity: Double,
)

data class CategorizedItem(
	val index: Int,
	val canonicalName: String,
	val category: String,
	val subcategory: String?,
	val spendingType: SpendingType,
	val confidence: Double,
)

interface CategorizationClient {
	fun categorize(request: CategorizationRequest): List<CategorizedItem>
}

interface ReceiptCategorizer {
	fun categorize(receipt: Receipt): Receipt
}

class AiReceiptCategorizer(
	private val client: CategorizationClient,
) : ReceiptCategorizer {
	override fun categorize(receipt: Receipt): Receipt {
		if (receipt.items.isEmpty()) return receipt

		val request = CategorizationRequest(
			merchant = receipt.merchant,
			items = receipt.items.mapIndexed { index, item ->
				CategorizationRequestItem(
					index = index,
					name = item.originalName,
					totalCents = item.totalCents,
					quantity = item.quantity,
				)
			},
		)
		val byIndex = client.categorize(request).associateBy(CategorizedItem::index)

		return receipt.copy(
			items = receipt.items.mapIndexed { index, item ->
				val categorized = byIndex[index] ?: return@mapIndexed item
				item.copy(
					canonicalName = categorized.canonicalName,
					category = categorized.category,
					subcategory = categorized.subcategory,
					spendingType = categorized.spendingType,
					classificationConfidence = categorized.confidence,
				)
			},
		)
	}
}
