package org.gradle.internal.declarativedsl.analysis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.ConfigureAccessor
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataConstructor
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.DataTopLevelFunction
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.ExternalObjectProviderKey
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.declarative.dsl.schema.ParameterSemantics
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.language.DataTypeInternal


@Serializable
data class DefaultAnalysisSchema(
    private val topLevelReceiverType: DefaultDataClass,
    private val dataClassesByFqName: Map<FqName, DefaultDataClass>,
    private val externalFunctionsByFqName: Map<FqName, DataTopLevelFunction>,
    private val externalObjectsByFqName: Map<FqName, ExternalObjectProviderKey>,
    private val defaultImports: Set<FqName>
) : AnalysisSchema {
    override fun getTopLevelReceiverType(): DefaultDataClass = topLevelReceiverType

    override fun getDataClassesByFqName(): Map<FqName, DefaultDataClass> = dataClassesByFqName

    override fun getExternalFunctionsByFqName(): Map<FqName, DataTopLevelFunction> = externalFunctionsByFqName

    override fun getExternalObjectsByFqName(): Map<FqName, ExternalObjectProviderKey> = externalObjectsByFqName

    override fun getDefaultImports(): Set<FqName> = defaultImports
}


@Serializable
@SerialName("data")
data class DefaultDataClass(
    private val name: FqName,
    private val supertypes: Set<FqName>,
    private val properties: List<DataProperty>,
    private val memberFunctions: List<SchemaMemberFunction>,
    private val constructors: List<DataConstructor>
) : DataClass, DataTypeInternal {

    override fun isClass(): Boolean = true

    override fun getDataClass(): DefaultDataClass = this

    override fun getName(): FqName = name

    override fun getSupertypes(): Set<FqName> = supertypes

    override fun getProperties(): List<DataProperty> = properties

    override fun getMemberFunctions(): List<SchemaMemberFunction> = memberFunctions

    override fun getConstructors(): List<DataConstructor> = constructors

    override fun toString(): String = name.simpleName
}


@Serializable
data class DefaultDataProperty(
    private val name: String,
    @SerialName("privateType") // TODO: is this ok?
    private val type: DataTypeRefInternal,
    private val mode: DataProperty.PropertyMode,
    private val hasDefaultValue: Boolean,
    private val isHiddenInDsl: Boolean = false,
    private val isDirectAccessOnly: Boolean = false
) : DataProperty {
    override fun getName(): String = name

    override fun getType(): DataTypeRefInternal = type

    override fun getMode(): DataProperty.PropertyMode = mode

    override fun hasDefaultValue(): Boolean = hasDefaultValue

    override fun isHiddenInDsl(): Boolean = isHiddenInDsl

    override fun isDirectAccessOnly(): Boolean = isDirectAccessOnly

    override fun isReadOnly(): Boolean = mode == DataProperty.PropertyMode.READ_ONLY

    override fun isWriteOnly(): Boolean = mode == DataProperty.PropertyMode.WRITE_ONLY
}


@Serializable
data class DataBuilderFunction(
    private val receiver: DataTypeRefInternal,
    private val simpleName: String,
    private val isDirectAccessOnly: Boolean,
    val dataParameter: DataParameter,
) : SchemaMemberFunction {
    private
    val internalSemantics: FunctionSemanticsInternal by lazy { FunctionSemanticsInternal.Builder(receiver) }
    private
    val internalParameters: List<DataParameter> by lazy { listOf(dataParameter) }

    override fun getSimpleName(): String = simpleName

    override fun getParameters(): List<DataParameter> = internalParameters

    override fun getSemantics(): FunctionSemanticsInternal = internalSemantics

    override fun getReturnValueType(): DataTypeRefInternal = internalSemantics.returnValueType as DataTypeRefInternal

    override fun getReceiver(): DataTypeRefInternal = receiver

    override fun isDirectAccessOnly(): Boolean = isDirectAccessOnly
}


@Serializable
data class DefaultDataTopLevelFunction(
    private val packageName: String,
    private val simpleName: String,
    private val parameters: List<DataParameter>,
    private val semantics: FunctionSemanticsInternal.Pure,
) : DataTopLevelFunction {
    override fun getPackageName(): String = packageName

    override fun getSimpleName(): String = simpleName

    override fun getParameters(): List<DataParameter> = parameters

    override fun getSemantics(): FunctionSemanticsInternal = semantics

    override fun getReturnValueType(): DataTypeRefInternal = semantics.returnValueType
}


