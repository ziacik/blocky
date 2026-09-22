package com.ziacik.blocky.normalization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeuristicItemNormalizerTest {
	private val normalizer = HeuristicItemNormalizer()

	@Test
	fun leavesCategoryUnclassifiedWithoutReceiptContext() {
		val item = normalizer.normalize("ROHLÍK BIELY 50G")

		assertEquals("ROHLÍK BIELY 50G", item.canonicalName)
		assertEquals("Nezaradené", item.category)
		assertNull(item.subcategory)
	}
}
