package com.ziacik.blocky.data

import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.model.ReceiptItem
import com.ziacik.blocky.model.SpendingType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BlockyDatabaseClassificationTest {
	private lateinit var database: BlockyDatabase

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
	fun persistsSpendingTypeAndClassificationConfidence() {
		database.save(
			Receipt(
				id = "receipt-1",
				merchant = "Lidl",
				issuedAt = 1L,
				totalCents = 199L,
				items = listOf(
					ReceiptItem(
						originalName = "MILKA OREO",
						canonicalName = "Milka Oreo",
						category = "Potraviny",
						subcategory = "Sladkosti",
						quantity = 1.0,
						totalCents = 199L,
						vatRate = 20.0,
						spendingType = SpendingType.DISCRETIONARY,
						classificationConfidence = 0.91,
					)
				),
				rawJson = "{}",
			)
		)

		val loaded = database.receipt("receipt-1")!!.items.single()

		assertEquals(SpendingType.DISCRETIONARY, loaded.spendingType)
		assertEquals(0.91, loaded.classificationConfidence!!, 0.0001)
	}
}
