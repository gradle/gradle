package org.gradle.internal.declarativedsl.analysis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.gradle.internal.declarativedsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.schema.ConfigureAccessor
import org.gradle.internal.declarativedsl.schema.DataBuilderFunction
import org.gradle.internal.declarativedsl.schema.DataClass
import org.gradle.internal.declarativedsl.schema.DataConstructor
import org.gradle.internal.declarativedsl.schema.DataMemberFunction
import org.gradle.internal.declarativedsl.schema.DataParameter
import org.gradle.internal.declarativedsl.schema.DataProperty
import org.gradle.internal.declarativedsl.schema.DataTopLevelFunction
import org.gradle.internal.declarativedsl.schema.DataType
import org.gradle.internal.declarativedsl.schema.DataTypeRef
import org.gradle.internal.declarativedsl.schema.ExternalObjectProviderKey
import org.gradle.internal.declarativedsl.schema.FqName
import org.gradle.internal.declarativedsl.schema.FunctionSemantics
import org.gradle.internal.declarativedsl.schema.FunctionSemantics.AccessAndConfigure.ReturnType
import org.gradle.internal.declarativedsl.schema.ParameterSemantics
import org.gradle.internal.declarativedsl.schema.SchemaMemberFunction


@Serializable
data class AnalysisSchemaImpl(
    override val topLevelReceiverType: DataClass,
    override val dataClassesByFqName: Map<FqName, DataClass>,
    override val externalFunctionsByFqName: Map<FqName, DataTopLevelFunction>,
    override val externalObjectsByFqName: Map<FqName, ExternalObjectProviderKey>,
    override val defaultImports: Set<FqName>
) : AnalysisSchema


@Serializable
@SerialName("data")
data class DataClassImpl(
    override val name: FqName,
    override val supertypes: Set<FqName>,
    override val properties: List<DataProperty>,
    override val memberFunctions: List<SchemaMemberFunction>,
    override val constructors: List<DataConstructor>
) : DataClass {
    override fun toString(): String = name.simpleName
}


@Serializable
data class DataPropertyImpl(
    override val name: String,
    override val type: DataTypeRef,
    override val mode: DataProperty.PropertyMode,
    override val hasDefaultValue: Boolean,
    override val isHiddenInDsl: Boolean = false,
    override val isDirectAccessOnly: Boolean = false
) : DataProperty


val DataProperty.isReadOnly: Boolean
    get() = mode == DataProperty.PropertyMode.ReadOnly


val DataProperty.isWriteOnly: Boolean
    get() = mode == DataProperty.PropertyMode.WriteOnly


@Serializable
data class DataBuilderFunction(
    override val receiver: DataTypeRef,
    override val simpleName: String,
    override val isDirectAccessOnly: Boolean,
    override val dataParameter: DataParameter,
) : DataBuilderFunction {
    override val semantics: FunctionSemantics.Builder = FunctionSemanticsImpl.BuilderImpl(receiver)
    override val parameters: List<DataParameter>
        get() = listOf(dataParameter)
}


@Serializable
data class DataTopLevelFunctionImpl(
    override val packageName: String,
    override val simpleName: String,
    override val parameters: List<DataParameter>,
    override val semantics: FunctionSemantics.Pure,
) : DataTopLevelFunction


@Serializable
data class DataMemberFunctionImpl(
    override val receiver: DataTypeRef,
    override val simpleName: String,
    override val parameters: List<DataParameter>,
    override val isDirectAccessOnly: Boolean,
    override val semantics: FunctionSemantics,
) : DataMemberFunction


@Serializable
data class DataConstructorImpl(
    override val parameters: List<DataParameter>,
    override val dataClass: DataTypeRef
) : DataConstructor {
    override val simpleName
        get() = "<init>"
    override val semantics: FunctionSemantics.Pure = FunctionSemanticsImpl.PureImpl(dataClass)
}


