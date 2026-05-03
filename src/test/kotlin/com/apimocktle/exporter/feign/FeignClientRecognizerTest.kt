package com.apimocktle.exporter.feign

import com.apimocktle.rule.engine.RuleEngine
import com.apimocktle.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.apimocktle.testFramework.TestConfigReader

class FeignClientRecognizerTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var recognizer: FeignClientRecognizer

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        val ruleEngine = RuleEngine.getInstance(project)
        recognizer = FeignClientRecognizer(ruleEngine, enabled = true)
    }

    private fun loadTestFiles() {
        loadFile("spring/FeignClient.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/PostMapping.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("api/feign/UserClient.java")
    }

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testRecognizeFeignClient() = runTest {
        val psiClass = findClass("com.itangcent.springboot.demo.client.UserClient")
        assertNotNull(psiClass)

        val isFeignClient = recognizer.isFeignClient(psiClass!!)
        assertTrue("Should recognize @FeignClient class as Feign client", isFeignClient)
    }

    fun testRecognizeNonFeignClient() = runTest {
        val psiClass = findClass("com.itangcent.model.UserInfo")
        assertNotNull(psiClass)

        val isFeignClient = recognizer.isFeignClient(psiClass!!)
        assertFalse("Should not recognize model class as Feign client", isFeignClient)
    }
}
