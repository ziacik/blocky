package com.ziacik.blocky.ui

import com.ziacik.blocky.model.CategoryTotal
import com.ziacik.blocky.model.SpendingType
import com.ziacik.blocky.model.SpendingTypeTotal
import com.ziacik.blocky.model.SubcategoryTotal

enum class BreakdownDimension {
	Category,
	Subcategory,
	SpendingType,
}

data class BreakdownTotal(
	val label: String,
	val totalCents: Long,
	val filter: SummaryFilter,
)

object SummaryBreakdown {
	fun totals(
		categories: List<CategoryTotal>,
		subcategories: List<SubcategoryTotal>,
		spendingTypes: List<SpendingTypeTotal>,
		dimension: BreakdownDimension,
	): List<BreakdownTotal> = when (dimension) {
		BreakdownDimension.Category -> categories.map {
			BreakdownTotal(
				label = it.category,
				totalCents = it.totalCents,
				filter = SummaryFilter.Category(it.category),
			)
		}

		BreakdownDimension.Subcategory -> subcategories.map {
			BreakdownTotal(
				label = it.subcategory,
				totalCents = it.totalCents,
				filter = SummaryFilter.Subcategory(it.category, it.subcategory),
			)
		}

		BreakdownDimension.SpendingType -> spendingTypes.map {
			BreakdownTotal(
				label = it.spendingType?.let(::spendingTypeLabel) ?: "Nezaradené",
				totalCents = it.totalCents,
				filter = SummaryFilter.ExpenseType(it.spendingType),
			)
		}
	}
		.filter { it.totalCents > 0 }
		.sortedByDescending { it.totalCents }

	fun title(dimension: BreakdownDimension): String = when (dimension) {
		BreakdownDimension.Category -> "VŠETKY KATEGÓRIE"
		BreakdownDimension.Subcategory -> "VŠETKY PODKATEGÓRIE"
		BreakdownDimension.SpendingType -> "VŠETKY TYPY VÝDAVKOV"
	}

	private fun spendingTypeLabel(spendingType: SpendingType): String = when (spendingType) {
		SpendingType.ESSENTIAL -> "Nevyhnutné"
		SpendingType.REGULAR -> "Bežné"
		SpendingType.DISCRETIONARY -> "Voliteľné"
	}
}
