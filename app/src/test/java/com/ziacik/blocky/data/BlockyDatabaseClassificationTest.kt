package com.ziacik.blocky.data

import com.ziacik.blocky.model.ClassificationSource
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
		database.save(receipt())

		val loaded = database.receipt("receipt-1")!!.items.single()

		assertEquals(SpendingType.DISCRETIONARY, loaded.spendingType)
		assertEquals(0.91, loaded.classificationConfidence!!, 0.0001)
	}

	@Test
	fun manualCorrectionUpdatesOnlySelectedReceiptItem() {
		database.save(
			receipt(
				items = listOf(
					item("MILKA OREO", "Sladkosti"),
					item("ROHLÍK BIELY", "Pečivo"),
				),
			),
		)

		database.updateItemClassification(
			receiptId = "receipt-1",
			itemIndex = 1,
			category = "Potraviny",
			subcategory = "Pečivo",
			spendingType = SpendingType.ESSENTIAL,
		)

		val items = database.receipt("receipt-1")!!.items
		assertEquals("Sladkosti", items[0].subcategory)
		assertEquals(ClassificationSource.USER, items[1].classificationSource)
		assertEquals(SpendingType.ESSENTIAL, items[1].spendingType)
	}

	@Test
	fun manualCorrectionSurvivesSavingSameReceiptAgain() {
		val original = receipt()
		database.save(original)
		database.updateItemClassification(
			receiptId = "receipt-1",
			itemIndex = 0,
			category = "Potraviny",
			subcategory = "Pečivo",
			spendingType = SpendingType.ESSENTIAL,
		)

		database.save(original)

		val loaded = database.receipt("receipt-1")!!.items.single()
		assertEquals("Pečivo", loaded.subcategory)
		assertEquals(SpendingType.ESSENTIAL, loaded.spendingType)
		assertEquals(ClassificationSource.USER, loaded.classificationSource)
	}

	private fun receipt(
		items: List<ReceiptItem> = listOf(item("MILKA OREO", "Sladkosti")),
	) = Receipt(
		id = "receipt-1",
		merchant = "Lidl",
		issuedAt = 1L,
		totalCents = items.sumOf { it.totalCents },
		items = items,
		rawJson = "{}",
	)

	private fun item(name: String, subcategory: String) = ReceiptItem(
		originalName = name,
		canonicalName = name,
		category = "Potraviny",
		subcategory = subcategory,
		quantity = 1.0,
		totalCents = 199L,
		vatRate = 20.0,
		spendingType = SpendingType.DISCRETIONARY,
		classificationConfidence = 0.91,
	)
}
