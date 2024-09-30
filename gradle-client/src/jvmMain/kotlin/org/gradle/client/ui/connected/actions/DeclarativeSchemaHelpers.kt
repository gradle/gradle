package org.gradle.client.ui.connected.actions

import org.gradle.declarative.dsl.schema.*
import kotlin.reflect.KClass

fun AnalysisSchema.dataClassFor(typeRef: DataTypeRef.Name): DataClass =
    dataClassTypesByFqName.getValue(typeRef.fqName) as? DataClass 
        ?: error("unexpected type for $typeRef")

val AnalysisSchema.softwareTypes: List<SchemaMemberFunction>
    get() = topLevelReceiverType.memberFunctions

fun AnalysisSchema.softwareTypeNamed(name: String): SchemaMemberFunction? =
    softwareTypes.singleOrNull { it.simpleName == name }

val SchemaMemberFunction.softwareTypeSemantics: FunctionSemantics.AccessAndConfigure
    get() = semantics as FunctionSemantics.AccessAndConfigure

fun AnalysisSchema.configuredTypeOf(semantics: FunctionSemantics.ConfigureSemantics): DataClass =
    dataClassFor(semantics.configuredType as DataTypeRef.Name)

val DataProperty.typeName: String
    get() = when (val propType = valueType) {
        is DataTypeRef.Type -> propType.dataType.toString()
        is DataTypeRef.Name -> propType.toHumanReadable()
    }

fun DataTypeRef.toHumanReadable(): String =
    when (this) {
        is DataTypeRef.Name -> fqName.simpleName
        is DataTypeRef.Type -> toHumanReadable()
    }

fun DataTypeRef.Type.toHumanReadable(): String =
    when (val type = this.dataType) {
        is DataType.NullType -> "null"
        is DataType.UnitType -> Unit::class.simpleName!!
        is DataType.ConstantType<*> -> type.toString()
        is DataType.ClassDataType -> type.name.simpleName
    }

fun DataParameter.toHumanReadable(): String =
    type.toHumanReadable()

val List<SchemaMemberFunction>.accessAndConfigure: List<SchemaMemberFunction>
    get() = filter { it.semantics is FunctionSemantics.AccessAndConfigure }

val SchemaMemberFunction.accessAndConfigureSemantics: FunctionSemantics.AccessAndConfigure
    get() = semantics as FunctionSemantics.AccessAndConfigure

val List<SchemaMemberFunction>.addAndConfigure: List<SchemaMemberFunction>
    get() = filter { it.semantics is FunctionSemantics.AddAndConfigure }

val SchemaMemberFunction.addAndConfigureSemantics: FunctionSemantics.AddAndConfigure
    get() = semantics as FunctionSemantics.AddAndConfigure
