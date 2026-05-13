package com.apimocktle.util.file

import com.apimocktle.testFramework.ApiMocktleLightCodeInsightFixtureTestCase

class DefaultFileSelectHelperTest : ApiMocktleLightCodeInsightFixtureTestCase() {

    private lateinit var fileSelectHelper: DefaultFileSelectHelper

    override fun setUp() {
        super.setUp()
        fileSelectHelper = DefaultFileSelectHelper()
    }

    fun testHelperExists() {
        assertNotNull("DefaultFileSelectHelper should be created", fileSelectHelper)
    }

    fun testHelperImplementsInterface() {
        assertTrue(
            "DefaultFileSelectHelper should implement FileSelectHelper",
            fileSelectHelper is FileSelectHelper
        )
    }
}
