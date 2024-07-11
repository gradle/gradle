package org.gradle.client.demo.mutations

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.internal.declarativedsl.dom.mutation.TypedMember

internal fun AnalysisSchema.typeByFqn(name: String): DataClass =
    dataClassesByFqName.entries.single { it.key.qualifiedName == name }.value

fun DataClass.singleConfiguringFunctionNamed(name: String): TypedMember.TypedFunction =
    TypedMember.TypedFunction(
        this, 
        memberFunctions.single { it.simpleName == name && it.semantics is FunctionSemantics.AccessAndConfigure }
    )