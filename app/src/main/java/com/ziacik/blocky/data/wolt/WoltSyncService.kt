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
		val receipts = parser.parseOrders(client.fetchOrders(limit = 200), since)

		BlockyDatabase(appContext).use { database ->
			receipts.forEach(database::save)
		}
		sessionStore.markSuccessfulSync(now)
		return receipts.size
	}
}
