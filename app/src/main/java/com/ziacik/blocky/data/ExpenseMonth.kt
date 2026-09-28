package com.ziacik.blocky.data

import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class ExpenseMonth(
	val year: Int,
	val month: Int,
) {
	init {
		require(month in 1..12) { "month must be between 1 and 12" }
	}

	fun range(zoneId: ZoneId): MonthRange {
		val start = YearMonth.of(year, month)
			.atDay(1)
			.atStartOfDay(zoneId)
			.toInstant()
			.toEpochMilli()
		val end = YearMonth.of(year, month)
			.plusMonths(1)
			.atDay(1)
			.atStartOfDay(zoneId)
			.toInstant()
			.toEpochMilli()
		return MonthRange(start, end)
	}

	companion object {
		fun current(clock: Clock = Clock.systemDefaultZone()): ExpenseMonth {
			val value = YearMonth.now(clock)
			return ExpenseMonth(value.year, value.monthValue)
		}

		fun fromMillis(value: Long, zoneId: ZoneId): ExpenseMonth {
			val date = Instant.ofEpochMilli(value).atZone(zoneId)
			return ExpenseMonth(date.year, date.monthValue)
		}
	}
}

data class MonthRange(
	val startInclusive: Long,
	val endExclusive: Long,
)
