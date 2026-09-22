package com.ziacik.blocky.data

import com.ziacik.blocky.categorization.ReceiptCategorizer
import com.ziacik.blocky.categorization.ReceiptIngestor
import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.model.ReceiptItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReceiptRepositoryCategorizationTest {
	private lateinit var database: BlockyDatabase

	@Before
	fun setUp() {
		val context = RuntimeEnvironment.getApplication()
		context.deleteDatabase("blocky.db")
		database = BlockyDatabase(context)
	}

	@After
	fun tearDown() {
		database.close()
	}

	@Test
	fun importCategorizesBeforeSaving() {
		val client = object : ReceiptLookupClient {
			override fun findReceipt(qrValue: String) = "raw-json"
		}
		val parser = object : ReceiptParser {
			override fun parse(json: String) = receipt("Nezaradené")
		}
		val categorizer = object : ReceiptCategorizer {
			override fun categorize(receipt: Receipt) = receipt.copy(
				items = receipt.items.map { it.copy(category = "Potraviny", subcategory = "Pečivo") },
			)
		}
		val repository = ReceiptRepository(
			client = client,
			parser = parser,
			database = database,
			ingestor = ReceiptIngestor(categorizer, database),
		)

		repository.import("qr")

		assertEquals("Potraviny", database.receipt("receipt-1")!!.items.single().category)
	}

	private fun receipt(category: String) = Receipt(
		id = "receipt-1",
		merchant = "Lidl",
		issuedAt = 1L,
		totalCents = 49L,
		items = listOf(
			ReceiptItem(
				originalName = "ROHLÍK",
				canonicalName = "ROHLÍK",
				category = category,
				subcategory = null,
				quantity = 1.0,
				totalCents = 49L,
				vatRate = null,
			),
		),
		rawJson = "{}",
	)
}
