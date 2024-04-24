/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.declarativedsl.schemaimpl

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
import org.gradle.internal.declarativedsl.schema.DataProperty.PropertyMode
import org.gradle.internal.declarativedsl.schema.DataTopLevelFunction
import org.gradle.internal.declarativedsl.schema.DataType
import org.gradle.internal.declarativedsl.schema.DataTypeRef
import org.gradle.internal.declarativedsl.schema.ExternalObjectProviderKey
import org.gradle.internal.declarativedsl.schema.FqName
import org.gradle.internal.declarativedsl.schema.FunctionSemantics
import org.gradle.internal.declarativedsl.schema.FunctionSemantics.AccessAndConfigure.ReturnType
import org.gradle.internal.declarativedsl.schema.FunctionSemantics.ConfigureSemantics.ConfigureBlockRequirement
import org.gradle.internal.declarativedsl.schema.ParameterSemantics
import org.gradle.internal.declarativedsl.schema.SchemaMemberFunction


@Serializable
@SerialName("analysisSchema")
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


/** Implementations for [DataType] */
object DataTypeImpl {
    @Serializable
    @SerialName("int")
    data object IntDataTypeImpl : DataType.IntDataType {
        override fun toString(): String = "Int"
    }

    @Serializable
    @SerialName("long")
    data object LongDataTypeImpl : DataType.LongDataType {
        override fun toString(): String = "Long"
    }

    @Serializable
    @SerialName("string")
    data object StringDataTypeImpl : DataType.StringDataType {
        override fun toString(): String = "String"
    }

    @Serializable
    @SerialName("boolean")
    data object BooleanDataTypeImpl : DataType.BooleanDataType {
        override fun toString(): String = "Boolean"
    }

    @Serializable
    @SerialName("null")
    data object NullTypeImpl : DataType.NullType {
        override fun toString(): String = "Null"
    }

    @Serializable
    @SerialName("unit")
    data object UnitTypeImpl : DataType.UnitType {
        override fun toString(): String = "Unit"
    }
}


@Serializable
@SerialName("dataProperty")
data class DataPropertyImpl(
    override val name: String,
    override val valueType: DataTypeRef,
    override val mode: PropertyMode,
    override val hasDefaultValue: Boolean,
    override val isHiddenInDsl: Boolean = false,
    override val isDirectAccessOnly: Boolean = false
) : DataProperty {

    /** Implementations for [PropertyMode] */
    data object PropertyModeImpl {
        @Serializable
        data object ReadWriteImpl : PropertyMode.ReadWrite


        @Serializable
        data object ReadOnlyImpl : PropertyMode.ReadOnly


        @Serializable
        data object WriteOnlyImpl : PropertyMode.WriteOnly
    }
}


val DataProperty.isReadOnly: Boolean
    get() = mode is PropertyMode.ReadOnly


val DataProperty.isWriteOnly: Boolean
    get() = mode is PropertyMode.WriteOnly


