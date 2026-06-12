package com.apimocktle.integration

import com.apimocktle.config.ConfigReader
import com.apimocktle.exporter.feign.FeignClassExporter
import com.apimocktle.exporter.springmvc.SpringMvcClassExporter
import com.apimocktle.psi.helper.DocHelper
import com.apimocktle.psi.helper.UnifiedDocHelper
import com.apimocktle.testFramework.ApiMocktleLightCodeInsightFixtureTestCase
import com.apimocktle.testFramework.TestConfigReader

class MultiFrameworkExportTest : ApiMocktleLightCodeInsightFixtureTestCase() {

    private lateinit var springExporter: SpringMvcClassExporter
    private lateinit var feignExporter: FeignClassExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        springExporter = SpringMvcClassExporter(project)
        feignExporter = FeignClassExporter(project)
    }

    private fun loadTestFiles() {
        loadFile("org/springframework/stereotype/Component.java")
        loadFile("org/springframework/stereotype/Controller.java")
        loadFile("spring/RestController.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("spring/RequestMapping.java")
        loadFile("spring/RequestBody.java")
        loadFile("spring/RequestParam.java")
        loadFile("spring/PathVariable.java")
        loadFile("spring/FeignClient.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/UserCtrl.java")
        loadFile("api/feign/UserClient.java")
    }

    override fun createConfigReader() = TestConfigReader.empty(project)


    fun testExportSpringMvcController() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)

        val endpoints = springExporter.export(psiClass!!)
        assertTrue("Spring MVC exporter should export endpoints", endpoints.isNotEmpty())
    }

    fun testExportFeignClient() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
        assertNotNull(psiClass)

        val endpoints = feignExporter.export(psiClass!!)
        assertTrue("Feign exporter should export endpoints", endpoints.isNotEmpty())
    }
}
