package com.ziacik.blocky.data.wolt

import android.content.Context
import com.ziacik.blocky.data.BlockyDatabase
import com.ziacik.blocky.normalization.HeuristicItemNormalizer
import java.time.ZoneId

class WoltSyncService(context: Context) {
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
		try {
			database.save(receipt)
		} finally {
			database.close()
		}
		diagnostic("db: uložené receiptId=" + receipt.id)
		return receipt
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
			client.fetchOrderHistory(limit = 200),
			since,
		)

		var imported = 0
		val database = BlockyDatabase(appContext)
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

				database.save(receipt)
				imported++
			}
		} finally {
			database.close()
		}

		sessionStore.markSuccessfulSync(now)
		return imported
	}
}
