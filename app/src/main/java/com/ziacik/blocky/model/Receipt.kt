package com.ziacik.blocky.model

data class Receipt(
	val id: String,
	val merchant: String,
	val issuedAt: Long,
	val totalCents: Long,
	val items: List<ReceiptItem>,
	val rawJson: String,
)

data class ReceiptItem(
	val originalName: String,
	val canonicalName: String,
	val category: String,
	val subcategory: String?,
	val quantity: Double,
	val totalCents: Long,
	val vatRate: Double?,
)

data class ReceiptSummary(
	val id: String,
	val merchant: String,
	val issuedAt: Long,
	val totalCents: Long,
)

data class CategoryTotal(
	val category: String,
	val totalCents: Long,
)

data class ProductTotal(
	val product: String,
	val totalCents: Long,
)
