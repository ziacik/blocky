package com.ziacik.blocky.ui

import com.ziacik.blocky.model.ReceiptItem
import com.ziacik.blocky.model.SpendingType
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassificationEditorStateTest {
	@Test
	fun usesRegularWhenItemHasNoSpendingType() {
		val state = ClassificationEditorState.from(item())

		assertEquals("Potraviny", state.category)
		assertEquals("Sladkosti", state.subcategory)
		assertEquals(SpendingType.REGULAR, state.spendingType)
	}

	@Test
	fun changingCategorySelectsValidSubcategory() {
		val changed = ClassificationEditorState.from(item())
			.selectCategory("Drogéria")

		assertEquals("Drogéria", changed.category)
		assertEquals("Osobná hygiena", changed.subcategory)
	}

	private fun item() = ReceiptItem(
		originalName = "MILKA",
		canonicalName = "Milka",
		category = "Potraviny",
		subcategory = "Sladkosti",
		quantity = 1.0,
		totalCents = 199L,
		vatRate = null,
	)
}
