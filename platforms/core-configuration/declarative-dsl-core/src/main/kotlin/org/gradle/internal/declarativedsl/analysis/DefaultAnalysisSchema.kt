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
import org.gradle.declarative.dsl.schema.FunctionSemantics.AccessAndConfigure
import org.gradle.declarative.dsl.schema.FunctionSemantics.AccessAndConfigure.ReturnType
import org.gradle.declarative.dsl.schema.FunctionSemantics.AddAndConfigure
import org.gradle.declarative.dsl.schema.FunctionSemantics.Builder
import org.gradle.declarative.dsl.schema.FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement
import org.gradle.declarative.dsl.schema.FunctionSemantics.Pure
import org.gradle.declarative.dsl.schema.ParameterSemantics
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.language.DataTypeInternal
import java.util.Collections


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

    companion object Empty : AnalysisSchema {
        override fun getTopLevelReceiverType(): DataClass = DefaultDataClass.Empty


        override fun getDataClassesByFqName(): Map<FqName, DataClass> = mapOf()

        override fun getExternalFunctionsByFqName(): Map<FqName, DataTopLevelFunction> = mapOf()

        override fun getExternalObjectsByFqName(): Map<FqName, ExternalObjectProviderKey> = mapOf()

        override fun getDefaultImports(): Set<FqName> = setOf()

        private
        fun readResolve(): Any = Empty
    }
}


@Serializable
@SerialName("data")
data class DefaultDataClass(
    override val name: FqName,
    override val supertypes: Set<FqName>,
    override val properties: List<DataProperty>,
    override val memberFunctions: List<SchemaMemberFunction>,
    override val constructors: List<DataConstructor>
) : DataClass {

    override fun isClass(): Boolean = true

    override fun getDataClass(): DefaultDataClass = this

    override fun toString(): String = name.simpleName

    companion object Empty : DataClass {
        override val name: FqName = FqName.Empty
        override val supertypes: Set<FqName> = Collections.emptySet()
        override val properties: List<DataProperty> = Collections.emptyList()
        override val memberFunctions: List<SchemaMemberFunction> = Collections.emptyList()
        override val constructors: List<DataConstructor> = Collections.emptyList()
        private
        fun readResolve(): Any = Empty
    }
}


@Serializable
@SerialName("dataProperty")
data class DefaultDataProperty(
    override val name: String,
    override val valueType: DataTypeRef,
    override val mode: DataProperty.PropertyMode,
    override val hasDefaultValue: Boolean,
    override val isHiddenInDsl: Boolean = false,
    override val isDirectAccessOnly: Boolean = false
) : DataProperty {
    data object DefaultPropertyMode {
        @Serializable
        data object DefaultReadWrite : DataProperty.PropertyMode.ReadWrite {
            private
            fun readResolve(): Any = DefaultReadWrite
        }


        @Serializable
        data object DefaultReadOnly : DataProperty.PropertyMode.ReadOnly {
            private
            fun readResolve(): Any = DefaultReadOnly
        }


        @Serializable
        data object DefaultWriteOnly : DataProperty.PropertyMode.WriteOnly {
            private
            fun readResolve(): Any = DefaultWriteOnly
        }
    }
}


val DataProperty.isReadOnly: Boolean
    get() = mode.canRead() && !mode.canWrite()


val DataProperty.isWriteOnly: Boolean
    get() = !mode.canRead() && mode.canWrite()


