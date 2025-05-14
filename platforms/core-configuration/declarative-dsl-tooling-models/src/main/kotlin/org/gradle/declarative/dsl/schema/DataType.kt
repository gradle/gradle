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

package org.gradle.declarative.dsl.schema

import org.gradle.declarative.dsl.schema.DataType.PrimitiveType
import org.gradle.declarative.dsl.schema.DataType.ConstantType
import org.gradle.declarative.dsl.schema.DataType.IntDataType
import org.gradle.declarative.dsl.schema.DataType.LongDataType
import org.gradle.declarative.dsl.schema.DataType.StringDataType
import org.gradle.declarative.dsl.schema.DataType.BooleanDataType
import org.gradle.declarative.dsl.schema.DataType.NullType
import org.gradle.declarative.dsl.schema.DataType.UnitType
import org.gradle.declarative.dsl.schema.DataType.TypeVariableUsage
import org.gradle.declarative.dsl.schema.DataType.ClassDataType
import org.gradle.declarative.dsl.schema.DataType.ParameterizedTypeInstance
import org.gradle.declarative.dsl.schema.DataType.ParameterizedTypeInstance.TypeArgument.ConcreteTypeArgument
import org.gradle.declarative.dsl.schema.DataType.ParameterizedTypeInstance.TypeArgument.StarProjection
import org.gradle.tooling.ToolingModelContract
import java.io.Serializable


@ToolingModelContract(subTypes = [
    PrimitiveType::class,
        ConstantType::class,
            IntDataType::class,
            LongDataType::class,
            StringDataType::class,
            BooleanDataType::class,
        NullType::class,
        UnitType::class,
        TypeVariableUsage::class,
    ClassDataType::class,
        ParameterizedTypeInstance::class,
        DataClass::class,
        EnumClass::class
])
sealed interface DataType : Serializable {

    @ToolingModelContract(subTypes = [
        ConstantType::class,
            IntDataType::class,
            LongDataType::class,
            StringDataType::class,
            BooleanDataType::class,
        NullType::class,
        UnitType::class,
        TypeVariableUsage::class,
    ])
    sealed interface PrimitiveType : DataType

    @ToolingModelContract(subTypes = [
        IntDataType::class,
        LongDataType::class,
        StringDataType::class,
        BooleanDataType::class,
    ])
    sealed interface ConstantType<JvmType> : PrimitiveType {
        val constantType: Class<*>
    }

    interface IntDataType : ConstantType<Int>
    interface LongDataType : ConstantType<Long>
    interface StringDataType : ConstantType<String>
    interface BooleanDataType : ConstantType<Boolean>

    // TODO: implement nulls?
    interface NullType : PrimitiveType

    interface UnitType : PrimitiveType

    /**
     * Appears in a type position in generic signatures.
     * Two type variable usages with the same [id] are considered usages of the same type variable.
     */
    interface TypeVariableUsage : PrimitiveType {
        val variableId: Long
    }

    @ToolingModelContract(subTypes = [
        ClassDataType::class,
            ParameterizedTypeInstance::class,
            EnumClass::class,
            DataClass::class,
        ParameterizedTypeSignature::class,
            VarargSignature::class,
    ])
    sealed interface HasTypeName {
        val name: FqName
        val javaTypeName: String
    }

    @ToolingModelContract(subTypes = [
        ParameterizedTypeInstance::class,
        DataClass::class,
        EnumClass::class
    ])
    sealed interface ClassDataType : DataType, HasTypeName

    /**
     * A template for a parameterized type.
     * With the current limitations, these types are opaque, meaning that they may not have any members, and DCL cannot reason about the content of this type.
     *
     * [typeParameters] can be substituted by any other [DataTypeRef]s at the use sites, see [ParameterizedTypeInstance].
     *
     * This is not a type that can be used in the schema.
     * Declarations in the schema may use [ParameterizedTypeInstance] referencing the [ParameterizedTypeSignature] instead.
     */
    // In the future, a type signature may become resolvable to a generic DataClass, but for now, it should not be associated with any DataClass, even if
    // a data class with the same name appears in the schema.
    @ToolingModelContract(subTypes = [
        VarargSignature::class
    ])
    interface ParameterizedTypeSignature : HasTypeName, Serializable {
        interface TypeParameter : Serializable {
            val name: String
            val isOutVariant: Boolean
        }

        val typeParameters: List<TypeParameter>
    }

    /**
     * Represents the single existing parameterized type signature that corresponds to the type of vararg parameters like `vararg ints: Int` or `vararg strings: String`.
     *
     * A schema should not have more than one instance of [VarargSignature], and all vararg types in function parameters should be represented by [ParameterizedTypeInstance] referencing
     * the [VarargSignature] as [ParameterizedTypeInstance.typeSignature].
     * These types may be handled specially by the processing stages to follow the vararg representations expected by the runtime.
     */
    interface VarargSignature : ParameterizedTypeSignature

    interface ParameterizedTypeInstance : ClassDataType {
        val typeSignature: ParameterizedTypeSignature
        val typeArguments: List<TypeArgument>

        override val name: FqName get() = typeSignature.name
        override val javaTypeName: String get() = typeSignature.javaTypeName

        @ToolingModelContract(subTypes = [
            ConcreteTypeArgument::class,
            StarProjection::class
        ])
        sealed interface TypeArgument : Serializable {
            interface ConcreteTypeArgument : TypeArgument {
                val type: DataTypeRef
            }

            interface StarProjection : TypeArgument
        }
    }

    // TODO: `Any` type?
    // TODO: Support subtyping of some sort in the schema rather than via reflection?
}
