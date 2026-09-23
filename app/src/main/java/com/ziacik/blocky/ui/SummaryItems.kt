package com.ziacik.blocky.ui

import com.ziacik.blocky.model.ItemListEntry
import com.ziacik.blocky.model.SpendingType

sealed interface SummaryFilter {
	data class Category(val category: String) : SummaryFilter

	data class Subcategory(
		val category: String,
		val subcategory: String,
	) : SummaryFilter

	data class ExpenseType(val spendingType: SpendingType?) : SummaryFilter
}

object SummaryItems {
	fun filter(
		items: List<ItemListEntry>,
		filter: SummaryFilter,
	): List<ItemListEntry> = when (filter) {
		is SummaryFilter.Category -> items.filter { item ->
			item.category == filter.category
		}

		is SummaryFilter.Subcategory -> items.filter { item ->
			item.category == filter.category && item.subcategory == filter.subcategory
		}

		is SummaryFilter.ExpenseType -> items.filter { item ->
			item.spendingType == filter.spendingType
		}
	}

	fun title(filter: SummaryFilter): String = when (filter) {
		is SummaryFilter.Category -> filter.category
		is SummaryFilter.Subcategory -> filter.subcategory
		is SummaryFilter.ExpenseType -> filter.spendingType?.let(::spendingTypeLabel) ?: "Nezaradené"
	}

	private fun spendingTypeLabel(spendingType: SpendingType): String = when (spendingType) {
		SpendingType.ESSENTIAL -> "Nevyhnutné"
		SpendingType.REGULAR -> "Bežné"
		SpendingType.DISCRETIONARY -> "Voliteľné"
	}
}
