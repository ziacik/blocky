package com.ziacik.blocky.categorization

import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.model.ReceiptItem
import com.ziacik.blocky.model.SpendingType
import org.junit.Assert.assertEquals
import org.junit.Test

class AiReceiptCategorizerTest {
	@Test
	fun sendsMerchantAndWholeReceiptInSingleRequest() {
		val requests = mutableListOf<CategorizationRequest>()
		val client = object : CategorizationClient {
			override fun categorize(request: CategorizationRequest): List<CategorizedItem> {
				requests += request
				return listOf(
					CategorizedItem(
						index = 0,
						canonicalName = "Biely rožok",
						category = "Potraviny",
						subcategory = "Pečivo",
						spendingType = SpendingType.ESSENTIAL,
						confidence = 0.97,
					),
					CategorizedItem(
						index = 1,
						canonicalName = "Sprchový gél Dove",
						category = "Drogéria",
						subcategory = "Osobná hygiena",
						spendingType = SpendingType.REGULAR,
						confidence = 0.93,
					),
				)
			}
		}

		val receipt = Receipt(
			id = "receipt-1",
			merchant = "dm drogerie markt",
			issuedAt = 1L,
			totalCents = 499L,
			items = listOf(
				item("ROHLÍK BIELY 50G", 49L),
				item("DOVE SHOWER GEL", 450L),
			),
			rawJson = "{}",
		)

		val categorized = AiReceiptCategorizer(client).categorize(receipt)

		assertEquals(1, requests.size)
		assertEquals("dm drogerie markt", requests.single().merchant)
		assertEquals(
			listOf("ROHLÍK BIELY 50G", "DOVE SHOWER GEL"),
			requests.single().items.map { it.name },
		)
		assertEquals("Biely rožok", categorized.items[0].canonicalName)
		assertEquals("Potraviny", categorized.items[0].category)
		assertEquals("Pečivo", categorized.items[0].subcategory)
		assertEquals(SpendingType.ESSENTIAL, categorized.items[0].spendingType)
		assertEquals("Drogéria", categorized.items[1].category)
		assertEquals("Osobná hygiena", categorized.items[1].subcategory)
		assertEquals(SpendingType.REGULAR, categorized.items[1].spendingType)
	}

	private fun item(name: String, totalCents: Long) = ReceiptItem(
		originalName = name,
		canonicalName = name,
		category = "Nezaradené",
		subcategory = null,
		quantity = 1.0,
		totalCents = totalCents,
		vatRate = null,
	)
}
