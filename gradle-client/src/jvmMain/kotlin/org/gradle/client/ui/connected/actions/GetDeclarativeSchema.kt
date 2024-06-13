package org.gradle.client.ui.connected.actions

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import org.gradle.client.ui.build.BuildTextField
import org.gradle.client.ui.composables.CodeBlock
import org.gradle.client.ui.composables.TitleLarge
import org.gradle.client.ui.theme.spacing
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.declarative.dsl.tooling.models.DeclarativeSchemaModel

class GetDeclarativeSchema : GetModelAction<DeclarativeSchemaModel> {

    override val modelType = DeclarativeSchemaModel::class

    override val displayName: String
        get() = "Declarative Schema"

    @Composable
    override fun ColumnScope.ModelContent(model: DeclarativeSchemaModel) {

        val availableSoftwareTypes = model.projectSchema.softwareTypes.map { it.simpleName }
        val selectedSoftwareType = remember { mutableStateOf(availableSoftwareTypes.first()) }

        TitleLarge(displayName)
        SoftwareTypeDropDown(availableSoftwareTypes, selectedSoftwareType)
        Spacer(Modifier.size(MaterialTheme.spacing.level4))
        SoftwareTypeSchema(model, selectedSoftwareType.value)
        Spacer(Modifier.size(MaterialTheme.spacing.level6))
    }

    @Composable
    private fun SoftwareTypeSchema(model: DeclarativeSchemaModel, softwareType: String) {
        val softwareTypeSchema = model.projectSchema.softwareTypes.single { it.simpleName == softwareType }
        val description = buildString {
            appendDescription(model.projectSchema, softwareTypeSchema)
        }
        CodeBlock(Modifier.fillMaxWidth(), buildAnnotatedString { append(description) })
    }

    private val indentChars = "    "

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
                append(indentChars.repeat(indentLevel))
                append(function.simpleName)
                append("(")
                append(function.parameters.joinToString { dp -> dp.toHumanReadable() })
                append("): ")
                append(function.returnValueType.toHumanReadable())
            }

            // Add and configure function
            is FunctionSemantics.AddAndConfigure -> {
                append(indentChars.repeat(indentLevel))
                append(function.simpleName)
                append("(")
                append(function.parameters.joinToString { dp -> dp.toHumanReadable() })
                append(")")
            }

            is FunctionSemantics.Builder -> {
                append(indentChars.repeat(indentLevel))
                append("TODO Block '${function.simpleName}' is a Builder")
            }
        }
    }

    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    private fun SoftwareTypeDropDown(
        availableSoftwareTypes: List<String>,
        state: MutableState<String>
    ) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            BuildTextField(
                modifier = Modifier.menuAnchor(),
                value = state.value,
                onValueChange = { state.value = it },
                readOnly = true,
                label = { Text("Software types") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availableSoftwareTypes.forEach { softwareType ->
                    DropdownMenuItem(
                        text = { Text(softwareType) },
                        onClick = {
                            state.value = softwareType
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}
