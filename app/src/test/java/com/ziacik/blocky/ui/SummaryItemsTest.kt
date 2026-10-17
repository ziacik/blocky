package com.ziacik.blocky.ui

import com.ziacik.blocky.model.ItemListEntry
import com.ziacik.blocky.model.SpendingType
import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryItemsTest {
	@Test
	fun categoryFilterReturnsOnlyItemsFromCategory() {
		val items = listOf(
			item("Potraviny", "Pečivo", SpendingType.ESSENTIAL),
			item("Domácnosť", "Čistenie", SpendingType.REGULAR),
		)

		assertEquals(
			listOf(items[0]),
			SummaryItems.filter(items, SummaryFilter.Category("Potraviny")),
		)
	}

	@Test
	fun subcategoryFilterAlsoChecksParentCategory() {
		val items = listOf(
			item("Potraviny", "Ostatné", SpendingType.ESSENTIAL),
			item("Domácnosť", "Ostatné", SpendingType.REGULAR),
		)

		assertEquals(
			listOf(items[0]),
			SummaryItems.filter(
				items,
				SummaryFilter.Subcategory("Potraviny", "Ostatné"),
			),
		)
	}

	@Test
	fun receiptOpenedFromSummaryRefreshesItemsUsingItsSummaryFilter() {
		val updatedItems = listOf(
			item("Domácnosť", "Čistenie", SpendingType.REGULAR),
		)
		val screen = MainScreen.ReceiptDetail(
			receiptId = "receipt-1",
			returnTo = MainScreen.SummaryItems(SummaryFilter.Category("Potraviny")),
		)

		assertEquals(
			emptyList<ItemListEntry>(),
			SummaryItems.forScreen(updatedItems, screen),
		)
	}

	@Test
	fun expenseTypeFilterCanShowUnclassifiedItems() {
		val items = listOf(
			item("Potraviny", "Pečivo", null),
			item("Potraviny", "Mäso", SpendingType.ESSENTIAL),
		)

		assertEquals(
			listOf(items[0]),
			SummaryItems.filter(items, SummaryFilter.ExpenseType(null)),
		)
	}


	@Test
	fun genericProductFilterMergesDifferentBrands() {
		val items = listOf(
			item("Potraviny", "Mliečne výrobky", SpendingType.REGULAR).copy(
				canonicalName = "Tami tatranské maslo",
				genericName = "Maslo",
			),
			item("Potraviny", "Mliečne výrobky", SpendingType.REGULAR).copy(
				canonicalName = "Rajo maslo",
				genericName = "Maslo",
			),
			item("Potraviny", "Mliečne výrobky", SpendingType.REGULAR).copy(
				canonicalName = "Eidam",
				genericName = "Eidam",
			),
		)
		assertEquals(items.take(2), SummaryItems.filter(items, SummaryFilter.Product("Maslo")))
	}

	private fun item(
		category: String,
		subcategory: String?,
		spendingType: SpendingType?,
	): ItemListEntry = ItemListEntry(
		receiptId = "$category-$subcategory-$spendingType",
		merchant = "Test obchod",
		issuedAt = 0L,
		originalName = "Test položka",
		canonicalName = "Test položka",
		category = category,
		subcategory = subcategory,
		quantity = 1.0,
		totalCents = 100L,
		spendingType = spendingType,
	)
}
