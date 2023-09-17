package com.example

import com.h0tk3y.kotlin.staticObjectNotation.analysis.*
import com.h0tk3y.kotlin.staticObjectNotation.analysis.FunctionSemantics.*
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ParameterSemantics.UsedExternally
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ParameterSemantics.StoreValueInProperty

val cRef = DataTypeRef.Name(FqName.parse("com.example.C"))
val abcRef = DataTypeRef.Name(FqName.parse("com.example.Abc"))
val dRef = DataTypeRef.Name(FqName.parse("com.example.D"))

internal fun demoSchema(): AnalysisSchema {
    val cX = DataProperty("x", int, false)
    val cD = DataProperty("d", dRef, false)
    val dId = DataProperty("id", string, false)

    val cClass = DataType.DataClass(
        C::class,
        properties = listOf(
            cX,
            DataProperty("y", string, true),
            cD
        ),
        memberFunctions = listOf(
            DataMemberFunction(
                cRef, "f",
                listOf(DataParameter("y", string, false, UsedExternally)),
                semantics = Pure(int)
            ),
            DataMemberFunction(
                cRef, "d",
                listOf(DataParameter("newD", dRef, false, StoreValueInProperty(cD))),
                semantics = Builder(cRef)
            )
        ),
        constructorSignatures = listOf(
            DataConstructorSignature(listOf(DataParameter("x", int, false, StoreValueInProperty(cD))))
        )
    )

    val abcClass = DataType.DataClass(
        Abc::class,
        properties = listOf(DataProperty("a", int, false)),
        memberFunctions = listOf(
            DataMemberFunction(abcRef, "b", emptyList(), Pure(int)),
            DataMemberFunction(
                abcRef, "c",
                listOf(DataParameter("x", int, false, StoreValueInProperty(cX))),
                semantics = AddAndConfigure(cRef)
            )
        ),
        constructorSignatures = emptyList()
    )

    val dClass = DataType.DataClass(
        D::class,
        properties = listOf(
            DataProperty("id", string, false)
        ),
        memberFunctions = emptyList(),
        constructorSignatures = emptyList()
    )

    val newDFunction = DataTopLevelFunction(
        "com.example", "newD",
        listOf(
            DataParameter("id", DataType.StringDataType.ref, isDefault = false, StoreValueInProperty(dId))
        ),
        semantics = Pure(dClass.ref)
    )

    return AnalysisSchema(
        topLevelReceiverType = abcClass,
        dataClassesByFqName = listOf(abcClass, cClass, dClass).associateBy { FqName.parse(it.kClass.qualifiedName!!) },
        externalFunctionsByFqName = mapOf(newDFunction.fqName to newDFunction),
        externalObjectsByFqName = emptyMap(),
        defaultImports = setOf(newDFunction.fqName)
    )
}

