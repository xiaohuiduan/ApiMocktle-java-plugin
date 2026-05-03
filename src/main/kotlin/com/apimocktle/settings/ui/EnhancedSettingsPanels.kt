package com.apimocktle.settings.ui

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.apimocktle.settings.Settings
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Utility object for validating settings input values.
 */
object ValidationUtils {
    /**
     * Validates a URL string.
     * 
     * @param text The URL to validate
     * @return true if valid URL or blank
     */
    fun validateUrl(text: String?): Boolean {
        if (text.isNullOrBlank()) return true
        return try {
            val uri = java.net.URI(text)
            uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Validates an integer string with optional range.
     * 
     * @param text The integer string to validate
     * @param min Minimum allowed value
     * @param max Maximum allowed value
     * @return true if valid integer within range or blank
     */
    fun validateInteger(text: String?, min: Int? = null, max: Int? = null): Boolean {
        if (text.isNullOrBlank()) return true
        return try {
            val value = text.toInt()
            (min == null || value >= min) && (max == null || value <= max)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Validates a token string.
     * Requires minimum 8 characters.
     * 
     * @param text The token to validate
     * @return true if valid token or blank
     */
    fun validateToken(text: String?): Boolean {
        if (text.isNullOrBlank()) return true
        return text.length >= 8
    }
}

/**
 * Abstract base class for settings panels with validation support.
 * Provides validation error tracking and display.
 */
abstract class ValidatedPanel : JPanel(BorderLayout()), SettingsPanel {
    /** Map of components to their validation error messages */
    protected val validationErrors = mutableMapOf<JComponent, String>()
    
    /**
     * Adds a validation error for a component.
     */
    protected fun addValidationError(component: JComponent, error: String) {
        validationErrors[component] = error
        component.toolTipText = error
        component.background = java.awt.Color(255, 230, 230)
    }
    
    /**
     * Clears the validation error for a component.
     */
    protected fun clearValidationError(component: JComponent) {
        validationErrors.remove(component)
        component.toolTipText = null
        component.background = null
    }
    
    /**
     * Checks if any validation errors exist.
     */
    protected fun hasValidationErrors(): Boolean = validationErrors.isNotEmpty()
    
    /**
     * Returns all validation error messages.
     */
    protected fun getValidationErrors(): String = validationErrors.values.joinToString("\n")
}

/**
 * 增强型通用设置面板，带验证功能。
 */
class EnhancedGeneralSettingsPanel : ValidatedPanel() {
    private val builtInCheckbox = JCheckBox("启用内置配置")

    private val yapiServerField = JTextField(30)
    private val yapiTokenTextArea = JTextArea(4, 30)

    private val resetButton = JButton("重置为默认")
    
    override val component: JComponent = this
    
    init {
        val mainPanel = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }
        
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(5, 5, 5, 5)
        }
        
        var row = 0
        
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        mainPanel.add(createSectionHeader("通用设置"), gbc)

        row++
        gbc.gridy = row
        gbc.gridwidth = 1
        gbc.gridx = 0
        mainPanel.add(builtInCheckbox, gbc)

        row++
        gbc.gridy = row
        gbc.gridwidth = 2
        mainPanel.add(createSectionHeader("YAPI 设置"), gbc)
        
        row++
        gbc.gridy = row
        gbc.gridwidth = 1
        gbc.gridx = 0
        mainPanel.add(JBLabel("YAPI服务器URL："), gbc)
        gbc.gridx = 1
        mainPanel.add(yapiServerField, gbc)
        
        row++
        gbc.gridy = row
        gbc.gridwidth = 1
        gbc.gridx = 0
        gbc.anchor = GridBagConstraints.NORTHWEST
        mainPanel.add(JBLabel("令牌："), gbc)
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.BOTH
        gbc.weighty = 1.0
        mainPanel.add(JScrollPane(yapiTokenTextArea), gbc)
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weighty = 0.0
        gbc.anchor = GridBagConstraints.CENTER
        
        row++
        gbc.gridy = row
        gbc.gridx = 0
        gbc.gridwidth = 2
        mainPanel.add(createButtonPanel(), gbc)
        
        add(mainPanel, BorderLayout.CENTER)
        
        setupValidation()
        setupTooltips()
        setupResetButton()
    }
    
    private fun createSectionHeader(text: String): JLabel {
        return JLabel(text).apply {
            font = font.deriveFont(java.awt.Font.BOLD, font.size + 2f)
            border = BorderFactory.createEmptyBorder(10, 0, 5, 0)
        }
    }
    
    private fun createButtonPanel(): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(resetButton)
        }
    }
    