@Serializable
data class DataMemberFunction(
    private val receiver: DataTypeRefInternal,
    private val simpleName: String,
    private val parameters: List<DataParameter>,
    private val isDirectAccessOnly: Boolean,
    private val semantics: FunctionSemanticsInternal,
) : SchemaMemberFunction {
    override fun getSimpleName(): String = simpleName

    override fun getParameters(): List<DataParameter> = parameters

    override fun getSemantics(): FunctionSemanticsInternal = semantics

    override fun getReturnValueType(): DataTypeRefInternal = semantics.returnValueType as DataTypeRefInternal

    override fun getReceiver(): DataTypeRefInternal = receiver

    override fun isDirectAccessOnly(): Boolean = isDirectAccessOnly
}


@Serializable
data class DefaultDataConstructor(
    private val parameters: List<DataParameter>,
    private val dataClass: DataTypeRefInternal
) : DataConstructor {
    private
    val internalSemantics: FunctionSemanticsInternal by lazy { FunctionSemanticsInternal.Pure(dataClass) }

    override fun getSimpleName(): String = "<init>"

    override fun getParameters(): List<DataParameter> = parameters

    override fun getSemantics(): FunctionSemanticsInternal = internalSemantics

    override fun getReturnValueType(): DataTypeRefInternal = internalSemantics.returnValueType as DataTypeRefInternal

    override fun getDataClass(): DataTypeRefInternal = dataClass
}


@Serializable
data class DefaultDataParameter(
    private val name: String?,
    @SerialName("privateType") // TODO: is this ok?
    private val type: DataTypeRefInternal,
    private val isDefault: Boolean,
    private val semantics: ParameterSemanticsInternal
) : DataParameter {
    override fun getName(): String? = name

    override fun getType(): DataTypeRefInternal = type

    override fun isDefault(): Boolean = isDefault

    override fun getSemantics(): ParameterSemanticsInternal = semantics
}


@Serializable
sealed interface ParameterSemanticsInternal : ParameterSemantics {
    @Serializable
    data class StoreValueInProperty(private val dataProperty: DataProperty) : ParameterSemanticsInternal {
        override fun isStoreValueInProperty(): Boolean = true

        override fun getDataProperty(): DataProperty = dataProperty
    }

    @Serializable
    data object Unknown : ParameterSemanticsInternal
}


@Serializable
sealed interface FunctionSemanticsInternal : FunctionSemantics {

    @Serializable
    sealed interface ConfigureSemantics : FunctionSemanticsInternal {
        override fun isConfigureSemantics(): Boolean = true
    }

    @Serializable
    sealed interface NewObjectFunctionSemantics : FunctionSemanticsInternal {
        override fun isNewObjectFunctionSemantics(): Boolean = true
    }

    @Serializable
    class Builder(private val objectType: DataTypeRefInternal) : FunctionSemanticsInternal {
        override fun getReturnValueType(): DataTypeRefInternal = objectType
    }

    @Serializable
    class AccessAndConfigure(
        val accessor: ConfigureAccessorInternal,
        val returnType: ReturnType
    ) : ConfigureSemantics {
        enum class ReturnType {
            UNIT, CONFIGURED_OBJECT
        }

        override fun getReturnValueType(): DataTypeRefInternal =
            when (returnType) {
                ReturnType.UNIT -> DataTypeInternal.UnitType.ref
                ReturnType.CONFIGURED_OBJECT -> accessor.objectType as DataTypeRefInternal
            }

        override fun getConfiguredType(): DataTypeRefInternal =
            if (returnType == ReturnType.CONFIGURED_OBJECT) returnValueType else accessor.objectType as DataTypeRefInternal

        override fun getConfigureBlockRequirement(): FunctionSemantics.ConfigureBlockRequirement =
            FunctionSemantics.ConfigureBlockRequirement.REQUIRED
    }

