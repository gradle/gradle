package org.gradle.client.demo.mutations

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.EnumClass
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.internal.declarativedsl.dom.mutation.TypedMember

internal fun AnalysisSchema.typeByFqn(name: String): DataClass {
    val type = dataClassTypesByFqName.entries.single { it.key.qualifiedName == name }.value
    return when (type) {
        is DataClass -> type
        is EnumClass -> error("$name is an enum class, expected a data class")
    }
}

fun DataClass.singleConfiguringFunctionNamed(name: String): TypedMember.TypedFunction =
    TypedMember.TypedFunction(
        this, 
        memberFunctions.single { it.simpleName == name && it.semantics is FunctionSemantics.AccessAndConfigure }
    )