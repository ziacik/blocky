package com.ziacik.blocky.normalization

import org.junit.Assert.assertEquals
import org.junit.Test

class HeuristicItemNormalizerTest {
	private val normalizer = HeuristicItemNormalizer()

	@Test
	fun normalizesDifferentEidamNamesToOneProduct() {
		val first = normalizer.normalize("TEHLA EIDAM 30%")
		val second = normalizer.normalize("EIDAM BLOK 400G")

		assertEquals("Eidam", first.canonicalName)
		assertEquals(first.canonicalName, second.canonicalName)
		assertEquals("Potraviny", first.category)
	}

	@Test
	fun stripsPackageSizeFromNormalizationKey() {
		assertEquals("MLIEKO PLNOTUCNE", HeuristicItemNormalizer.normalizationKey("Mlieko plnotučné 1 L"))
	}
}
