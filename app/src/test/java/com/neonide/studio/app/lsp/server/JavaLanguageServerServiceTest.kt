package com.neonide.studio.app.lsp.server

import org.junit.Assert.assertNotNull
import org.junit.Test

class JavaLanguageServerServiceTest {

    @Test
    fun testServiceCreation() {
        val service = JavaLanguageServerService()
        assertNotNull("Service should be created", service)
    }
}
