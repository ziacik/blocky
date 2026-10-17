package com.ziacik.blocky.data

object ReceiptQrSelector {
	fun select(values: Iterable<String?>): String? = values
		.asSequence()
		.mapNotNull { it?.trim() }
		.firstOrNull { value ->
			value.isNotEmpty() && runCatching {
				EkasaLookupRequest.fromQr(value)
			}.isSuccess
		}
}
