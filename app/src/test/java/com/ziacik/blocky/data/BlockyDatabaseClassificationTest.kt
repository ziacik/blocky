package com.ziacik.blocky.data

import com.ziacik.blocky.model.CategoryTotal
import com.ziacik.blocky.model.ClassificationSource
import com.ziacik.blocky.model.Receipt
import com.ziacik.blocky.model.ReceiptItem
import com.ziacik.blocky.model.SpendingType
import com.ziacik.blocky.model.SpendingTypeTotal
import com.ziacik.blocky.model.SubcategoryTotal
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
	fun upgradeBackfillsItemlessReceiptsIntoStatistics() {
		database.save(
			Receipt(
				id = "dental-1",
				merchant = "FAMILY DENTAL CARE",
				issuedAt = 1L,
				totalCents = 16_800L,
				items = emptyList(),
				rawJson = "{}",
			),
		)

		assertEquals(emptyList<ReceiptItem>(), database.receipt("dental-1")!!.items)

		database.onUpgrade(database.writableDatabase, 3, 4)

		val fallback = database.receipt("dental-1")!!.items.single()
		assertEquals("Nerozpísaná platba", fallback.originalName)
		assertEquals("Nezaradené", fallback.category)
		assertEquals(16_800L, fallback.totalCents)
		assertEquals(
			listOf(CategoryTotal("Nezaradené", 16_800L)),
			database.categoryTotals(),
		)
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

	@Test
	fun aggregatesSubcategoryTotals() {
		database.save(
			receipt(
				items = listOf(
					item("ROHLÍK BIELY", "Pečivo"),
					item("CHLIEB", "Pečivo"),
					item("MILKA OREO", "Sladkosti"),
				),
			),
		)

		assertEquals(
			listOf(
				SubcategoryTotal("Potraviny", "Pečivo", 398L),
				SubcategoryTotal("Potraviny", "Sladkosti", 199L),
			),
			database.subcategoryTotals(),
		)
	}

	@Test
	fun categoryAndSubcategoryTotalsAreNotTruncated() {
		val items = (1..13).map { index ->
			item("ITEM-$index", "Subcategory-$index").copy(
				category = "Category-$index",
				totalCents = index.toLong(),
			)
		}
		database.save(receipt(items = items))

		assertEquals(13, database.categoryTotals().size)
		assertEquals(13, database.subcategoryTotals().size)
	}

	@Test
	fun aggregatesSpendingTypeTotalsIncludingUnclassifiedItems() {
		database.save(
			Receipt(
				id = "spending-types",
				merchant = "Lidl",
				issuedAt = 1L,
				totalCents = 600L,
				items = listOf(
					item("CHLIEB", "Pečivo").copy(
						totalCents = 300L,
						spendingType = SpendingType.ESSENTIAL,
					),
					item("KAVA", "Trvanlivé potraviny").copy(
						totalCents = 200L,
						spendingType = SpendingType.REGULAR,
					),
					item("NEZARADENE", "Iné potraviny").copy(
						totalCents = 100L,
						spendingType = null,
					),
				),
				rawJson = "{}",
			),
		)

		assertEquals(
			listOf(
				SpendingTypeTotal(SpendingType.ESSENTIAL, 300L),
				SpendingTypeTotal(SpendingType.REGULAR, 200L),
				SpendingTypeTotal(null, 100L),
			),
			database.spendingTypeTotals(),
		)
	}

	@Test
	fun findsOnlyReceiptsThatStillNeedClassification() {
		database.save(
			Receipt(
				id = "pending",
				merchant = "Lidl",
				issuedAt = 1L,
				totalCents = 100L,
				items = listOf(
					ReceiptItem(
						originalName = "ROHLIK",
						canonicalName = "ROHLIK",
						category = "Nezaradené",
						subcategory = null,
						quantity = 1.0,
						totalCents = 100L,
						vatRate = null,
					),
				),
				rawJson = "{}",
			),
		)
		database.save(
			Receipt(
				id = "classified",
				merchant = "Lidl",
				issuedAt = 2L,
				totalCents = 100L,
				items = listOf(
					ReceiptItem(
						originalName = "ROHLIK",
						canonicalName = "Biely rožok",
						category = "Potraviny",
						subcategory = "Pečivo",
						quantity = 1.0,
						totalCents = 100L,
						vatRate = null,
						spendingType = SpendingType.ESSENTIAL,
						classificationSource = ClassificationSource.AI,
					),
				),
				rawJson = "{}",
			),
		)

		assertEquals(listOf("pending"), database.receiptIdsNeedingClassification())
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
