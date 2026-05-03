package com.apimocktle.ide.action

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.apimocktle.psi.JsonOption
import com.apimocktle.psi.PsiClassHelper
import com.apimocktle.psi.model.ObjectModelJsonConverter

/**
 * Action to convert class fields to JSON5 format.
 *
 * Builds an object model from the class fields and formats it as JSON5,
 * which supports comments, trailing commas, and unquoted keys.
 *
 * @see FieldFormatAction for the base class
 * @see ObjectModelJsonConverter for JSON5 conversion
 */
class FieldsToJson5Action : FieldFormatAction("字段转JSON5") {
    override suspend fun format(project: Project, psiClass: PsiClass): String {
        val helper = PsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass, option = JsonOption.ALL)
        return model?.let { ObjectModelJsonConverter.toJson5(it) } ?: ""
    }
}
