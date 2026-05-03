package com.apimocktle.gap

import com.apimocktle.exporter.feign.FeignClientRecognizer
import com.apimocktle.exporter.jaxrs.JaxRsContentTypeResolver
import com.apimocktle.exporter.jaxrs.JaxRsResourceRecognizer
import com.apimocktle.exporter.springmvc.ContentTypeResolver
import com.apimocktle.exporter.springmvc.SpringControllerRecognizer
import com.apimocktle.psi.helper.UnifiedAnnotationHelper
import com.apimocktle.rule.engine.RuleEngine
import com.apimocktle.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.apimocktle.testFramework.TestConfigReader
import org.junit.Assert.*

class PipelineComponentParityTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testSpringControllerRecognizerExists() = runTest {
        val ruleEngine = RuleEngine.getInstance(project)
        val recognizer = SpringControllerRecognizer(ruleEngine)
        assertNotNull("SpringControllerRecognizer should exist", recognizer)
    }

    fun testJaxRsResourceRecognizerExists() = runTest {
        val ruleEngine = RuleEngine.getInstance(project)
        val recognizer = JaxRsResourceRecognizer(ruleEngine)
        assertNotNull("JaxRsResourceRecognizer should exist", recognizer)
    }

    fun testFeignClientRecognizerExists() = runTest {
        val ruleEngine = RuleEngine.getInstance(project)
        val recognizer = FeignClientRecognizer(ruleEngine)
        assertNotNull("FeignClientRecognizer should exist", recognizer)
    }

    fun testContentTypeResolverExists() = runTest {
        val annotationHelper = UnifiedAnnotationHelper()
        val ruleEngine = RuleEngine.getInstance(project)
        val resolver = ContentTypeResolver(annotationHelper, ruleEngine)
        assertNotNull("ContentTypeResolver should exist", resolver)
    }

    fun testJaxRsContentTypeResolverExists() = runTest {
        val annotationHelper = UnifiedAnnotationHelper()
        val resolver = JaxRsContentTypeResolver(annotationHelper)
        assertNotNull("JaxRsContentTypeResolver should exist", resolver)
    }
}
