package com.apimocktle.util.file

import com.apimocktle.testFramework.ApiMocktleLightCodeInsightFixtureTestCase
import java.nio.charset.StandardCharsets

class DefaultFileSaveHelperTest : ApiMocktleLightCodeInsightFixtureTestCase() {

    private lateinit var fileSaveHelper: DefaultFileSaveHelper

    override fun setUp() {
        super.setUp()
        fileSaveHelper = DefaultFileSaveHelper(StandardCharsets.UTF_8)
    }

    fun testHelperExists() {
        assertNotNull("DefaultFileSaveHelper should be created", fileSaveHelper)
    }

    fun testHelperImplementsInterface() {
        assertTrue(
            "DefaultFileSaveHelper should implement FileSaveHelper",
            fileSaveHelper is FileSaveHelper
        )
    }

    fun testDefaultCharset() {
        val helper = DefaultFileSaveHelper()
        assertNotNull("DefaultFileSaveHelper should be created with default charset", helper)
    }

    fun testCustomCharset() {
        val helper = DefaultFileSaveHelper(StandardCharsets.ISO_8859_1)
        assertNotNull("DefaultFileSaveHelper should be created with custom charset", helper)
    }
}
