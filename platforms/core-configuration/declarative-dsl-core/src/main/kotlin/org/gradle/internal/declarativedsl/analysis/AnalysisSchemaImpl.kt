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
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.ExternalObjectProviderKey
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.declarative.dsl.schema.ParameterSemantics
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.language.UnitDataType


@Serializable
data class AnalysisSchemaImpl(
    private val topLevelReceiverType: DataClass,
    private val dataClassesByFqName: Map<FqName, DataClass>,
    private val externalFunctionsByFqName: Map<FqName, DataTopLevelFunction>,
    private val externalObjectsByFqName: Map<FqName, ExternalObjectProviderKey>,
    private val defaultImports: Set<FqName>
) : AnalysisSchema {
    override fun getTopLevelReceiverType(): DataClass = topLevelReceiverType

    override fun getDataClassesByFqName(): Map<FqName, DataClass> = dataClassesByFqName

    override fun getExternalFunctionsByFqName(): Map<FqName, DataTopLevelFunction> = externalFunctionsByFqName

    override fun getExternalObjectsByFqName(): Map<FqName, ExternalObjectProviderKey> = externalObjectsByFqName

    override fun getDefaultImports(): Set<FqName> = defaultImports
}


@Serializable
@SerialName("data")
data class DataClassImpl(
    private val name: FqName,
    private val supertypes: Set<FqName>,
    private val properties: List<DataProperty>,
    private val memberFunctions: List<SchemaMemberFunction>,
    private val constructors: List<DataConstructor>
) : DataClass, DataType {

    override fun isClass(): Boolean = true

    override fun getDataClass(): DataClass = this

    override fun getName(): FqName = name

    override fun getSupertypes(): Set<FqName> = supertypes

    override fun getProperties(): List<DataProperty> = properties

    override fun getMemberFunctions(): List<SchemaMemberFunction> = memberFunctions

    override fun getConstructors(): List<DataConstructor> = constructors

    override fun toString(): String = name.simpleName
}


@Serializable
data class DataPropertyImpl(
    private val name: String,
    @SerialName("privateType") // TODO: is this ok?
    private val type: DataTypeRef,
    private val mode: DataProperty.PropertyMode,
    private val hasDefaultValue: Boolean,
    private val isHiddenInDsl: Boolean = false,
    private val isDirectAccessOnly: Boolean = false
) : DataProperty {
    override fun getName(): String = name

    override fun getType(): DataTypeRef = type

    override fun getMode(): DataProperty.PropertyMode = mode

    override fun hasDefaultValue(): Boolean = hasDefaultValue

    override fun isHiddenInDsl(): Boolean = isHiddenInDsl

    override fun isDirectAccessOnly(): Boolean = isDirectAccessOnly

    override fun isReadOnly(): Boolean = mode == DataProperty.PropertyMode.READ_ONLY

    override fun isWriteOnly(): Boolean = mode == DataProperty.PropertyMode.WRITE_ONLY
}


@Serializable
data class DataBuilderFunction(
    private val receiver: DataTypeRef,
    private val simpleName: String,
    private val isDirectAccessOnly: Boolean,
    val dataParameter: DataParameter,
) : SchemaMemberFunction {
    private
    val internalSemantics: FunctionSemanticsImpl by lazy { FunctionSemanticsImpl.Builder(receiver) }
    private
    val internalParameters: List<DataParameter> by lazy { listOf(dataParameter) }

    override fun getSimpleName(): String = simpleName

    override fun getParameters(): List<DataParameter> = internalParameters

    override fun getSemantics(): FunctionSemantics = internalSemantics

    override fun getReturnValueType(): DataTypeRef = internalSemantics.returnValueType

    override fun getReceiver(): DataTypeRef = receiver

    override fun isDirectAccessOnly(): Boolean = isDirectAccessOnly
}


@Serializable
data class DataTopLevelFunctionImpl(
    private val packageName: String,
    private val simpleName: String,
    private val parameters: List<DataParameter>,
    private val semantics: FunctionSemanticsImpl.Pure,
) : DataTopLevelFunction {
    override fun getPackageName(): String = packageName

    override fun getSimpleName(): String = simpleName

    override fun getParameters(): List<DataParameter> = parameters

    override fun getSemantics(): FunctionSemantics = semantics

    override fun getReturnValueType(): DataTypeRef = semantics.returnValueType
}


@Serializable
data class DataMemberFunction(
    private val receiver: DataTypeRef,
    private val simpleName: String,
    private val parameters: List<DataParameter>,
    private val isDirectAccessOnly: Boolean,
    private val semantics: FunctionSemantics,
) : SchemaMemberFunction {
    override fun getSimpleName(): String = simpleName

    override fun getParameters(): List<DataParameter> = parameters

    override fun getSemantics(): FunctionSemantics = semantics

    override fun getReturnValueType(): DataTypeRef = semantics.returnValueType

    override fun getReceiver(): DataTypeRef = receiver

    override fun isDirectAccessOnly(): Boolean = isDirectAccessOnly
}


@Serializable
data class DataConstructorImpl(
    private val parameters: List<DataParameter>,
    private val dataClass: DataTypeRef
) : DataConstructor {
    private
    val internalSemantics: FunctionSemanticsImpl by lazy { FunctionSemanticsImpl.Pure(dataClass) }

    override fun getSimpleName(): String = "<init>"

    override fun getParameters(): List<DataParameter> = parameters

    override fun getSemantics(): FunctionSemantics = internalSemantics

    override fun getReturnValueType(): DataTypeRef = internalSemantics.returnValueType

    override fun getDataClass(): DataTypeRef = dataClass
}


