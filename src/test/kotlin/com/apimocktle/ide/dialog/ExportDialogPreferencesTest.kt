package com.apimocktle.ide.dialog

import com.apimocktle.testFramework.ApiMocktleLightCodeInsightFixtureTestCase
import org.junit.Assert.*

class ExportDialogPreferencesPersistenceTest : ApiMocktleLightCodeInsightFixtureTestCase() {

    private lateinit var persistence: ExportDialogPreferencesPersistence

    override fun setUp() {
        super.setUp()
        persistence = ExportDialogPreferencesPersistence(project)
        persistence.reset()
    }

    override fun tearDown() {
        persistence.reset()
        super.tearDown()
    }

    fun testLoadEmptyPreferences() {
        val prefs = persistence.load()
        assertNull("Last export format should be null", prefs.lastExportFormat)
        assertNull("Last output dir should be null", prefs.lastOutputDir)
        assertNull("Last file name should be null", prefs.lastFileName)
        assertNull("Last yapi token should be null", prefs.lastYapiToken)
    }

    fun testSaveAndLoadPreferences() {
        val prefs = ExportDialogPreferences(
            lastExportFormat = "YAPI",
            lastOutputDir = "/tmp/output",
            lastFileName = "api_doc"
        )

        persistence.save(prefs)
        val loaded = persistence.load()

        assertEquals("YAPI", loaded.lastExportFormat)
        assertEquals("/tmp/output", loaded.lastOutputDir)
        assertEquals("api_doc", loaded.lastFileName)
    }

    fun testSaveAndLoadYapiToken() {
        val prefs = ExportDialogPreferences(
            lastExportFormat = "YAPI",
            lastYapiToken = "abc123def456"
        )

        persistence.save(prefs)
        val loaded = persistence.load()

        assertEquals("YAPI", loaded.lastExportFormat)
        assertEquals("abc123def456", loaded.lastYapiToken)
    }

    fun testOverwritePreferences() {
        val prefs1 = ExportDialogPreferences(
            lastExportFormat = "YAPI",
            lastOutputDir = "/tmp/output1"
        )
        persistence.save(prefs1)

        val prefs2 = ExportDialogPreferences(
            lastExportFormat = "YAPI",
            lastYapiToken = "token-789"
        )
        persistence.save(prefs2)

        val loaded = persistence.load()
        assertEquals("YAPI", loaded.lastExportFormat)
        assertNull("Output dir should be null after overwrite", loaded.lastOutputDir)
        assertEquals("token-789", loaded.lastYapiToken)
    }

    fun testReset() {
        val prefs = ExportDialogPreferences(
            lastExportFormat = "YAPI",
            lastOutputDir = "/tmp/test"
        )
        persistence.save(prefs)

        persistence.reset()

        val loaded = persistence.load()
        assertNull("Last export format should be null after reset", loaded.lastExportFormat)
        assertNull("Last output dir should be null after reset", loaded.lastOutputDir)
    }

    fun testPartialPreferences() {
        val prefs = ExportDialogPreferences(
            lastExportFormat = "YAPI"
        )

        persistence.save(prefs)
        val loaded = persistence.load()

        assertEquals("YAPI", loaded.lastExportFormat)
        assertNull("Other fields should be null", loaded.lastOutputDir)
        assertNull("Other fields should be null", loaded.lastFileName)
        assertNull("Other fields should be null", loaded.lastYapiToken)
    }

    fun testEmptyStrings() {
        val prefs = ExportDialogPreferences(
            lastExportFormat = "",
            lastOutputDir = "",
            lastFileName = ""
        )

        persistence.save(prefs)
        val loaded = persistence.load()

        assertEquals("Empty strings should be preserved", "", loaded.lastExportFormat)
        assertEquals("Empty strings should be preserved", "", loaded.lastOutputDir)
        assertEquals("Empty strings should be preserved", "", loaded.lastFileName)
    }

