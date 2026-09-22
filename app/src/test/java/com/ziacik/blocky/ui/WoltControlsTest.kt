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
	fun connectedShowsLatestAndCurrentMonthDownloads() {
		assertEquals(
			listOf(WoltAction.Latest, WoltAction.CurrentMonth),
			WoltControls.actions(connected = true, busy = false),
		)
	}
}