@Serializable
data class DataParameterImpl(
    override val name: String?,
    override val type: DataTypeRef,
    override val isDefault: Boolean,
    override val semantics: ParameterSemantics
) : DataParameter


@Serializable
object ParameterSemanticsImpl {
    @Serializable
    data class StoreValueInPropertyImpl(override val dataProperty: DataProperty) : ParameterSemantics.StoreValueInProperty
}


object FunctionSemanticsImpl {
    @Serializable
    class BuilderImpl(private val objectType: DataTypeRef) : FunctionSemantics.Builder {
        override val returnValueType: DataTypeRef
            get() = objectType
    }

    @Serializable
    class AccessAndConfigureImpl(
        override val accessor: ConfigureAccessor,
        override val returnType: ReturnType
    ) : FunctionSemantics.AccessAndConfigure {
        override val returnValueType: DataTypeRef
            get() = when (returnType) {
                ReturnType.ConfiguredObject -> accessor.objectType
                ReturnType.Unit -> DataType.UnitType.ref
            }

        override val configureBlockRequirement: FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement
            get() = FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement.Required
    }

    @Serializable
    class AddAndConfigureImpl(
        private val objectType: DataTypeRef,
        override val configureBlockRequirement: FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement
    ) : FunctionSemantics.AddAndConfigure {
        override val returnValueType: DataTypeRef
            get() = objectType

        override val configuredType: DataTypeRef
            get() = returnValueType
    }

    @Serializable
    class PureImpl(override val returnValueType: DataTypeRef) : FunctionSemantics.Pure
}


fun ConfigureAccessor.access(objectOrigin: ObjectOrigin, inFunction: ObjectOrigin.AccessAndConfigureReceiver): ObjectOrigin = when (this) {
    is ConfigureAccessor.Property ->
        ObjectOrigin.PropertyReference(objectOrigin, dataProperty, objectOrigin.originElement)
    is ConfigureAccessor.Custom ->
        ObjectOrigin.CustomConfigureAccessor(objectOrigin, this, objectOrigin.originElement)
    is ConfigureAccessor.ConfiguringLambdaArgument ->
        ObjectOrigin.ConfiguringLambdaReceiver(inFunction.function, inFunction.parameterBindings, inFunction.invocationId, objectType, inFunction.originElement, inFunction.receiver)
}


object ConfigureAccessorImpl {
    @Serializable
    data class PropertyImpl(override val dataProperty: DataProperty) : ConfigureAccessor.Property

    @Serializable
    data class CustomImpl(override val objectType: DataTypeRef, override val customAccessorIdentifier: String) : ConfigureAccessor.Custom

    @Serializable
    data class ConfiguringLambdaArgumentImpl(override val objectType: DataTypeRef) : ConfigureAccessor.ConfiguringLambdaArgument

    // TODO: configure all elements by addition key?
    // TODO: Do we want to support configuring external objects?
}


@Serializable
data class FqNameImpl(override val packageName: String, override val simpleName: String) : FqName {
    companion object {
        fun parse(fqNameString: String): FqName {
            val parts = fqNameString.split(".")
            return FqNameImpl(parts.dropLast(1).joinToString("."), parts.last())
        }
    }

    override fun toString(): String = qualifiedName
}


val FqName.qualifiedName
    get() = "$packageName.$simpleName"


val DataTopLevelFunction.fqName: FqName
    get() = FqNameImpl(packageName, simpleName)


@Serializable
data class ExternalObjectProviderKeyImpl(override val type: DataTypeRef) : ExternalObjectProviderKey


object DataTypeRefImpl {
    @Serializable
    data class TypeImpl(override val dataType: DataType) : DataTypeRef.Type

    @Serializable
    data class NameImpl(override val fqName: FqName) : DataTypeRef.Name
}


val DataType.ref: DataTypeRef
    get() = DataTypeRefImpl.TypeImpl(this)