@Serializable
data class DataBuilderFunction(
    private val receiver: DataTypeRef,
    private val simpleName: String,
    private val isDirectAccessOnly: Boolean,
    val dataParameter: DataParameter,
) : SchemaMemberFunction {
    private
    val internalSemantics: FunctionSemantics by lazy { DefaultFunctionSemantics.DefaultBuilder(receiver) }
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
data class DefaultDataTopLevelFunction(
    private val packageName: String,
    private val simpleName: String,
    private val parameters: List<DataParameter>,
    private val semantics: Pure,
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
data class DefaultDataConstructor(
    private val parameters: List<DataParameter>,
    private val dataClass: DataTypeRef
) : DataConstructor {
    private
    val internalSemantics: FunctionSemantics by lazy { DefaultFunctionSemantics.DefaultPure(dataClass) }

    override fun getSimpleName(): String = "<init>"

    override fun getParameters(): List<DataParameter> = parameters

    override fun getSemantics(): FunctionSemantics = internalSemantics

    override fun getReturnValueType(): DataTypeRef = internalSemantics.returnValueType

    override fun getDataClass(): DataTypeRef = dataClass
}


@Serializable
data class DefaultDataParameter(
    private val name: String?,
    @SerialName("privateType")
    private val type: DataTypeRef,
    private val isDefault: Boolean,
    private val semantics: ParameterSemanticsInternal
) : DataParameter {
    override fun getName(): String? = name

    override fun getType(): DataTypeRef = type

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
    data object Unknown : ParameterSemanticsInternal {
        private
        fun readResolve(): Any = Unknown
    }
}


object DefaultFunctionSemantics {

    @Serializable
    @SerialName("builder")
    class DefaultBuilder(private val objectType: DataTypeRef) : Builder {
        override val returnValueType: DataTypeRef
            get() = objectType
    }

    @Serializable
    @SerialName("accessAndConfigure")
    class DefaultAccessAndConfigure(
        override val accessor: ConfigureAccessor,
        override val returnType: ReturnType
    ) : AccessAndConfigure {
        override val returnValueType: DataTypeRef
            get() = when (returnType) {
                is ReturnType.ConfiguredObject -> accessor.objectType
                is ReturnType.Unit -> DataTypeInternal.DefaultUnitType.ref
            }

        override fun getConfiguredType(): DataTypeRef {
            return if (returnType is ReturnType.ConfiguredObject) returnValueType else accessor.objectType
        }

        override fun getConfigureBlockRequirement(): ConfigureBlockRequirement {
            return DefaultConfigureBlockRequirement.DefaultRequired
        }

        /** Implementations for [ReturnType] */
        object DefaultReturnType {
            @Serializable
            @SerialName("configuredObject")
            data object DefaultConfiguredObject : ReturnType.ConfiguredObject {
                private
                fun readResolve(): Any = DefaultConfiguredObject
            }

            @Serializable
            @SerialName("unit")
            object DefaultUnit : ReturnType.Unit {
                private
                fun readResolve(): Any = DefaultUnit
            }
        }
    }

    @Serializable
    @SerialName("addAndConfigure")
    class DefaultAddAndConfigure(
        private val objectType: DataTypeRef,
        private val configureBlockRequirement: ConfigureBlockRequirement
    ) : AddAndConfigure {
        override val returnValueType: DataTypeRef
            get() = objectType

        override fun getConfiguredType(): DataTypeRef {
            return returnValueType
        }

        override fun getConfigureBlockRequirement(): ConfigureBlockRequirement {
            return configureBlockRequirement
        }
    }

    @Serializable
    @SerialName("pure")
    class DefaultPure(override val returnValueType: DataTypeRef) : Pure

    /** Implementations for [ConfigureBlockRequirement] */
    object DefaultConfigureBlockRequirement {
        @Serializable
        @SerialName("notAllowed")
        data object DefaultNotAllowed : ConfigureBlockRequirement.NotAllowed {
            private
            fun readResolve(): Any = DefaultNotAllowed
        }

        @Serializable
        @SerialName("optional")
        data object DefaultOptional : ConfigureBlockRequirement.Optional {
            private
            fun readResolve(): Any = DefaultOptional
        }

        @Serializable
        @SerialName("required")
        data object DefaultRequired : ConfigureBlockRequirement.Required {
            private
            fun readResolve(): Any = DefaultRequired
        }
    }
}


@Serializable
sealed interface ConfigureAccessorInternal : ConfigureAccessor {

    fun access(objectOrigin: ObjectOrigin, inFunction: ObjectOrigin.AccessAndConfigureReceiver): ObjectOrigin

    @Serializable
    data class Property(
        private val dataProperty: DataProperty
    ) : ConfigureAccessorInternal {

        override fun getObjectType(): DataTypeRef = dataProperty.valueType

        override fun access(objectOrigin: ObjectOrigin, inFunction: ObjectOrigin.AccessAndConfigureReceiver): ObjectOrigin =
            ObjectOrigin.PropertyReference(objectOrigin, dataProperty, objectOrigin.originElement)
    }

    @Serializable
    data class Custom(
        private val objectType: DataTypeRef,
        val customAccessorIdentifier: String
    ) : ConfigureAccessorInternal {

        override fun getObjectType(): DataTypeRef = objectType

        override fun access(objectOrigin: ObjectOrigin, inFunction: ObjectOrigin.AccessAndConfigureReceiver): ObjectOrigin =
            ObjectOrigin.CustomConfigureAccessor(objectOrigin, this, objectOrigin.originElement)
    }

    @Serializable
    data class ConfiguringLambdaArgument(
        private val objectType: DataTypeRef
    ) : ConfigureAccessorInternal {

        override fun getObjectType(): DataTypeRef = objectType

        override fun access(objectOrigin: ObjectOrigin, inFunction: ObjectOrigin.AccessAndConfigureReceiver): ObjectOrigin =
            ObjectOrigin.ConfiguringLambdaReceiver(inFunction.function, inFunction.parameterBindings, inFunction.invocationId, objectType, inFunction.originElement, inFunction.receiver)
    }

    // TODO: configure all elements by addition key?
    // TODO: Do we want to support configuring external objects?
}


@Serializable
data class DefaultFqName(override val packageName: String, override val simpleName: String) : FqName {
    companion object {
        fun parse(fqNameString: String): FqName {
            val parts = fqNameString.split(".")
            return DefaultFqName(parts.dropLast(1).joinToString("."), parts.last())
        }
    }

    override
    val qualifiedName by lazy { "$packageName.$simpleName" }

    override fun toString(): String = qualifiedName
}


val DataTopLevelFunction.fqName: FqName
    get() = DefaultFqName(packageName, simpleName)


@Serializable
data class DefaultExternalObjectProviderKey(
    private val type: DataTypeRef
) : ExternalObjectProviderKey {
    override fun getType(): DataTypeRef = type
}


object DataTypeRefInternal {
    @Serializable
    @SerialName("dataTypeRefType")
    data class DefaultType(
        private val dataType: DataType
    ) : DataTypeRef.Type {

        override fun isTyped(): Boolean = true

        override fun getDataType(): DataType = dataType
    }

    @Serializable
    @SerialName("dataTypeRefName")
    data class DefaultName(
        private val fqName: FqName
    ) : DataTypeRef.Name {
        override fun isNamed() = true

        override fun getFqName(): FqName = fqName
    }
}


val DataType.ref: DataTypeRef
    get() = DataTypeRefInternal.DefaultType(this)
