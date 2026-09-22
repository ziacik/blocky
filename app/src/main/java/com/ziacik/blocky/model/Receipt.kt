package com.ziacik.blocky.model

data class Receipt(
	val id: String,
	val merchant: String,
	val issuedAt: Long,
	val totalCents: Long,
	val items: List<ReceiptItem>,
	val rawJson: String,
)

enum class SpendingType {
	ESSENTIAL,
	REGULAR,
	DISCRETIONARY,
}

enum class ClassificationSource {
	AI,
	USER,
}

data class ReceiptItem(
	val originalName: String,
	val canonicalName: String,
	val category: String,
	val subcategory: String?,
	val quantity: Double,
	val totalCents: Long,
	val vatRate: Double?,
	val spendingType: SpendingType? = null,
	val classificationConfidence: Double? = null,
	val classificationSource: ClassificationSource? = null,
)

data class ReceiptSummary(
	val id: String,
	val merchant: String,
	val issuedAt: Long,
	val totalCents: Long,
)

data class ItemListEntry(
	val receiptId: String,
	val merchant: String,
	val issuedAt: Long,
	val originalName: String,
	val canonicalName: String,
	val category: String,
	val subcategory: String?,
	val quantity: Double,
	val totalCents: Long,
	val spendingType: SpendingType? = null,
	val classificationConfidence: Double? = null,
	val classificationSource: ClassificationSource? = null,
)

data class CategoryTotal(
	val category: String,
	val totalCents: Long,
)

data class SubcategoryTotal(
	val category: String,
	val subcategory: String,
	val totalCents: Long,
)

data class ProductTotal(
	val product: String,
	val totalCents: Long,
)
