package com.ziacik.blocky.data

import com.ziacik.blocky.model.CategoryTotal
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

	fun snapshot(): RepositorySnapshot = RepositorySnapshot(
		totalCents = database.totalCents(),
		receipts = database.receiptSummaries(),
		categories = database.categoryTotals(),
	)
}

data class RepositorySnapshot(
	val totalCents: Long,
	val receipts: List<ReceiptSummary>,
	val categories: List<CategoryTotal>,
)
