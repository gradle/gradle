package org.gradle.internal.declarativedsl.analysis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.gradle.declarative.dsl.schema.AnalysisSchema
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
    val internalSemantics: BuilderFunctionSemantics by lazy { BuilderFunctionSemantics(receiver) }
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
    private val semantics: PureFunctionSemantics,
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
    val internalSemantics: FunctionSemantics by lazy { PureFunctionSemantics(dataClass) }

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
class AccessAndConfigureFunctionSemantics(
    val accessor: ConfigureAccessor,
    val returnType: ReturnType
) : FunctionSemantics.ConfigureSemantics {
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

    override fun getConfigureBlockRequirement(): FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement =
        FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement.REQUIRED
}


@Serializable
class AddAndConfigureFunctionSemantics(
    private val objectType: DataTypeRef,
    private val configureBlockRequirement: FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement
) : FunctionSemantics.NewObjectFunctionSemantics, FunctionSemantics.ConfigureSemantics {

    override fun getReturnValueType(): DataTypeRef = objectType

    override fun getConfiguredType(): DataTypeRef = objectType

    override fun getConfigureBlockRequirement(): FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement = configureBlockRequirement
}


@Serializable
class PureFunctionSemantics(private val returnValueType: DataTypeRef) : FunctionSemantics.NewObjectFunctionSemantics {
    override fun getReturnValueType(): DataTypeRef = returnValueType
}


@Serializable
class BuilderFunctionSemantics(private val objectType: DataTypeRef) : FunctionSemantics {

    override fun getReturnValueType(): DataTypeRef = objectType
}


@Serializable
sealed interface ConfigureAccessor {
    val objectType: DataTypeRef

    fun access(objectOrigin: ObjectOrigin, inFunction: ObjectOrigin.AccessAndConfigureReceiver): ObjectOrigin

    @Serializable
    data class Property(val dataProperty: DataProperty) : ConfigureAccessor {
        override val objectType: DataTypeRef
            get() = dataProperty.type

        override fun access(objectOrigin: ObjectOrigin, inFunction: ObjectOrigin.AccessAndConfigureReceiver): ObjectOrigin =
            ObjectOrigin.PropertyReference(objectOrigin, dataProperty, objectOrigin.originElement)
    }

    @Serializable
    data class Custom(override val objectType: DataTypeRef, val customAccessorIdentifier: String) : ConfigureAccessor {
        override fun access(objectOrigin: ObjectOrigin, inFunction: ObjectOrigin.AccessAndConfigureReceiver): ObjectOrigin =
            ObjectOrigin.CustomConfigureAccessor(objectOrigin, this, objectOrigin.originElement)
    }

    @Serializable
    data class ConfiguringLambdaArgument(override val objectType: DataTypeRef) : ConfigureAccessor {
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
data class DataTypeRefTypeImpl(private val dataType: DataType) : DataTypeRef.Type {
    override fun getDataType(): DataType = dataType
}


@Serializable
data class DataTypeRefNameImpl(private val fqName: FqName) : DataTypeRef.Name {
    override fun getFqName(): FqName = fqName
}


val DataType.ref: DataTypeRef
    get() = DataTypeRefTypeImpl(this)
