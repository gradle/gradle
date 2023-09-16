package com.example

import com.h0tk3y.kotlin.staticObjectNotation.analysis.*
import com.h0tk3y.kotlin.staticObjectNotation.analysis.FunctionSemantics.AddAndConfigure
import com.h0tk3y.kotlin.staticObjectNotation.analysis.FunctionSemantics.Pure
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ParameterSemantics.UsedExternally
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ParameterSemantics.StoreValueInProperty

val int = DataType.IntDataType.ref
val string = DataType.StringDataType.ref

val cRef = DataTypeRef.Name(FqName.parse("com.example.C"))
val abcRef = DataTypeRef.Name(FqName.parse("com.example.Abc"))
val dRef = DataTypeRef.Name(FqName.parse("com.example.D"))

internal fun demoSchema(): AnalysisSchema {
    val c_x = DataProperty("x", int, false)
    val d_id = DataProperty("id", string, false)

    val cClass = DataType.DataClass(
        C::class,
        properties = listOf(
            c_x,
            DataProperty("y", string, true),
            DataProperty("d", dRef, false)
        ),
        memberFunctions = listOf(
            DataMemberFunction(
                cRef, "f",
                listOf(DataParameter("y", string, false, UsedExternally)),
                semantics = Pure(int)
            )
        ),
        constructorSignatures = emptyList()
    )

    val abcClass = DataType.DataClass(
        Abc::class,
        properties = listOf(DataProperty("a", int, false)),
        memberFunctions = listOf(
            DataMemberFunction(abcRef, "b", emptyList(), Pure(int)),
            DataMemberFunction(
                abcRef, "c",
                listOf(DataParameter("x", int, false, StoreValueInProperty(c_x))),
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
            DataParameter("id", DataType.StringDataType.ref, isDefault = false, StoreValueInProperty(d_id))),
        semantics = Pure(dClass.ref)
    )

    val schema = AnalysisSchema(
        topLevelReceiverType = abcClass,
        dataClassesByFqName = listOf(abcClass, cClass).associateBy { FqName.parse(it.kClass.qualifiedName!!) },
        externalFunctionsByFqName = mapOf(newDFunction.fqName to newDFunction),
        externalObjectsByFqName = emptyMap(),
        defaultImports = setOf(newDFunction.fqName)
    )
    return schema
}