    private fun setupValidation() {
        yapiServerField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = validateYapiServer()
            override fun removeUpdate(e: DocumentEvent?) = validateYapiServer()
            override fun changedUpdate(e: DocumentEvent?) = validateYapiServer()
        })
    }
    
    private fun validateYapiServer() {
        val text = yapiServerField.text
        if (text.isNotBlank() && !ValidationUtils.validateUrl(text)) {
            addValidationError(yapiServerField, "URL格式无效，必须以 http:// 或 https:// 开头")
        } else {
            clearValidationError(yapiServerField)
        }
    }
    
    private fun setupTooltips() {
        builtInCheckbox.toolTipText = "启用常用框架的内置配置规则"
        yapiServerField.toolTipText = "YAPI 服务器 URL（如 http://yapi.example.com）"
        yapiTokenTextArea.toolTipText = "YAPI 令牌，格式：模块=令牌（每行一个）。也可在导出时输入。"
    }
    
    private fun setupResetButton() {
        resetButton.addActionListener {
            resetToDefaults()
        }
    }
    
    private fun resetToDefaults() {
        builtInCheckbox.isSelected = true
        yapiServerField.text = ""
        yapiTokenTextArea.text = ""
    }

    override fun resetFrom(settings: Settings?) {
        builtInCheckbox.isSelected = true
        yapiServerField.text = settings?.yapiServer ?: ""
        yapiTokenTextArea.text = settings?.yapiPersonalToken ?: ""
    }

    override fun applyTo(settings: Settings) {
        if (hasValidationErrors()) {
            throw IllegalArgumentException("验证错误：\n${getValidationErrors()}")
        }

        settings.yapiServer = yapiServerField.text.takeIf { it.isNotBlank() }
        settings.yapiPersonalToken = yapiTokenTextArea.text.takeIf { it.isNotBlank() }
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        return yapiServerField.text != (s.yapiServer ?: "") ||
            yapiTokenTextArea.text != (s.yapiPersonalToken ?: "")
    }
}

class EnhancedOtherSettingsPanel : ValidatedPanel() {
    private val charsetField = JTextField(20)
    private val unsafeSslCheckbox = JCheckBox("允许不安全的SSL连接")
    private val httpTimeoutField = JTextField(10)
    
    private val resetButton = JButton("重置为默认")
    
    override val component: JComponent = this
    
    init {
        val mainPanel = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }
        
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(5, 5, 5, 5)
        }
        
        var row = 0
        
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        mainPanel.add(createSectionHeader("Output Settings"), gbc)
        
        row++
        gbc.gridy = row
        gbc.gridwidth = 1
        gbc.gridx = 0
        mainPanel.add(JBLabel("输出字符集："), gbc)
        gbc.gridx = 1
        mainPanel.add(charsetField, gbc)
        
        row++
        gbc.gridy = row
        gbc.gridwidth = 2
        mainPanel.add(createSectionHeader("HTTP 设置"), gbc)
        
        row++
        gbc.gridy = row
        gbc.gridwidth = 1
        mainPanel.add(unsafeSslCheckbox, gbc)
        
        row++
        gbc.gridy = row
        gbc.gridx = 0
        mainPanel.add(JBLabel("HTTP超时（毫秒）："), gbc)
        gbc.gridx = 1
        mainPanel.add(httpTimeoutField, gbc)
        
        row++
        gbc.gridy = row
        gbc.gridx = 0
        gbc.gridwidth = 2
        mainPanel.add(createButtonPanel(), gbc)
        
        add(mainPanel, BorderLayout.CENTER)
        
        setupValidation()
        setupTooltips()
        setupResetButton()
    }
    
    private fun createSectionHeader(text: String): JLabel {
        return JLabel(text).apply {
            font = font.deriveFont(java.awt.Font.BOLD, font.size + 2f)
            border = BorderFactory.createEmptyBorder(10, 0, 5, 0)
        }
    }
    
    private fun createButtonPanel(): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(resetButton)
        }
    }
    
    private fun setupValidation() {
        httpTimeoutField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = validateTimeout()
            override fun removeUpdate(e: DocumentEvent?) = validateTimeout()
            override fun changedUpdate(e: DocumentEvent?) = validateTimeout()
        })
    }
    
    private fun validateTimeout() {
        val text = httpTimeoutField.text
        if (text.isNotBlank() && !ValidationUtils.validateInteger(text, 1000, 600_000)) {
            addValidationError(httpTimeoutField, "超时必须在 1000 到 600000 毫秒之间")
        } else {
            clearValidationError(httpTimeoutField)
        }
    }
    
    private fun setupTooltips() {
        charsetField.toolTipText = "输出文件的字符编码（如 UTF-8、ISO-8859-1）"
        unsafeSslCheckbox.toolTipText = "允许连接到自签名证书的服务器"
        httpTimeoutField.toolTipText = "HTTP请求超时时间，单位毫秒（1000-600000）"
    }
    
    private fun setupResetButton() {
        resetButton.addActionListener {
            resetToDefaults()
        }
    }
    
    private fun resetToDefaults() {
        charsetField.text = "UTF-8"
        unsafeSslCheckbox.isSelected = false
        httpTimeoutField.text = "30000"
    }
    
    override fun resetFrom(settings: Settings?) {
        charsetField.text = settings?.outputCharset ?: "UTF-8"
        unsafeSslCheckbox.isSelected = settings?.unsafeSsl ?: false
        httpTimeoutField.text = (settings?.httpTimeOut ?: 30_000).toString()
    }
    
    override fun applyTo(settings: Settings) {
        if (hasValidationErrors()) {
            throw IllegalArgumentException("Validation errors:\n${getValidationErrors()}")
        }
        
        settings.outputCharset = charsetField.text.ifBlank { "UTF-8" }
        settings.unsafeSsl = unsafeSslCheckbox.isSelected
        settings.httpTimeOut = httpTimeoutField.text.toIntOrNull() ?: 30_000
    }
    
    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        return charsetField.text != s.outputCharset ||
            unsafeSslCheckbox.isSelected != s.unsafeSsl ||
            httpTimeoutField.text != s.httpTimeOut.toString()
    }
}
