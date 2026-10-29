package com.swarmamr.app

import com.swarmamr.app.ui.parseMarkerQr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrTest {
    @Test
    fun parsesMarkerLabels() {
        assertEquals("A3-07", parseMarkerQr("SWM:A3-07"))
        assertEquals("A3-07", parseMarkerQr(" A3-07 "))
        assertNull(parseMarkerQr("  "))
        assertNull(parseMarkerQr(null))
    }
}
