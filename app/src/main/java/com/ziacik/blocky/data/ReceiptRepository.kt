package com.ziacik.blocky.data

import com.ziacik.blocky.model.CategoryTotal
import com.ziacik.blocky.model.ItemListEntry
import com.ziacik.blocky.model.ProductTotal
import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.model.ReceiptSummary

class ReceiptRepository(
	private val client: EkasaClient,
	private val parser: EkasaReceiptParser,
	private val database: BlockyDatabase,
) {
	fun import(qrValue: String) {
		val json = client.findReceipt(qrValue)
		val receipt = parser.parse(json)
		database.save(receipt)
	}

	fun receipt(receiptId: String): Receipt? = database.receipt(receiptId)

	fun allItems(): List<ItemListEntry> = database.allItems()

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
