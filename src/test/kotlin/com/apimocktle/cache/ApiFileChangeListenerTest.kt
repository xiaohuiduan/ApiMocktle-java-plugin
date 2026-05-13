package com.apimocktle.cache

import com.apimocktle.settings.SettingBinder
import com.apimocktle.testFramework.ApiMocktleLightCodeInsightFixtureTestCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class ApiFileChangeListenerTest : ApiMocktleLightCodeInsightFixtureTestCase() {

    private lateinit var listener: ApiFileChangeListener

    override fun setUp() {
        super.setUp()
        listener = ApiFileChangeListener.getInstance(project)
    }

    override fun tearDown() {
        runBlocking {
            listener.dispose()
        }
        super.tearDown()
    }

    fun testGetInstance() {
        assertNotNull(listener)
        assertSame(listener, ApiFileChangeListener.getInstance(project))
    }

    fun testStart() {
        listener.start()
    }

    fun testAfterWithNoEvents() {
        listener.start()
        listener.after(mutableListOf())
        runBlocking {
            delay(100)
        }
    }

    fun testAutoScanDisabled() {
        val settings = SettingBinder.getInstance(project).read()
        settings.autoScanEnabled = false
        SettingBinder.getInstance(project).save(settings)

        listener.start()
        listener.after(mutableListOf())

        runBlocking {
            delay(100)
        }

        // Reset
        settings.autoScanEnabled = true
        SettingBinder.getInstance(project).save(settings)
    }
}
