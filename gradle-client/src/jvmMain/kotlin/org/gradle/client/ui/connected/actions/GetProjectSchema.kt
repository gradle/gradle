package org.gradle.client.ui.connected.actions

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
        when (this) {
            is DataTypeRef.Name -> fqName.qualifiedName

            is DataTypeRef.Type -> when (dataType) {
                is DataType.NullType -> "null"
                is DataType.UnitType -> "void"
                is DataType.ConstantType<*> -> (dataType as DataType.ConstantType<*>).constantType.simpleName
                is DataClass -> (dataType as DataClass).name.qualifiedName
            }
        }

    private fun DataParameter.toHumanReadable(): String = type.toHumanReadable()
}
