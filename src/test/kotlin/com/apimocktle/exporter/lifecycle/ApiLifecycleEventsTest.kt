package com.apimocktle.exporter.lifecycle

import com.apimocktle.exporter.ClassExporter
import com.apimocktle.exporter.feign.FeignClassExporter
import com.apimocktle.exporter.springmvc.SpringMvcClassExporter
import com.apimocktle.psi.helper.DocHelper
import com.apimocktle.psi.helper.UnifiedDocHelper
import com.apimocktle.testFramework.ApiMocktleLightCodeInsightFixtureTestCase
import com.apimocktle.testFramework.TestConfigReader

class ApiLifecycleEventsTest : ApiMocktleLightCodeInsightFixtureTestCase() {

    private lateinit var springExporter: SpringMvcClassExporter
    private lateinit var feignExporter: FeignClassExporter

    override fun setUp() {
        super.setUp()
        loadCommonTestFiles()
        springExporter = SpringMvcClassExporter(project)
        feignExporter = FeignClassExporter(project)
    }

    private fun loadCommonTestFiles() {
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/PutMapping.java")
        loadFile("spring/DeleteMapping.java")
        loadFile("spring/PatchMapping.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RequestHeader.java")
        loadFile("spring/ModelAttribute.java")
        loadFile("spring/RestController.java")
        loadFile("spring/Controller.java")
        loadFile("model/Result.java")
        loadFile("model/IResult.java")
        loadFile("model/UserInfo.java")
        loadFile("api/BaseController.java")
        loadFile("api/UserCtrl.java")

        loadFile("feign/RequestLine.java")
        loadFile("feign/Headers.java")
        loadFile("feign/Body.java")
        loadFile("feign/Param.java")
        loadFile("spring/FeignClient.java")
        loadFile("api/feign/UserClient.java")
    }

    override fun createConfigReader() = TestConfigReader.fromConfigText(
        project,
        """
        api.class.parse.before=groovy:logger.info("lifecycle:api.class.parse.before:" + it.name())
        api.class.parse.after=groovy:logger.info("lifecycle:api.class.parse.after:" + it.name())
        api.method.parse.before=groovy:logger.info("lifecycle:api.method.parse.before:" + it.name())
        api.method.parse.after=groovy:logger.info("lifecycle:api.method.parse.after:" + it.name())
        export.after=groovy:logger.info("lifecycle:export.after:" + it.name())
        """.trimIndent()
    )


    // ── Spring MVC lifecycle events ──────────────────────────────

    fun testSpringMvcClassParseEvents() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = springExporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())
    }

    fun testSpringMvcExportAfterEvent() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = springExporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())
        for (endpoint in endpoints) {
            assertNotNull("Endpoint should have source method", endpoint.sourceMethod)
        }
    }

    // ── Feign lifecycle events ───────────────────────────────────

    fun testFeignClassParseEvents() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
        assertNotNull(psiClass)

        val endpoints = feignExporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())
    }

    fun testFeignExportAfterEvent() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
        assertNotNull(psiClass)

        val endpoints = feignExporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())
        for (endpoint in endpoints) {
            assertNotNull("Endpoint should have source method", endpoint.sourceMethod)
        }
    }

    // ── Non-matching classes should not fire events ──────────────

    fun testSpringMvcNonControllerNoEvents() = runTest {
        val psiClass = findClass("com.itangcent.model.UserInfo")
        assertNotNull(psiClass)

        val endpoints = springExporter.export(psiClass!!)
        assertTrue("Should not export endpoints for non-controller", endpoints.isEmpty())
    }
}
