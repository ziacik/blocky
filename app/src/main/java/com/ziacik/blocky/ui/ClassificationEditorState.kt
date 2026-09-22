package com.ziacik.blocky.ui

import com.ziacik.blocky.categorization.ExpenseTaxonomy
import com.ziacik.blocky.model.ReceiptItem
import com.ziacik.blocky.model.SpendingType

data class ClassificationEditorState(
	val category: String,
	val subcategory: String?,
	val spendingType: SpendingType,
) {
	fun selectCategory(category: String): ClassificationEditorState {
		val subcategories = requireNotNull(ExpenseTaxonomy.categories[category]) {
			"Unknown category: $category"
		}
		return copy(
			category = category,
			subcategory = subcategories.firstOrNull(),
		)
	}

	fun selectSubcategory(subcategory: String): ClassificationEditorState {
		require(subcategory in ExpenseTaxonomy.categories[category].orEmpty()) {
			"Unknown subcategory '$subcategory' for '$category'"
		}
		return copy(subcategory = subcategory)
	}

	fun selectSpendingType(spendingType: SpendingType): ClassificationEditorState =
		copy(spendingType = spendingType)

	companion object {
		fun from(item: ReceiptItem): ClassificationEditorState {
			val category = item.category.takeIf(ExpenseTaxonomy.categories::containsKey)
				?: ExpenseTaxonomy.categories.keys.first()
			val subcategories = ExpenseTaxonomy.categories.getValue(category)
			val subcategory = item.subcategory?.takeIf(subcategories::contains)
				?: subcategories.firstOrNull()
			return ClassificationEditorState(
				category = category,
				subcategory = subcategory,
				spendingType = item.spendingType ?: SpendingType.REGULAR,
			)
		}
	}
}
