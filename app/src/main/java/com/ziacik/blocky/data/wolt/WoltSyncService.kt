package com.ziacik.blocky.data.wolt

import android.content.Context
import com.ziacik.blocky.BuildConfig
import com.ziacik.blocky.categorization.CategorizationPipeline
import com.ziacik.blocky.categorization.ReceiptCategorizer
import com.ziacik.blocky.categorization.ReceiptIngestor
import com.ziacik.blocky.data.BlockyDatabase
import com.ziacik.blocky.normalization.HeuristicItemNormalizer
import java.time.ZoneId

class WoltSyncService(
	context: Context,
	private val categorizer: ReceiptCategorizer = CategorizationPipeline.create(
		endpoint = BuildConfig.CATEGORIZATION_ENDPOINT,
		apiKey = BuildConfig.OPENAI_API_KEY,
	),
) {
	private val appContext = context.applicationContext
	private val sessionStore = WoltSessionStore(appContext)
	private val client = WoltClient(sessionStore)
	private val parser = WoltOrderParser(
		normalizer = HeuristicItemNormalizer(),
		zoneId = ZoneId.of("Europe/Bratislava"),
	)

	fun importLatestOrder(
		diagnostic: (String) -> Unit = {},
	): com.ziacik.blocky.model.Receipt? {
		if (!sessionStore.isConnected()) {
			diagnostic("session: Wolt nie je pripojený")
			return null
		}

		diagnostic("session: pripojený")
		diagnostic("history: načítavam posledné objednávky")
		val historyJson = client.fetchOrderHistory(
			limit = 10,
			diagnostic = diagnostic,
		)
		val historyOrder = parser.latestHistoryOrder(historyJson)
		if (historyOrder == null) {
			diagnostic("history: nenašla sa žiadna objednávka")
			return null
		}

		diagnostic(
			"history: selected purchaseId=" + historyOrder.purchaseId +
				", issuedAt=" + (historyOrder.issuedAt?.toString() ?: "<missing>") +
				", merchant=" + (historyOrder.merchant ?: "<missing>"),
		)
		WoltDiagnosticFormatter.historySummary(
			historyJson,
			historyOrder.purchaseId,
		).forEach(diagnostic)
		diagnostic("detail: načítavam jednu objednávku")
		val detailJson = client.fetchOrderDetail(
			purchaseId = historyOrder.purchaseId,
			diagnostic = diagnostic,
		)
		WoltDiagnosticFormatter.detailSummary(detailJson).forEach(diagnostic)

		val receipt = parser.parseOrderDetail(
			json = detailJson,
			fallbackPurchaseId = historyOrder.purchaseId,
			fallbackIssuedAt = historyOrder.issuedAt,
			fallbackMerchant = historyOrder.merchant,
		)
		if (receipt == null) {
			diagnostic("parse: detail sa nepodarilo premeniť na bloček")
			return null
		}

		val pricedItems = receipt.items.count { it.totalCents > 0L }
		val zeroItems = receipt.items.size - pricedItems
		diagnostic(
			"parse: merchant=" + receipt.merchant +
				", total=" + receipt.totalCents +
				", items=" + receipt.items.size +
				", priced=" + pricedItems +
				", zero=" + zeroItems,
		)

		val database = BlockyDatabase(appContext)
		return try {
			val categorized = ReceiptIngestor(categorizer, database).ingest(receipt)
			diagnostic("db: uložené receiptId=" + categorized.id)
			categorized
		} finally {
			database.close()
		}
	}

	fun importCurrentMonth(
		diagnostic: (String) -> Unit = {},
	): Int {
		if (!sessionStore.isConnected()) {
			diagnostic("session: Wolt nie je pripojený")
			return 0
		}

		val now = System.currentTimeMillis()
		val since = WoltSyncWindow.since(
			lastSuccessfulSyncMillis = null,
			nowMillis = now,
			zoneId = ZoneId.of("Europe/Bratislava"),
		)

		diagnostic("START: sťahujem všetky objednávky aktuálneho mesiaca")
		diagnostic("history: načítavam objednávky")
		val historyJson = client.fetchOrderHistory(
			limit = WOLT_ORDER_HISTORY_MAX_LIMIT,
			diagnostic = diagnostic,
		)
		val historyOrders = parser.historyOrdersSince(historyJson, since)
		diagnostic("history: kandidátov za mesiac=" + historyOrders.size)

		var imported = 0
		var skipped = 0
		val database = BlockyDatabase(appContext)
		val ingestor = ReceiptIngestor(categorizer, database)
		try {
			for ((index, historyOrder) in historyOrders.withIndex()) {
				val receiptId = "wolt:" + historyOrder.purchaseId
				diagnostic(
					"order " + (index + 1) + "/" + historyOrders.size +
						": " + historyOrder.purchaseId +
						" " + (historyOrder.merchant ?: ""),
				)

				if (!WoltSyncPlanner.shouldFetchDetail(database.hasPricedReceipt(receiptId))) {
					skipped++
					diagnostic("skip: už uložené s cenami")
					continue
				}

				val detailJson = client.fetchOrderDetail(
					purchaseId = historyOrder.purchaseId,
					diagnostic = diagnostic,
				)
				val receipt = parser.parseOrderDetail(
					json = detailJson,
					fallbackPurchaseId = historyOrder.purchaseId,
					fallbackIssuedAt = historyOrder.issuedAt,
					fallbackMerchant = historyOrder.merchant,
				)
				if (receipt == null) {
					skipped++
					diagnostic("skip: detail sa nepodarilo parsovať")
					continue
				}
				if (receipt.issuedAt < since) {
					skipped++
					diagnostic("skip: objednávka je mimo aktuálneho mesiaca")
					continue
				}

				val categorized = ingestor.ingest(receipt)
				imported++
				diagnostic(
					"saved: " + categorized.merchant +
						", items=" + categorized.items.size +
						", total=" + categorized.totalCents,
				)
			}
		} finally {
			database.close()
		}

		diagnostic("DONE: importované=" + imported + ", preskočené=" + skipped)
		return imported
	}

	fun sync(): Int {
		if (!sessionStore.isConnected()) return 0

		val now = System.currentTimeMillis()
		val since = WoltSyncWindow.since(
			lastSuccessfulSyncMillis = sessionStore.lastSuccessfulSyncMillis(),
			nowMillis = now,
			zoneId = ZoneId.of("Europe/Bratislava"),
		)

		val purchaseIds = parser.parseHistoryPurchaseIds(
			client.fetchOrderHistory(limit = WOLT_ORDER_HISTORY_MAX_LIMIT),
			since,
		)

		var imported = 0
		val database = BlockyDatabase(appContext)
		val ingestor = ReceiptIngestor(categorizer, database)
		try {
			for (purchaseId in purchaseIds) {
				val receiptId = "wolt:" + purchaseId
				if (!WoltSyncPlanner.shouldFetchDetail(database.hasPricedReceipt(receiptId))) {
					continue
				}

				val receipt = parser.parseOrderDetail(
					client.fetchOrderDetail(purchaseId),
				) ?: continue

				if (receipt.issuedAt < since) {
					continue
				}

				ingestor.ingest(receipt)
				imported++
			}
		} finally {
			database.close()
		}

		sessionStore.markSuccessfulSync(now)
		return imported
	}
}
