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
