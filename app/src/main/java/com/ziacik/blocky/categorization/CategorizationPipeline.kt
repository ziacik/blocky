package com.ziacik.blocky.categorization

class PassthroughReceiptCategorizer : ReceiptCategorizer {
	override fun categorize(receipt: com.ziacik.blocky.model.Receipt) = receipt
}

class ResilientReceiptCategorizer(
	private val delegate: ReceiptCategorizer,
) : ReceiptCategorizer {
	override fun categorize(receipt: com.ziacik.blocky.model.Receipt) =
		runCatching { delegate.categorize(receipt) }.getOrElse { receipt }
}

object CategorizationPipeline {
	fun create(
		endpoint: String,
		transportFactory: (String) -> CategorizationTransport = ::UrlCategorizationTransport,
	): ReceiptCategorizer {
		if (endpoint.isBlank()) return PassthroughReceiptCategorizer()

		val client = HttpCategorizationClient(transportFactory(endpoint))
		return ResilientReceiptCategorizer(AiReceiptCategorizer(client))
	}
}
