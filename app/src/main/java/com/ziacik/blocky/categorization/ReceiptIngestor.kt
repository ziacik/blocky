package com.ziacik.blocky.categorization

import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.model.ReceiptItem

interface ReceiptStore {
	fun save(receipt: Receipt)
}

class ReceiptIngestor(
	private val categorizer: ReceiptCategorizer,
	private val store: ReceiptStore,
) {
	fun ingest(receipt: Receipt): Receipt {
		val categorized = categorizer.categorize(withFallbackItem(receipt))
		store.save(categorized)
		return categorized
	}

	private fun withFallbackItem(receipt: Receipt): Receipt {
		if (receipt.items.isNotEmpty() || receipt.totalCents <= 0L) return receipt

		return receipt.copy(
			items = listOf(
				ReceiptItem(
					originalName = FALLBACK_ITEM_NAME,
					canonicalName = FALLBACK_ITEM_NAME,
					category = "Nezaradené",
					subcategory = null,
					quantity = 1.0,
					totalCents = receipt.totalCents,
					vatRate = null,
				),
			),
		)
	}

	private companion object {
		const val FALLBACK_ITEM_NAME = "Nerozpísaná platba"
	}
}
