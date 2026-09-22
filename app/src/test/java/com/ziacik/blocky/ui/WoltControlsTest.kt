package com.ziacik.blocky.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WoltControlsTest {
	@Test
	fun disconnectedShowsOnlyConnect() {
		assertEquals(
			listOf(WoltAction.Connect),
			WoltControls.actions(connected = false, busy = false),
		)
	}

	@Test
	fun connectedShowsOnlyCurrentMonthSync() {
		assertEquals(
			listOf(WoltAction.CurrentMonth),
			WoltControls.actions(connected = true, busy = false),
		)
	}

	@Test
	fun busyShowsNoAction() {
		assertEquals(
			emptyList<WoltAction>(),
			WoltControls.actions(connected = true, busy = true),
		)
	}
}
