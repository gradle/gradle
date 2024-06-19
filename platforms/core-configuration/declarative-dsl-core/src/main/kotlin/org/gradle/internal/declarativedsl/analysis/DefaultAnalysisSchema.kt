package org.gradle.internal.declarativedsl.analysis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.ConfigureAccessor
import org.gradle.declarative.dsl.schema.DataBuilderFunction
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataConstructor
import org.gradle.declarative.dsl.schema.DataMemberFunction
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.DataProperty.PropertyMode
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
@SerialName("analysisSchema")
data class DefaultAnalysisSchema(
    override val topLevelReceiverType: DataClass,
    override val dataClassesByFqName: Map<FqName, DataClass>,
    override val externalFunctionsByFqName: Map<FqName, DataTopLevelFunction>,
    override val externalObjectsByFqName: Map<FqName, ExternalObjectProviderKey>,
    override val defaultImports: Set<FqName>
) : AnalysisSchema {
    companion object Empty : AnalysisSchema {
        override val topLevelReceiverType: DataClass = DefaultDataClass.Empty
        override val dataClassesByFqName: Map<FqName, DataClass> = mapOf()
        override val externalFunctionsByFqName: Map<FqName, DataTopLevelFunction> = mapOf()
        override val externalObjectsByFqName: Map<FqName, ExternalObjectProviderKey> = mapOf()
        override val defaultImports: Set<FqName> = setOf()

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
    override val mode: PropertyMode,
    override val hasDefaultValue: Boolean,
    override val isHiddenInDsl: Boolean = false,
    override val isDirectAccessOnly: Boolean = false
) : DataProperty {
    data object DefaultPropertyMode {
        @Serializable
        data object DefaultReadWrite : PropertyMode.ReadWrite {
            private
            fun readResolve(): Any = DefaultReadWrite
        }


        @Serializable
        data object DefaultReadOnly : PropertyMode.ReadOnly {
            private
            fun readResolve(): Any = DefaultReadOnly
        }


        @Serializable
        data object DefaultWriteOnly : PropertyMode.WriteOnly {
            private
            fun readResolve(): Any = DefaultWriteOnly
        }
    }
}


val DataProperty.isReadOnly: Boolean
    get() = mode is PropertyMode.ReadOnly


val DataProperty.isWriteOnly: Boolean
    get() = mode is PropertyMode.WriteOnly


@Serializable
@SerialName("dataBuilderFunction")
data class DefaultDataBuilderFunction(
    override val receiver: DataTypeRef,
    override val simpleName: String,
    override val isDirectAccessOnly: Boolean,
    override val dataParameter: DataParameter,
) : DataBuilderFunction {
    override val semantics: Builder = FunctionSemanticsInternal.DefaultBuilder(receiver)
    override val parameters: List<DataParameter>
        get() = listOf(dataParameter)
}


@Serializable
@SerialName("dataTopLevelFunction")
data class DefaultDataTopLevelFunction(
    override val packageName: String,
    override val simpleName: String,
    override val parameters: List<DataParameter>,
    override val semantics: Pure,
) : DataTopLevelFunction


@Serializable
@SerialName("dataMemberFunction")
data class DefaultDataMemberFunction(
    override val receiver: DataTypeRef,
    override val simpleName: String,
    override val parameters: List<DataParameter>,
    override val isDirectAccessOnly: Boolean,
    override val semantics: FunctionSemantics,
) : DataMemberFunction


@Serializable
@SerialName("dataConstructor")
data class DefaultDataConstructor(
    override val parameters: List<DataParameter>,
    override val dataClass: DataTypeRef
) : DataConstructor {
    override val simpleName
        get() = "<init>"
    override val semantics: Pure = FunctionSemanticsInternal.DefaultPure(dataClass)
}


@Serializable
data class DefaultDataParameter(
    override val name: String?,
    @SerialName("privateType")
    override val type: DataTypeRef,
    override val isDefault: Boolean,
    override val semantics: ParameterSemantics
) : DataParameter


object ParameterSemanticsInternal {
    @Serializable
    @SerialName("storeValueInProperty")
    data class DefaultStoreValueInProperty(override val dataProperty: DataProperty) : ParameterSemantics.StoreValueInProperty

    @Serializable
    @SerialName("unknown")
    data object DefaultUnknown : ParameterSemantics.Unknown {
        private
        fun readResolve(): Any = DefaultUnknown
    }
}


object FunctionSemanticsInternal {

    @Serializable
    @SerialName("builder")
    data class DefaultBuilder(private val objectType: DataTypeRef) : Builder {
        override val returnValueType: DataTypeRef
            get() = objectType
    }

    @Serializable
    @SerialName("accessAndConfigure")
    data class DefaultAccessAndConfigure(
        override val accessor: ConfigureAccessor,
        override val returnType: ReturnType,
        override val configureBlockRequirement: ConfigureBlockRequirement
    ) : AccessAndConfigure {
        override val returnValueType: DataTypeRef
            get() = when (returnType) {
                is ReturnType.ConfiguredObject -> accessor.objectType
                is ReturnType.Unit -> DataTypeInternal.DefaultUnitType.ref
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
    data class DefaultAddAndConfigure(
        private val objectType: DataTypeRef,
        override val configureBlockRequirement: ConfigureBlockRequirement
    ) : AddAndConfigure {
        override val returnValueType: DataTypeRef
            get() = objectType

        override val configuredType: DataTypeRef
            get() = returnValueType
    }

    @Serializable
    @SerialName("pure")
    data class DefaultPure(override val returnValueType: DataTypeRef) : Pure

    /** Implementations for [ConfigureBlockRequirement] */
    data object DefaultConfigureBlockRequirement {
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


object ConfigureAccessorInternal {
    @Serializable
    @SerialName("property")
    data class DefaultProperty(override val dataProperty: DataProperty) : ConfigureAccessor.Property

    @Serializable
    @SerialName("custom")
    data class DefaultCustom(override val objectType: DataTypeRef, override val customAccessorIdentifier: String) : ConfigureAccessor.Custom

    @Serializable
    @SerialName("configuringLambdaArgument")
    data class DefaultConfiguringLambdaArgument(override val objectType: DataTypeRef) : ConfigureAccessor.ConfiguringLambdaArgument

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
@SerialName("externalObjectProviderKey")
data class DefaultExternalObjectProviderKey(override val objectType: DataTypeRef) : ExternalObjectProviderKey


object DataTypeRefInternal {
    @Serializable
    @SerialName("dataTypeRefType")
    data class DefaultType(override val dataType: DataType) : DataTypeRef.Type {
        override fun toString(): String = dataType.toString()
    }

    @Serializable
    @SerialName("dataTypeRefName")
    data class DefaultName(override val fqName: FqName) : DataTypeRef.Name {
        override fun toString(): String = fqName.simpleName
    }
}


val DataType.ref: DataTypeRef
    get() = DataTypeRefInternal.DefaultType(this)