    fun testSpecialCharacters() {
        val prefs = ExportDialogPreferences(
            lastExportFormat = "YAPI",
            lastOutputDir = "/tmp/测试目录/папка",
            lastFileName = "api-文档_v1.0"
        )

        persistence.save(prefs)
        val loaded = persistence.load()

        assertEquals("/tmp/测试目录/папка", loaded.lastOutputDir)
        assertEquals("api-文档_v1.0", loaded.lastFileName)
    }

    fun testLongValues() {
        val longPath = "/tmp/" + "a".repeat(500)
        val longName = "b".repeat(500)

        val prefs = ExportDialogPreferences(
            lastExportFormat = "YAPI",
            lastOutputDir = longPath,
            lastFileName = longName
        )

        persistence.save(prefs)
        val loaded = persistence.load()

        assertEquals(longPath, loaded.lastOutputDir)
        assertEquals(longName, loaded.lastFileName)
    }

    fun testAllFields() {
        val prefs = ExportDialogPreferences(
            lastExportFormat = "YAPI",
            lastOutputDir = "/output",
            lastFileName = "export",
            lastYapiToken = "yapi-token-abc"
        )

        persistence.save(prefs)
        val loaded = persistence.load()

        assertEquals("YAPI", loaded.lastExportFormat)
        assertEquals("/output", loaded.lastOutputDir)
        assertEquals("export", loaded.lastFileName)
        assertEquals("yapi-token-abc", loaded.lastYapiToken)
    }

    fun testMultipleSaveLoadCycles() {
        for (i in 1..5) {
            val prefs = ExportDialogPreferences(
                lastExportFormat = "FORMAT_$i",
                lastOutputDir = "/dir_$i"
            )
            persistence.save(prefs)
            val loaded = persistence.load()
            assertEquals("FORMAT_$i", loaded.lastExportFormat)
            assertEquals("/dir_$i", loaded.lastOutputDir)
        }
    }

    fun testPreferencesDataClassDefaultValues() {
        val prefs = ExportDialogPreferences()
        assertNull(prefs.lastExportFormat)
        assertNull(prefs.lastOutputDir)
        assertNull(prefs.lastFileName)
        assertNull(prefs.lastYapiToken)
    }

    fun testPreferencesDataClassCopy() {
        val original = ExportDialogPreferences(
            lastExportFormat = "YAPI",
            lastOutputDir = "/tmp",
            lastFileName = "test",
            lastYapiToken = "yapi-tok"
        )

        val copy = original.copy()

        assertEquals(original.lastExportFormat, copy.lastExportFormat)
        assertEquals(original.lastOutputDir, copy.lastOutputDir)
        assertEquals(original.lastFileName, copy.lastFileName)
        assertEquals(original.lastYapiToken, copy.lastYapiToken)
    }

    fun testPreferencesDataClassEquality() {
        val prefs1 = ExportDialogPreferences(
            lastExportFormat = "YAPI",
            lastOutputDir = "/tmp"
        )
        val prefs2 = ExportDialogPreferences(
            lastExportFormat = "YAPI",
            lastOutputDir = "/tmp"
        )
        val prefs3 = ExportDialogPreferences(
            lastExportFormat = "YAPI",
            lastOutputDir = "/other"
        )

        assertEquals(prefs1, prefs2)
        assertNotEquals(prefs1, prefs3)
    }

    fun testPreferencesDataClassHashCode() {
        val prefs1 = ExportDialogPreferences(
            lastExportFormat = "YAPI",
            lastOutputDir = "/tmp"
        )
        val prefs2 = ExportDialogPreferences(
            lastExportFormat = "YAPI",
            lastOutputDir = "/tmp"
        )

        assertEquals(prefs1.hashCode(), prefs2.hashCode())
    }

    fun testPreferencesDataClassToString() {
        val prefs = ExportDialogPreferences(
            lastExportFormat = "YAPI",
            lastOutputDir = "/tmp"
        )

        val str = prefs.toString()
        assertTrue("toString should contain lastExportFormat", str.contains("YAPI"))
        assertTrue("toString should contain lastOutputDir", str.contains("/tmp"))
    }
}
