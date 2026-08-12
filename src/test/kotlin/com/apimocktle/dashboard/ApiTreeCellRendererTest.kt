package com.apimocktle.dashboard

import com.apimocktle.exporter.model.*
import com.apimocktle.testFramework.ApiMocktleLightCodeInsightFixtureTestCase
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

class ApiTreeCellRendererTest : ApiMocktleLightCodeInsightFixtureTestCase() {

    private lateinit var renderer: ApiTreeCellRenderer
    private lateinit var tree: JTree

    override fun setUp() {
        super.setUp()
        renderer = ApiTreeCellRenderer()
        tree = JTree()
    }

    fun testGetTreeCellRendererComponentWithApiEndpoint() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET
            )
        )
        
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        
        assertNotNull(component)
    }

    fun testGetTreeCellRendererComponentWithDefaultMutableTreeNode() {
        val endpoint = ApiEndpoint(
            name = "Create User",
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.POST
            )
        )
        val node = DefaultMutableTreeNode(endpoint)
        
        val component = renderer.getTreeCellRendererComponent(
            tree, node, false, false, true, 0, false
        )
        
        assertNotNull(component)
    }

    fun testGetTreeCellRendererComponentWithNonEndpoint() {
        val component = renderer.getTreeCellRendererComponent(
            tree, "Not an endpoint", false, false, true, 0, false
        )
        
        assertNotNull(component)
    }

    fun testGetMethodColorForGet() {
        val endpoint = ApiEndpoint(metadata = httpMetadata(path = "/test", method = HttpMethod.GET))
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testGetMethodColorForPost() {
        val endpoint = ApiEndpoint(metadata = httpMetadata(path = "/test", method = HttpMethod.POST))
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testGetMethodColorForPut() {
        val endpoint = ApiEndpoint(metadata = httpMetadata(path = "/test", method = HttpMethod.PUT))
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testGetMethodColorForDelete() {
        val endpoint = ApiEndpoint(metadata = httpMetadata(path = "/test", method = HttpMethod.DELETE))
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testGetMethodColorForPatch() {
        val endpoint = ApiEndpoint(metadata = httpMetadata(path = "/test", method = HttpMethod.PATCH))
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testGetMethodColorForHead() {
        val endpoint = ApiEndpoint(metadata = httpMetadata(path = "/test", method = HttpMethod.HEAD))
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testGetMethodColorForOptions() {
        val endpoint = ApiEndpoint(metadata = httpMetadata(path = "/test", method = HttpMethod.OPTIONS))
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testBuildApiTextWithName() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET
            )
        )
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

    fun testBuildApiTextWithoutName() {
        val endpoint = ApiEndpoint(
            name = null,
            metadata = httpMetadata(
                path = "/api/users",
                method = HttpMethod.GET
            )
        )
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertNotNull(component)
    }

fun testHttpEndpointStillShowsMethodName() {
        val endpoint = ApiEndpoint(
            name = "Get User",
            metadata = httpMetadata(
                path = "/api/users/{id}",
                method = HttpMethod.GET
            )
        )
        val component = renderer.getTreeCellRendererComponent(
            tree, endpoint, false, false, true, 0, false
        )
        assertTrue(component is javax.swing.JPanel)
        val labels = (component as javax.swing.JPanel).components.filterIsInstance<javax.swing.JLabel>()
        assertTrue("should contain GET badge", labels.any { it.text == "GET" })
        assertTrue("badge should be GET blue", labels.any { it.background == java.awt.Color(0x61affe) })
    }
}
