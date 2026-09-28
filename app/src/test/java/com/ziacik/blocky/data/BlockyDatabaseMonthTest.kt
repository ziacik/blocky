package com.ziacik.blocky.data

import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.model.ReceiptItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BlockyDatabaseMonthTest {
	private lateinit var database: BlockyDatabase
	private val zoneId = ZoneId.of("Europe/Bratislava")

	@Before
	fun setUp() {
		val context = RuntimeEnvironment.getApplication()
		context.deleteDatabase("blocky.db")
		database = BlockyDatabase(context)
	}

	@After
	fun tearDown() {
		database.close()
	}

	@Test
	fun expenseMonthCreatesLocalCalendarMonthRange() {
		val month = ExpenseMonth(2026, 9)
		val range = month.range(zoneId)

		assertEquals(at(2026, 9, 1), range.startInclusive)
		assertEquals(at(2026, 10, 1), range.endExclusive)
	}

	@Test
	fun monthlyQueriesExcludeReceiptsFromOtherMonths() {
		database.save(receipt("sep", at(2026, 9, 30), 300L))
		database.save(receipt("oct", at(2026, 10, 1), 700L))

		val range = ExpenseMonth(2026, 9).range(zoneId)

		assertEquals(300L, database.totalCentsBetween(range.startInclusive, range.endExclusive))
		assertEquals(listOf("sep"), database.receiptSummariesBetween(range.startInclusive, range.endExclusive).map { it.id })
		assertEquals(listOf("sep"), database.allItemsBetween(range.startInclusive, range.endExclusive).map { it.receiptId })
		assertEquals(300L, database.categoryTotalsBetween(range.startInclusive, range.endExclusive).single().totalCents)
	}

	private fun at(year: Int, month: Int, day: Int): Long =
		ZonedDateTime.of(year, month, day, 12, 0, 0, 0, zoneId)
			.toInstant()
			.toEpochMilli()

	private fun receipt(id: String, issuedAt: Long, totalCents: Long) = Receipt(
		id = id,
		merchant = "Test",
		issuedAt = issuedAt,
		totalCents = totalCents,
		items = listOf(
			ReceiptItem(
				originalName = id,
				canonicalName = id,
				category = "Potraviny",
				subcategory = "Iné potraviny",
				quantity = 1.0,
				totalCents = totalCents,
				vatRate = null,
			),
		),
		rawJson = "{}",
	)
}
