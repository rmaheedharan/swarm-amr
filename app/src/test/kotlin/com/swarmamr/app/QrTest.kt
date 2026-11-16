package com.swarmamr.app

import com.swarmamr.app.ui.ScanRouter
import org.junit.Assert.assertEquals
import org.junit.Test

class QrTest {
    @Test
    fun scansGoToTheLatestScreen() {
        val router = ScanRouter {}
        val got = mutableListOf<String>()
        router.register { got += "root:$it" }
        val off = router.register { got += "screen:$it" }
        router.deliver("SWM:A3-07")
        router.deliver("  ")
        off()
        router.deliver("DESK-12")
        assertEquals(listOf("screen:A3-07", "root:DESK-12"), got)
    }
}
