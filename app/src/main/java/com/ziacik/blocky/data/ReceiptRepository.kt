package com.ziacik.blocky.data

import com.ziacik.blocky.categorization.ExpenseTaxonomy
import com.ziacik.blocky.categorization.ReceiptIngestor
import com.ziacik.blocky.model.CategoryTotal
import com.ziacik.blocky.model.ItemListEntry
import com.ziacik.blocky.model.ProductTotal
import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.model.ReceiptSummary
import com.ziacik.blocky.model.SpendingType

interface ReceiptLookupClient {
	fun findReceipt(qrValue: String): String
}

interface ReceiptParser {
	fun parse(json: String): Receipt
}

class ReceiptRepository(
	private val client: ReceiptLookupClient,
	private val parser: ReceiptParser,
	private val database: BlockyDatabase,
	private val ingestor: ReceiptIngestor,
) {
	fun import(qrValue: String): Receipt {
		val json = client.findReceipt(qrValue)
		return ingestor.ingest(parser.parse(json))
	}

	fun receipt(receiptId: String): Receipt? = database.receipt(receiptId)

	fun allItems(): List<ItemListEntry> = database.allItems()

	fun correctItemClassification(
		receiptId: String,
		itemIndex: Int,
		category: String,
		subcategory: String?,
		spendingType: SpendingType,
	): Receipt {
		ExpenseTaxonomy.requireValid(category, subcategory)
		check(
			database.updateItemClassification(
				receiptId = receiptId,
				itemIndex = itemIndex,
				category = category,
				subcategory = subcategory,
				spendingType = spendingType,
			)
		) { "Položka sa nenašla." }

		return checkNotNull(database.receipt(receiptId)) { "Bloček sa nenašiel." }
	}

	fun snapshot(): RepositorySnapshot = RepositorySnapshot(
		totalCents = database.totalCents(),
		receipts = database.receiptSummaries(),
		categories = database.categoryTotals(),
		products = database.productTotals(),
	)
}

data class RepositorySnapshot(
	val totalCents: Long,
	val receipts: List<ReceiptSummary>,
	val categories: List<CategoryTotal>,
	val products: List<ProductTotal>,
)
