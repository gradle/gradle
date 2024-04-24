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

package org.gradle.internal.declarativedsl.schema


interface AnalysisSchema {
    val topLevelReceiverType: DataClass
    val dataClassesByFqName: Map<FqName, DataClass>
    val externalFunctionsByFqName: Map<FqName, DataTopLevelFunction>
    val externalObjectsByFqName: Map<FqName, ExternalObjectProviderKey>
    val defaultImports: Set<FqName>
}


interface DataClass : DataType {
    val name: FqName
    val supertypes: Set<FqName>
    val properties: List<DataProperty>
    val memberFunctions: List<SchemaMemberFunction>
    val constructors: List<DataConstructor>
}


interface DataProperty {
    val name: String
    val valueType: DataTypeRef
    val mode: PropertyMode
    val hasDefaultValue: Boolean
    val isHiddenInDsl: Boolean
    val isDirectAccessOnly: Boolean

    sealed interface PropertyMode {
        interface ReadWrite : PropertyMode
        interface ReadOnly : PropertyMode
        interface WriteOnly : PropertyMode
    }
}


sealed interface SchemaFunction {
    val simpleName: String
    val semantics: FunctionSemantics
    val parameters: List<DataParameter>
    val returnValueType: DataTypeRef
        get() = semantics.returnValueType
}


sealed interface SchemaMemberFunction : SchemaFunction {
    override val simpleName: String
    val receiver: DataTypeRef
    val isDirectAccessOnly: Boolean
}


interface DataBuilderFunction : SchemaMemberFunction {
    override val receiver: DataTypeRef
    override val simpleName: String
    override val isDirectAccessOnly: Boolean
    val dataParameter: DataParameter
}


interface DataTopLevelFunction : SchemaFunction {
    val packageName: String
    override val simpleName: String
    override val parameters: List<DataParameter>
    override val semantics: FunctionSemantics.Pure
}


interface DataMemberFunction : SchemaMemberFunction {
    override val receiver: DataTypeRef
    override val simpleName: String
    override val parameters: List<DataParameter>
    override val isDirectAccessOnly: Boolean
    override val semantics: FunctionSemantics
}


interface DataConstructor : SchemaFunction {
    val dataClass: DataTypeRef
    override val parameters: List<DataParameter>
    override val simpleName
        get() = "<init>"
    override val semantics: FunctionSemantics.Pure
}


interface DataParameter {
    val name: String?
    val type: DataTypeRef
    val isDefault: Boolean
    val semantics: ParameterSemantics
}


sealed interface ParameterSemantics {
    interface StoreValueInProperty : ParameterSemantics {
        val dataProperty: DataProperty
    }

    interface Unknown : ParameterSemantics
}


sealed interface FunctionSemantics {
    val returnValueType: DataTypeRef

    sealed interface ConfigureSemantics : FunctionSemantics {
        val configuredType: DataTypeRef
        val configureBlockRequirement: ConfigureBlockRequirement

        sealed interface ConfigureBlockRequirement {
            interface NotAllowed : ConfigureBlockRequirement
            interface Optional : ConfigureBlockRequirement
            interface Required : ConfigureBlockRequirement

            val allows: Boolean
                get() = this !is NotAllowed

            val requires: Boolean
                get() = this is Required

            fun isValidIfLambdaIsPresent(isPresent: Boolean): Boolean = when {
                isPresent -> allows
                else -> !requires
            }
        }
    }

    sealed interface NewObjectFunctionSemantics : FunctionSemantics

    interface Builder : FunctionSemantics

    interface AccessAndConfigure : ConfigureSemantics {
        val accessor: ConfigureAccessor
        val returnType: ReturnType

        sealed interface ReturnType {
            interface Unit : ReturnType
            interface ConfiguredObject : ReturnType
        }

        override val configuredType: DataTypeRef
            get() = if (returnType is ReturnType.ConfiguredObject) returnValueType else accessor.objectType

        override val configureBlockRequirement: ConfigureSemantics.ConfigureBlockRequirement.Required
    }

    interface AddAndConfigure : NewObjectFunctionSemantics, ConfigureSemantics {
        override val configureBlockRequirement: ConfigureSemantics.ConfigureBlockRequirement

        override val configuredType: DataTypeRef
            get() = returnValueType
    }

    interface Pure : NewObjectFunctionSemantics {
        override val returnValueType: DataTypeRef
    }
}


sealed interface ConfigureAccessor {
    val objectType: DataTypeRef

    interface Property : ConfigureAccessor {
        val dataProperty: DataProperty

        override val objectType: DataTypeRef
            get() = dataProperty.valueType
    }

    interface Custom : ConfigureAccessor {
        val customAccessorIdentifier: String
    }

    interface ConfiguringLambdaArgument : ConfigureAccessor

    // TODO: configure all elements by addition key?
    // TODO: Do we want to support configuring external objects?
}


interface FqName {
    val packageName: String
    val simpleName: String
}


interface ExternalObjectProviderKey {
    val objectType: DataTypeRef
}


sealed interface DataTypeRef {
    interface Type : DataTypeRef {
        val dataType: DataType
    }

    interface Name : DataTypeRef {
        val fqName: FqName
    }
}
