package com.ziacik.blocky.categorization

import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.model.ReceiptItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptIngestorTest {
	@Test
	fun categorizesBeforeSaving() {
		val original = receipt("Nezaradené")
		val categorized = receipt("Potraviny")
		val saved = mutableListOf<Receipt>()
		val categorizer = object : ReceiptCategorizer {
			override fun categorize(receipt: Receipt): Receipt = categorized
		}
		val store = object : ReceiptStore {
			override fun save(receipt: Receipt) {
				saved += receipt
			}
		}

		val result = ReceiptIngestor(categorizer, store).ingest(original)

		assertEquals("Potraviny", result.items.single().category)
		assertEquals(listOf(categorized), saved)
	}

	private fun receipt(category: String) = Receipt(
		id = "receipt-1",
		merchant = "Lidl",
		issuedAt = 1L,
		totalCents = 100L,
		items = listOf(
			ReceiptItem(
				originalName = "ROHLIK",
				canonicalName = "ROHLIK",
				category = category,
				subcategory = null,
				quantity = 1.0,
				totalCents = 100L,
				vatRate = null,
			)
		),
		rawJson = "{}",
	)
}
