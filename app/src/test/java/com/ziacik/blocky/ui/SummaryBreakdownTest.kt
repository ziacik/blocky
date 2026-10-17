package com.ziacik.blocky.ui

import com.ziacik.blocky.model.CategoryTotal
import com.ziacik.blocky.model.ProductTotal
import com.ziacik.blocky.model.SpendingType
import com.ziacik.blocky.model.SpendingTypeTotal
import com.ziacik.blocky.model.SubcategoryTotal
import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryBreakdownTest {

	@Test
	fun productTotalsUseGenericNameAsAFilter() {
		val totals = SummaryBreakdown.totals(
			categories = emptyList(),
			subcategories = emptyList(),
			spendingTypes = emptyList(),
			products = listOf(ProductTotal("Maslo", 600), ProductTotal("Eidam", 300)),
			dimension = BreakdownDimension.Product,
		)
		assertEquals(listOf("Maslo", "Eidam"), totals.map { it.label })
		assertEquals(SummaryFilter.Product("Maslo"), totals.first().filter)
	}

	@Test
	fun categoryTotalsContainAllPositiveEntriesSortedByAmount() {
		val totals = SummaryBreakdown.totals(
			categories = listOf(
				CategoryTotal("C", 300),
				CategoryTotal("A", 500),
				CategoryTotal("D", 200),
				CategoryTotal("B", 400),
				CategoryTotal("F", 50),
				CategoryTotal("E", 100),
				CategoryTotal("Zero", 0),
			),
			subcategories = emptyList(),
			spendingTypes = emptyList(),
			dimension = BreakdownDimension.Category,
		)

		assertEquals(6, totals.size)
		assertEquals(
			listOf("A", "B", "C", "D", "E", "F"),
			totals.map { it.label },
		)
	}

	@Test
	fun subcategoryTotalKeepsParentCategoryInFilter() {
		val totals = SummaryBreakdown.totals(
			categories = emptyList(),
			subcategories = listOf(
				SubcategoryTotal("Potraviny", "Ostatné", 250),
			),
			spendingTypes = emptyList(),
			dimension = BreakdownDimension.Subcategory,
		)

		assertEquals(
			SummaryFilter.Subcategory("Potraviny", "Ostatné"),
			totals.single().filter,
		)
	}

	@Test
	fun spendingTypesAreSortedAndCanIncludeUnclassified() {
		val totals = SummaryBreakdown.totals(
			categories = emptyList(),
			subcategories = emptyList(),
			spendingTypes = listOf(
				SpendingTypeTotal(SpendingType.REGULAR, 100),
				SpendingTypeTotal(null, 300),
				SpendingTypeTotal(SpendingType.ESSENTIAL, 200),
			),
			dimension = BreakdownDimension.SpendingType,
		)

		assertEquals(
			listOf("Nezaradené", "Nevyhnutné", "Bežné"),
			totals.map { it.label },
		)
	}
}
