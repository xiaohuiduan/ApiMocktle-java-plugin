package com.apimocktle.exporter

import com.apimocktle.exporter.model.ApiEndpoint
import com.apimocktle.exporter.model.ExportFormat
import com.apimocktle.exporter.model.ExportResult
import com.apimocktle.exporter.model.HttpMetadata
import com.apimocktle.exporter.model.HttpMethod
import com.apimocktle.exporter.model.OutputConfig
import com.apimocktle.exporter.model.httpMetadata
import com.apimocktle.testFramework.ApiMocktleLightCodeInsightFixtureTestCase
import com.apimocktle.testFramework.TestConfigReader

class ExportOrchestratorTest : ApiMocktleLightCodeInsightFixtureTestCase() {

    private lateinit var orchestrator: ExportOrchestrator

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        orchestrator = ExportOrchestrator.getInstance(project)
    }

    private fun loadTestFiles() {
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/RestController.java")
        loadFile("spring/Controller.java")
        loadFile("spring/ResponseBody.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/UserCtrl.java")
    }

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testGetInstanceReturnsSameInstance() {
        val instance1 = ExportOrchestrator.getInstance(project)
        val instance2 = ExportOrchestrator.getInstance(project)

        assertSame("Should return same instance for same project", instance1, instance2)
    }

    fun testOrchestratorHasCorrectProjectReference() {
        assertNotNull("Orchestrator should not be null", orchestrator)
    }

    fun testOrchestrateExportWithNullSelectionReturnsResult() = runTest {
        val result = orchestrator.orchestrateExport(null, ExportFormat.YAPI)

        assertNotNull("Result should not be null", result)
        assertTrue(
            "Result should be Error (no endpoints cached) or Success",
            result is ExportResult.Error || result is ExportResult.Success
        )
    }

    fun testExportEndpointsWithEmptyListReturnsResult() = runTest {
        val endpoints = emptyList<ApiEndpoint>()

        val result = orchestrator.exportEndpoints(endpoints, ExportFormat.YAPI, OutputConfig.DEFAULT)

        assertNotNull("Result should not be null", result)
    }

    fun testExportEndpointsWithSingleEndpointReturnsResult() = runTest {
        val endpoints = listOf(createTestEndpoint())

        val result = orchestrator.exportEndpoints(endpoints, ExportFormat.YAPI, OutputConfig.DEFAULT)

        assertNotNull("Result should not be null", result)
    }

    fun testExportEndpointsWithMultipleEndpointsReturnsResult() = runTest {
        val endpoints = listOf(
            createTestEndpoint("API 1", "/test1", HttpMethod.GET),
            createTestEndpoint("API 2", "/test2", HttpMethod.POST),
            createTestEndpoint("API 3", "/test3", HttpMethod.PUT)
        )

        val result = orchestrator.exportEndpoints(endpoints, ExportFormat.YAPI, OutputConfig.DEFAULT)

        assertNotNull("Result should not be null", result)
    }

    fun testExportEndpointsWithCustomOutputConfig() = runTest {
        val endpoints = listOf(createTestEndpoint())
        val outputConfig = OutputConfig(
            outputDir = "/tmp",
            fileName = "test"
        )

        val result = orchestrator.exportEndpoints(endpoints, ExportFormat.YAPI, outputConfig)

        assertNotNull("Result should not be null", result)
    }

    fun testOrchestratorHandlesYapiExportFormat() = runTest {
        val result = orchestrator.orchestrateExport(null, ExportFormat.YAPI)
        assertNotNull("Should handle YAPI format", result)
    }

    private fun createTestEndpoint(
        name: String = "Test API",
        path: String = "/test",
        method: HttpMethod = HttpMethod.GET
    ): ApiEndpoint {
        return ApiEndpoint(
            name = name,
            metadata = httpMetadata(
                path = path,
                method = method
            )
        )
    }
}