    @Serializable
    class AddAndConfigure(
        private val objectType: DataTypeRefInternal,
        private val configureBlockRequirement: FunctionSemantics.ConfigureBlockRequirement
    ) : NewObjectFunctionSemantics, ConfigureSemantics {
        override fun getReturnValueType(): DataTypeRefInternal = objectType

        override fun getConfiguredType(): DataTypeRefInternal = objectType

        override fun getConfigureBlockRequirement(): FunctionSemantics.ConfigureBlockRequirement = configureBlockRequirement
    }

    @Serializable
    class Pure(private val returnValueType: DataTypeRefInternal) : NewObjectFunctionSemantics {
        override fun getReturnValueType(): DataTypeRefInternal = returnValueType
    }
}


@Serializable
sealed interface ConfigureAccessorInternal : ConfigureAccessor {

    fun access(objectOrigin: ObjectOrigin, inFunction: ObjectOrigin.AccessAndConfigureReceiver): ObjectOrigin

    @Serializable
    data class Property(
        private val dataProperty: DataProperty
    ) : ConfigureAccessorInternal {

        override fun getObjectType(): DataTypeRefInternal = dataProperty.type as DataTypeRefInternal

        override fun access(objectOrigin: ObjectOrigin, inFunction: ObjectOrigin.AccessAndConfigureReceiver): ObjectOrigin =
            ObjectOrigin.PropertyReference(objectOrigin, dataProperty, objectOrigin.originElement)
    }

    @Serializable
    data class Custom(
        private val objectType: DataTypeRefInternal,
        val customAccessorIdentifier: String
    ) : ConfigureAccessorInternal {

        override fun getObjectType(): DataTypeRefInternal = objectType

        override fun access(objectOrigin: ObjectOrigin, inFunction: ObjectOrigin.AccessAndConfigureReceiver): ObjectOrigin =
            ObjectOrigin.CustomConfigureAccessor(objectOrigin, this, objectOrigin.originElement)
    }

    @Serializable
    data class ConfiguringLambdaArgument(
        private val objectType: DataTypeRefInternal
    ) : ConfigureAccessorInternal {

        override fun getObjectType(): DataTypeRefInternal = objectType

        override fun access(objectOrigin: ObjectOrigin, inFunction: ObjectOrigin.AccessAndConfigureReceiver): ObjectOrigin =
            ObjectOrigin.ConfiguringLambdaReceiver(inFunction.function, inFunction.parameterBindings, inFunction.invocationId, objectType, inFunction.originElement, inFunction.receiver)
    }

    // TODO: configure all elements by addition key?
    // TODO: Do we want to support configuring external objects?
}


@Serializable
data class DefaultFqName(private val packageName: String, private val simpleName: String) : FqName {
    companion object {
        fun parse(fqNameString: String): FqName {
            val parts = fqNameString.split(".")
            return DefaultFqName(parts.dropLast(1).joinToString("."), parts.last())
        }
    }

    @get:JvmName("privateQualifiedName")
    private
    val qualifiedName by lazy { "$packageName.$simpleName" }

    override fun getPackageName(): String = packageName

    override fun getSimpleName(): String = simpleName

    override fun getQualifiedName(): String = qualifiedName

    override fun toString(): String = qualifiedName
}


val DataTopLevelFunction.fqName: FqName
    get() = DefaultFqName(packageName, simpleName)


@Serializable
data class DefaultExternalObjectProviderKey(
    private val type: DataTypeRefInternal
) : ExternalObjectProviderKey {
    override fun getType(): DataTypeRefInternal = type
}


@Serializable
sealed interface DataTypeRefInternal : DataTypeRef {
    @Serializable
    data class Type(private val dataType: DataTypeInternal) : DataTypeRefInternal {
        override fun isNamed(): Boolean = false

        override fun getDataType(): DataTypeInternal = dataType

        override fun getFqName(): FqName {
            throw UnsupportedOperationException("Not a reference to a named data type")
        }
    }

    @Serializable
    data class Name(private val fqName: FqName) : DataTypeRefInternal {
        override fun isNamed(): Boolean = true

        override fun getFqName(): FqName = fqName

        override fun getDataType(): DataTypeInternal {
            throw UnsupportedOperationException("Data type only available as a name")
        }
    }
}


val DataTypeInternal.ref: DataTypeRefInternal
    get() = DataTypeRefInternal.Type(this)
