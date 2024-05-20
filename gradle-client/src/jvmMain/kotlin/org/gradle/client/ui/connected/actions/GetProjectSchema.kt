package org.gradle.client.ui.connected.actions

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import org.gradle.declarative.dsl.schema.*
import org.gradle.declarative.dsl.tooling.models.DeclarativeSchemaModel

class GetProjectSchema : GetModelAction<DeclarativeSchemaModel> {

    override val modelType = DeclarativeSchemaModel::class

    @Composable
    override fun ColumnScope.ModelContent(model: DeclarativeSchemaModel) {
        Text(
            text = "Gradle Project Schema",
            style = MaterialTheme.typography.titleMedium
        )
        SelectionContainer {
            Text(
                text = "Available Software Types:\n${model.projectSchema.describeSoftwareTypes()}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }

    private fun AnalysisSchema.describeSoftwareTypes(): String {
        return buildString {
            softwareTypes.forEach { softwareType ->
                appendLine()
                appendDescription(this@describeSoftwareTypes, softwareType)
                appendLine()
            }
        }
    }

    private val indentChars = "  "

    @Suppress("NestedBlockDepth")
    private fun StringBuilder.appendDescription(
        schema: AnalysisSchema,
        function: SchemaMemberFunction,
        indentLevel: Int = 0
    ) {
        when (val functionSemantics = function.semantics) {

            // Configuring blocks
            is FunctionSemantics.AccessAndConfigure -> {
                append(indentChars.repeat(indentLevel))
                append(function.simpleName)
                append(" {")
                appendLine()
                when (val blockType = functionSemantics.configuredType) {
                    is DataTypeRef.Name -> {
                        val blockDataClass = schema.dataClassFor(blockType)
                        blockDataClass.properties.forEach { property ->
                            val propTypeName = when (val propType = property.valueType) {
                                is DataTypeRef.Type -> propType.dataType.toString()
                                is DataTypeRef.Name -> propType.toHumanReadable()
                            }
                            append(indentChars.repeat(indentLevel + 1))
                            append("${property.name}: $propTypeName")
                            appendLine()
                        }
                        blockDataClass.memberFunctions.forEach { subBlock ->
                            appendDescription(schema, subBlock, indentLevel + 1)
                            appendLine()
                        }
                    }

                    is DataTypeRef.Type -> TODO("Block '${function.simpleName}' type is not a type ref")
                }
                append(indentChars.repeat(indentLevel))
                append("}")
            }

            // Factory function
            is FunctionSemantics.Pure -> {
                append(indentChars.repeat(indentLevel + 1))
                append(function.simpleName)
                append("(")
                append(function.parameters.joinToString { dp -> dp.toHumanReadable() })
                append("): ")
                append(function.returnValueType.toHumanReadable())
            }

            // Add and configure function
            is FunctionSemantics.AddAndConfigure -> {
                append(indentChars.repeat(indentLevel + 1))
                append(function.simpleName)
                append("(")
                append(function.parameters.joinToString { dp -> dp.toHumanReadable() })
                append(")")
            }

            is FunctionSemantics.Builder -> {
                append(indentChars.repeat(indentLevel + 1))
                append("TODO Block '${function.simpleName}' is a Builder")
            }
        }
    }
}
