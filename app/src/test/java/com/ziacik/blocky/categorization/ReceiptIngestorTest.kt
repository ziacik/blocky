package com.ziacik.blocky.categorization

import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.model.ReceiptItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptIngestorTest {
	@Test
	fun itemlessReceiptGetsFallbackItemBeforeCategorization() {
		val saved = mutableListOf<Receipt>()
		var categorizerInput: Receipt? = null
		val categorizer = object : ReceiptCategorizer {
			override fun categorize(receipt: Receipt): Receipt {
				categorizerInput = receipt
				return receipt
			}
		}
		val store = object : ReceiptStore {
			override fun save(receipt: Receipt) {
				saved += receipt
			}
		}
		val original = Receipt(
			id = "dental-1",
			merchant = "FAMILY DENTAL CARE",
			issuedAt = 1L,
			totalCents = 16_800L,
			items = emptyList(),
			rawJson = "{}",
		)

		val result = ReceiptIngestor(categorizer, store).ingest(original)

		val fallback = categorizerInput!!.items.single()
		assertEquals("Nerozpísaná platba", fallback.originalName)
		assertEquals("Nerozpísaná platba", fallback.canonicalName)
		assertEquals("Nezaradené", fallback.category)
		assertEquals(16_800L, fallback.totalCents)
		assertEquals(result, saved.single())
	}

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
