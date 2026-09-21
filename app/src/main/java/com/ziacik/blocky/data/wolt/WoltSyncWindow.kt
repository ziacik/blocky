package com.ziacik.blocky.data.wolt

import java.time.Instant
import java.time.ZoneId

object WoltSyncWindow {
	private const val OVERLAP_MILLIS = 24L * 60 * 60 * 1000

	fun since(
		lastSuccessfulSyncMillis: Long?,
		nowMillis: Long,
		zoneId: ZoneId = ZoneId.systemDefault(),
	): Long {
		if (lastSuccessfulSyncMillis != null) {
			return lastSuccessfulSyncMillis - OVERLAP_MILLIS
		}

		return Instant.ofEpochMilli(nowMillis)
			.atZone(zoneId)
			.toLocalDate()
			.withDayOfMonth(1)
			.atStartOfDay(zoneId)
			.toInstant()
			.toEpochMilli()
	}
}
