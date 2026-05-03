package com.apimocktle.exporter.jaxrs

import com.apimocktle.rule.engine.RuleEngine
import com.apimocktle.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.apimocktle.testFramework.TestConfigReader

class JaxRsResourceRecognizerTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var recognizer: JaxRsResourceRecognizer

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        val ruleEngine = RuleEngine.getInstance(project)
        recognizer = JaxRsResourceRecognizer(ruleEngine, enabled = true)
    }

    private fun loadTestFiles() {
        loadFile("jaxrs/Path.java")
        loadFile("jaxrs/GET.java")
        loadFile("jaxrs/POST.java")
        loadFile("jaxrs/PUT.java")
        loadFile("jaxrs/DELETE.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
        loadFile("constant/UserType.java")
        loadFile("api/jaxrs/UserResource.java")
    }

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testRecognizeJaxRsResource() = runTest {
        val psiClass = findClass("com.itangcent.jaxrs.UserResource")
        assertNotNull(psiClass)

        val isResource = recognizer.isResource(psiClass!!)
        assertTrue("Should recognize @Path class as JAX-RS resource", isResource)
    }

    fun testRecognizeNonResource() = runTest {
        val psiClass = findClass("com.itangcent.model.UserInfo")
        assertNotNull(psiClass)

        val isResource = recognizer.isResource(psiClass!!)
        assertFalse("Should not recognize model class as JAX-RS resource", isResource)
    }
}
