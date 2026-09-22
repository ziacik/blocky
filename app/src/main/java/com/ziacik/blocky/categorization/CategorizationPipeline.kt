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
		apiKey: String = "",
		transportFactory: (String) -> CategorizationTransport = ::UrlCategorizationTransport,
		openAiClientFactory: (String) -> CategorizationClient = ::OpenAiCategorizationClient,
	): ReceiptCategorizer {
		val client = when {
			apiKey.isNotBlank() -> openAiClientFactory(apiKey)
			endpoint.isNotBlank() -> HttpCategorizationClient(transportFactory(endpoint))
			else -> return PassthroughReceiptCategorizer()
		}

		return ResilientReceiptCategorizer(AiReceiptCategorizer(client))
	}
}
