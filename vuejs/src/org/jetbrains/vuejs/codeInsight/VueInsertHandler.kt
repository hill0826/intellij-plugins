package org.jetbrains.vuejs.codeInsight

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.XmlTagInsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.javascript.nodejs.NodeModuleSearchUtil
import com.intellij.lang.ecmascript6.psi.JSExportAssignment
import com.intellij.lang.ecmascript6.psi.impl.ES6ImportPsiUtil
import com.intellij.lang.ecmascript6.psi.impl.ES6ImportPsiUtil.ImportType
import com.intellij.lang.ecmascript6.resolve.ES6PsiUtil
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.lang.javascript.psi.impl.JSChangeUtil
import com.intellij.lang.javascript.psi.stubs.JSImplicitElement
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.html.HtmlFileImpl

class VueInsertHandler : XmlTagInsertHandler() {
  companion object {
    val INSTANCE = VueInsertHandler()
  }

  override fun handleInsert(context: InsertionContext?, item: LookupElement?) {
    super.handleInsert(context, item)
    if (context == null || item  == null) return
    val importedFile = (item.`object` as JSImplicitElement).containingFile
    if (importedFile == context.file) return
    val nodeModule = NodeModuleSearchUtil.findDependencyRoot((item.`object` as PsiElement).containingFile.virtualFile)
    if (isSkippedModule(nodeModule)) return

    context.commitDocument()
    val file = context.file as? HtmlFileImpl ?: return
    val content = findScriptContent(file) ?: return

    val defaultExport = ES6PsiUtil.findDefaultExport(content) as? JSExportAssignment ?: return
    val obj = defaultExport.expression as? JSObjectLiteralExpression ?: return
    val name = toAsset(item.lookupString)
    val components = componentProperty(obj).value as? JSObjectLiteralExpression ?: return
    val capitalizedName = name.capitalize()
    if (components.findProperty(name) != null || components.findProperty(capitalizedName) != null) return
    val newProperty = (JSChangeUtil.createExpressionWithContext("{ $capitalizedName }", obj)!!.psi as JSObjectLiteralExpression).firstProperty
    addProperty(newProperty, components)
    ES6ImportPsiUtil.insertImport(content, capitalizedName, ImportType.DEFAULT, importedFile, context.editor)
  }

  private fun isSkippedModule(nodeModule: VirtualFile?) = "vue" == nodeModule?.name || "vue-router" == nodeModule?.name

  private fun componentProperty(obj: JSObjectLiteralExpression): JSProperty {
    val property = obj.findProperty("components")
    if (property != null) return property
    val newProperty = (JSChangeUtil.createExpressionWithContext("{ components: {} }", obj)!!.psi as JSObjectLiteralExpression).firstProperty
    val addedProperty: PsiElement = addProperty(newProperty, obj)
    return addedProperty as JSProperty
  }

  private fun addProperty(newProperty: JSProperty, obj: JSObjectLiteralExpression): PsiElement {
    val addedProperty: PsiElement
    if (obj.firstProperty != null) {
      addedProperty = obj.addBefore(newProperty, obj.firstProperty)
    } else {
      addedProperty = obj.addAfter(newProperty, obj.firstChild)
    }
    return addedProperty
  }
}