@Serializable
data class DataParameterImpl(
    private val name: String?,
    @SerialName("privateType") // TODO: is this ok?
    private val type: DataTypeRef,
    private val isDefault: Boolean,
    private val semantics: ParameterSemantics
) : DataParameter {
    override fun getName(): String? = name

    override fun getType(): DataTypeRef = type

    override fun isDefault(): Boolean = isDefault

    override fun getSemantics(): ParameterSemantics = semantics
}


@Serializable
data class StoreValueInPropertyParameterSemantics(private val dataProperty: DataProperty) : ParameterSemantics.StoreValueInProperty {
    override fun getDataProperty(): DataProperty = dataProperty
}


@Serializable
data object UnknownParameterSemantics : ParameterSemantics.Unknown


@Serializable
sealed interface FunctionSemanticsImpl : FunctionSemantics {

    @Serializable
    sealed interface ConfigureSemantics : FunctionSemanticsImpl {
        override fun isConfigureSemantics(): Boolean = true
    }

    @Serializable
    sealed interface NewObjectFunctionSemantics : FunctionSemanticsImpl {
        override fun isNewObjectFunctionSemantics(): Boolean = true
    }

    @Serializable
    class Builder(private val objectType: DataTypeRef) : FunctionSemanticsImpl {
        override fun getReturnValueType(): DataTypeRef = objectType
    }

    @Serializable
    class AccessAndConfigure(
        val accessor: ConfigureAccessorImpl,
        val returnType: ReturnType
    ) : ConfigureSemantics {
        enum class ReturnType {
            UNIT, CONFIGURED_OBJECT
        }

        override fun getReturnValueType(): DataTypeRef =
            when (returnType) {
                ReturnType.UNIT -> UnitDataType.ref
                ReturnType.CONFIGURED_OBJECT -> accessor.objectType
            }

        override fun getConfiguredType(): DataTypeRef =
            if (returnType == ReturnType.CONFIGURED_OBJECT) returnValueType else accessor.objectType

        override fun getConfigureBlockRequirement(): FunctionSemantics.ConfigureBlockRequirement =
            FunctionSemantics.ConfigureBlockRequirement.REQUIRED
    }

    @Serializable
    class AddAndConfigure(
        private val objectType: DataTypeRef,
        private val configureBlockRequirement: FunctionSemantics.ConfigureBlockRequirement
    ) : NewObjectFunctionSemantics, ConfigureSemantics {
        override fun getReturnValueType(): DataTypeRef = objectType

        override fun getConfiguredType(): DataTypeRef = objectType

        override fun getConfigureBlockRequirement(): FunctionSemantics.ConfigureBlockRequirement = configureBlockRequirement
    }

    @Serializable
    class Pure(private val returnValueType: DataTypeRef) : NewObjectFunctionSemantics {
        override fun getReturnValueType(): DataTypeRef = returnValueType
    }
}


@Serializable
sealed interface ConfigureAccessorImpl : ConfigureAccessor {

    fun access(objectOrigin: ObjectOrigin, inFunction: ObjectOrigin.AccessAndConfigureReceiver): ObjectOrigin

    @Serializable
    data class Property(
        private val dataProperty: DataProperty
    ) : ConfigureAccessorImpl {

        override fun getObjectType(): DataTypeRef = dataProperty.type

        override fun access(objectOrigin: ObjectOrigin, inFunction: ObjectOrigin.AccessAndConfigureReceiver): ObjectOrigin =
            ObjectOrigin.PropertyReference(objectOrigin, dataProperty, objectOrigin.originElement)
    }

    @Serializable
    data class Custom(
        private val objectType: DataTypeRef,
        val customAccessorIdentifier: String
    ) : ConfigureAccessorImpl {

        override fun getObjectType(): DataTypeRef = objectType

        override fun access(objectOrigin: ObjectOrigin, inFunction: ObjectOrigin.AccessAndConfigureReceiver): ObjectOrigin =
            ObjectOrigin.CustomConfigureAccessor(objectOrigin, this, objectOrigin.originElement)
    }

    @Serializable
    data class ConfiguringLambdaArgument(
        private val objectType: DataTypeRef
    ) : ConfigureAccessorImpl {

        override fun getObjectType(): DataTypeRef = objectType

        override fun access(objectOrigin: ObjectOrigin, inFunction: ObjectOrigin.AccessAndConfigureReceiver): ObjectOrigin =
            ObjectOrigin.ConfiguringLambdaReceiver(inFunction.function, inFunction.parameterBindings, inFunction.invocationId, objectType, inFunction.originElement, inFunction.receiver)
    }

    // TODO: configure all elements by addition key?
    // TODO: Do we want to support configuring external objects?
}


@Serializable
data class FqNameImpl(private val packageName: String, private val simpleName: String) : FqName {
    companion object {
        fun parse(fqNameString: String): FqName {
            val parts = fqNameString.split(".")
            return FqNameImpl(parts.dropLast(1).joinToString("."), parts.last())
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
    get() = FqNameImpl(packageName, simpleName)


@Serializable
data class ExternalObjectProviderKeyImpl(
    private val type: DataTypeRef
) : ExternalObjectProviderKey {
    override fun getType(): DataTypeRef = type
}


@Serializable
data class DataTypeRefTypeImpl(private val dataType: DataType) : DataTypeRef {
    override fun isNamed(): Boolean = false

    override fun getDataType(): DataType = dataType

    override fun getFqName(): FqName {
        throw UnsupportedOperationException("Not a reference to a named data type")
    }
}


@Serializable
data class DataTypeRefNameImpl(private val fqName: FqName) : DataTypeRef {
    override fun isNamed(): Boolean = true

    override fun getFqName(): FqName = fqName

    override fun getDataType(): DataType {
        throw UnsupportedOperationException("Data type only available as a name")
    }
}


val DataType.ref: DataTypeRef
    get() = DataTypeRefTypeImpl(this)
