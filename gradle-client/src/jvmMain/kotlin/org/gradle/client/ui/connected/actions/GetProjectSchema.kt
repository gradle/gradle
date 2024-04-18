package org.gradle.client.ui.connected.actions

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.tooling.models.DeclarativeSchemaModel

class GetProjectSchema : GetModelAction<DeclarativeSchemaModel> {

    override val modelType = DeclarativeSchemaModel::class

    @Composable
    override fun ColumnScope.ModelContent(model: DeclarativeSchemaModel) {
        Text(
            text = "Gradle Project",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Schema: ${model.projectSchema.toHumanReadable()}",
            style = MaterialTheme.typography.labelSmall
        )
    }

    private fun AnalysisSchema.toHumanReadable(): String {
        val memberFunctions = this.topLevelReceiverType.memberFunctions
        return buildString {
            memberFunctions.forEach {
                appendLine()
                append("\t")
                append(it.receiver.toHumanReadable())
                append(".")
                append(it.simpleName)
                append("(")
                append(it.parameters.joinToString {dp -> dp.toHumanReadable()})
                append(")")
                append(" -> ")
                append(it.returnValueType.toHumanReadable())
            }
        }
    }

    private fun DataTypeRef.toHumanReadable(): String =
        if (isNamed) {
            fqName.qualifiedName
        } else {
            when {
                dataType.isNull -> "null"
                dataType.isUnit -> "void"
                dataType.isConstant -> dataType.constantType.simpleName
                else -> dataType.dataClass.name.qualifiedName
            }
        }

    private fun DataParameter.toHumanReadable(): String = type.toHumanReadable()
}