@Serializable
@SerialName("dataBuilderFunction")
data class DataBuilderFunctionImpl(
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
@SerialName("dataTopLevelFunction")
data class DataTopLevelFunctionImpl(
    override val packageName: String,
    override val simpleName: String,
    override val parameters: List<DataParameter>,
    override val semantics: FunctionSemantics.Pure,
) : DataTopLevelFunction


@Serializable
@SerialName("dataMemberFunction")
data class DataMemberFunctionImpl(
    override val receiver: DataTypeRef,
    override val simpleName: String,
    override val parameters: List<DataParameter>,
    override val isDirectAccessOnly: Boolean,
    override val semantics: FunctionSemantics,
) : DataMemberFunction


@Serializable
@SerialName("dataConstructor")
data class DataConstructorImpl(
    override val parameters: List<DataParameter>,
    override val dataClass: DataTypeRef
) : DataConstructor {
    override val simpleName
        get() = "<init>"
    override val semantics: FunctionSemantics.Pure = FunctionSemanticsImpl.PureImpl(dataClass)
}


@Serializable
@SerialName("dataParameter")
data class DataParameterImpl(
    override val name: String?,
    @SerialName("parameterType")
    override val type: DataTypeRef,
    override val isDefault: Boolean,
    override val semantics: ParameterSemantics
) : DataParameter


/** Implementations for [ParameterSemantics] */
object ParameterSemanticsImpl {
    @Serializable
    @SerialName("storeValueInProperty")
    data class StoreValueInPropertyImpl(override val dataProperty: DataProperty) : ParameterSemantics.StoreValueInProperty

    @Serializable
    @SerialName("unknown")
    data object UnknownImpl : ParameterSemantics.Unknown
}


/** Implementations for [FunctionSemantics] */
object FunctionSemanticsImpl {
    @Serializable
    @SerialName("builder")
    class BuilderImpl(private val objectType: DataTypeRef) : FunctionSemantics.Builder {
        override val returnValueType: DataTypeRef
            get() = objectType
    }

    @Serializable
    @SerialName("accessAndConfigure")
    class AccessAndConfigureImpl(
        override val accessor: ConfigureAccessor,
        override val returnType: ReturnType
    ) : FunctionSemantics.AccessAndConfigure {
        override val returnValueType: DataTypeRef
            get() = when (returnType) {
                is ReturnType.ConfiguredObject -> accessor.objectType
                is ReturnType.Unit -> DataTypeImpl.UnitTypeImpl.ref
            }

        override val configureBlockRequirement: ConfigureBlockRequirement.Required
            get() = ConfigureBlockRequirementImpl.RequiredImpl

        /** Implementations for [ReturnType] */
        object ReturnTypeImpl {
            @Serializable
            @SerialName("configuredObject")
            data object ConfiguredObjectImpl : ReturnType.ConfiguredObject

            @Serializable
            @SerialName("unit")
            object UnitImpl : ReturnType.Unit
        }
    }

    @Serializable
    @SerialName("addAndConfigure")
    class AddAndConfigureImpl(
        private val objectType: DataTypeRef,
        override val configureBlockRequirement: ConfigureBlockRequirement
    ) : FunctionSemantics.AddAndConfigure {
        override val returnValueType: DataTypeRef
            get() = objectType

        override val configuredType: DataTypeRef
            get() = returnValueType
    }

    @Serializable
    @SerialName("pure")
    class PureImpl(override val returnValueType: DataTypeRef) : FunctionSemantics.Pure

    /** Implementations for [ConfigureBlockRequirement] */
    object ConfigureBlockRequirementImpl {
        @Serializable
        @SerialName("notAllowed")
        data object NotAllowedImpl : ConfigureBlockRequirement.NotAllowed

        @Serializable
        @SerialName("optional")
        data object OptionalImpl : ConfigureBlockRequirement.Optional

        @Serializable
        @SerialName("required")
        data object RequiredImpl : ConfigureBlockRequirement.Required
    }
}


/** Implementations for [ConfigureAccessor] */
object ConfigureAccessorImpl {
    @Serializable
    @SerialName("property")
    data class PropertyImpl(override val dataProperty: DataProperty) : ConfigureAccessor.Property

    @Serializable
    @SerialName("custom")
    data class CustomImpl(override val objectType: DataTypeRef, override val customAccessorIdentifier: String) : ConfigureAccessor.Custom

    @Serializable
    @SerialName("configuringLambdaArgument")
    data class ConfiguringLambdaArgumentImpl(override val objectType: DataTypeRef) : ConfigureAccessor.ConfiguringLambdaArgument

    // TODO: configure all elements by addition key?
    // TODO: Do we want to support configuring external objects?
}


@Serializable
@SerialName("fqName")
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
@SerialName("externalObjectProviderKey")
data class ExternalObjectProviderKeyImpl(override val objectType: DataTypeRef) : ExternalObjectProviderKey


/** Implementations for [DataTypeRef] */
object DataTypeRefImpl {
    @Serializable
    @SerialName("dataTypeRefType")
    data class TypeImpl(override val dataType: DataType) : DataTypeRef.Type

    @Serializable
    @SerialName("dataTypeRefName")
    data class NameImpl(override val fqName: FqName) : DataTypeRef.Name
}


val DataType.ref: DataTypeRef
    get() = DataTypeRefImpl.TypeImpl(this)
