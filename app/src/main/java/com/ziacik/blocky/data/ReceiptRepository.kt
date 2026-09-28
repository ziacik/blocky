package com.ziacik.blocky.data

import com.ziacik.blocky.categorization.ExpenseTaxonomy
import com.ziacik.blocky.categorization.ReceiptIngestor
import com.ziacik.blocky.model.CategoryTotal
import com.ziacik.blocky.model.ItemListEntry
import com.ziacik.blocky.model.ProductTotal
import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.model.ReceiptSummary
import com.ziacik.blocky.model.SpendingType
import com.ziacik.blocky.model.SpendingTypeTotal
import com.ziacik.blocky.model.SubcategoryTotal
import java.time.ZoneId

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
	private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
	fun import(qrValue: String): Receipt {
		val json = client.findReceipt(qrValue)
		return ingestor.ingest(parser.parse(json))
	}

	fun receipt(receiptId: String): Receipt? = database.receipt(receiptId)

	fun allItems(month: ExpenseMonth): List<ItemListEntry> {
		val range = month.range(zoneId)
		return database.allItemsBetween(range.startInclusive, range.endExclusive)
	}

	fun availableMonths(currentMonth: ExpenseMonth = ExpenseMonth.current()): List<ExpenseMonth> =
		(database.receiptIssuedAt().map { ExpenseMonth.fromMillis(it, zoneId) } + currentMonth)
			.distinct()
			.sortedWith(compareByDescending<ExpenseMonth> { it.year }.thenByDescending { it.month })

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

	fun categorizePending(limit: Int = 50): Int {
		var categorized = 0
		database.receiptIdsNeedingClassification(limit).forEach { receiptId ->
			val receipt = database.receipt(receiptId) ?: return@forEach
			ingestor.ingest(receipt)
			categorized++
		}
		return categorized
	}

	fun snapshot(month: ExpenseMonth): RepositorySnapshot {
		val range = month.range(zoneId)
		return RepositorySnapshot(
			totalCents = database.totalCentsBetween(range.startInclusive, range.endExclusive),
			receipts = database.receiptSummariesBetween(range.startInclusive, range.endExclusive),
			categories = database.categoryTotalsBetween(range.startInclusive, range.endExclusive),
			subcategories = database.subcategoryTotalsBetween(range.startInclusive, range.endExclusive),
			spendingTypes = database.spendingTypeTotalsBetween(range.startInclusive, range.endExclusive),
			products = database.productTotalsBetween(range.startInclusive, range.endExclusive),
			items = database.allItemsBetween(range.startInclusive, range.endExclusive),
			availableMonths = availableMonths(),
		)
	}
}

data class RepositorySnapshot(
	val totalCents: Long,
	val receipts: List<ReceiptSummary>,
	val categories: List<CategoryTotal>,
	val subcategories: List<SubcategoryTotal>,
	val spendingTypes: List<SpendingTypeTotal>,
	val products: List<ProductTotal>,
	val items: List<ItemListEntry>,
	val availableMonths: List<ExpenseMonth>,
)
