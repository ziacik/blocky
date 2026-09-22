package com.ziacik.blocky.categorization

import com.ziacik.blocky.model.Receipt

interface ReceiptStore {
	fun save(receipt: Receipt)
}

class ReceiptIngestor(
	private val categorizer: ReceiptCategorizer,
	private val store: ReceiptStore,
) {
	fun ingest(receipt: Receipt): Receipt {
		val categorized = categorizer.categorize(receipt)
		store.save(categorized)
		return categorized
	}
}
