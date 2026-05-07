/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.declarativedsl.documentation

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.internal.declarativedsl.analysis.DataTypeRefInternal
import org.gradle.internal.declarativedsl.analysis.DefaultAnalysisSchema
import org.gradle.internal.declarativedsl.analysis.DefaultDataClass
import org.gradle.internal.declarativedsl.analysis.DefaultDataParameter
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.analysis.DefaultVarargSignature
import org.gradle.internal.declarativedsl.analysis.ParameterSemanticsInternal
import org.gradle.internal.declarativedsl.analysis.TypeArgumentInternal
import org.gradle.internal.declarativedsl.language.DataTypeInternal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.Test


class CatalogKeysTest {

    @Test
    fun `int primitive maps to int`() {
        assertEquals("int", paramTypeFragment(primitive(DataTypeInternal.DefaultIntDataType), schemaWith()))
    }

    @Test
    fun `long primitive maps to long`() {
        assertEquals("long", paramTypeFragment(primitive(DataTypeInternal.DefaultLongDataType), schemaWith()))
    }

    @Test
    fun `boolean primitive maps to boolean`() {
        assertEquals("boolean", paramTypeFragment(primitive(DataTypeInternal.DefaultBooleanDataType), schemaWith()))
    }

    @Test
    fun `string primitive maps to java lang String`() {
        assertEquals("java.lang.String", paramTypeFragment(primitive(DataTypeInternal.DefaultStringDataType), schemaWith()))
    }

    @Test
    fun `unit primitive maps to void`() {
        assertEquals("void", paramTypeFragment(primitive(DataTypeInternal.DefaultUnitType), schemaWith()))
    }

    @Test
    fun `top level class type uses its javaTypeName`() {
        val fqn = DefaultFqName.parse("org.example.Foo")
        val cls = dataClass(fqn, "org.example.Foo")
        val schema = schemaWith(types = mapOf(Pair(fqn, cls)))

        assertEquals("org.example.Foo", paramTypeFragment(name(fqn), schema))
    }

    @Test
    fun `nested class type uses dollar separated javaTypeName`() {
        val fqn = DefaultFqName.parse("org.example.Outer.Inner")
        val cls = dataClass(fqn, "org.example.Outer\$Inner")
        val schema = schemaWith(types = mapOf(Pair(fqn, cls)))

        assertEquals("org.example.Outer\$Inner", paramTypeFragment(name(fqn), schema))
    }

    @Test
    fun `parameterized type is erased to the signature javaTypeName`() {
        val listFqn = DefaultFqName.parse("kotlin.collections.List")
        val signature = parameterizedSignature(listFqn, "java.util.List")
        val schema = schemaWith(signatures = mapOf(Pair(listFqn, signature)))

        val ref = DataTypeRefInternal.DefaultNameWithArgs(
            listFqn,
            listOf(TypeArgumentInternal.DefaultConcreteTypeArgument(primitive(DataTypeInternal.DefaultStringDataType)))
        )

        assertEquals("java.util.List", paramTypeFragment(ref, schema))
    }

    @Test
    fun `vararg of String yields java lang String with brackets`() {
        val schema = schemaWith(signatures = mapOf(Pair(DefaultVarargSignature.name, DefaultVarargSignature)))

        val ref = DataTypeRefInternal.DefaultNameWithArgs(
            DefaultVarargSignature.name,
            listOf(TypeArgumentInternal.DefaultConcreteTypeArgument(primitive(DataTypeInternal.DefaultStringDataType)))
        )

        assertEquals("java.lang.String[]", paramTypeFragment(ref, schema))
    }

    @Test
    fun `vararg of user type yields its javaTypeName with brackets`() {
        val fooFqn = DefaultFqName.parse("org.example.Foo")
        val foo = dataClass(fooFqn, "org.example.Foo")
        val schema = schemaWith(
            types = mapOf(Pair(fooFqn, foo)),
            signatures = mapOf(Pair(DefaultVarargSignature.name, DefaultVarargSignature))
        )

        val ref = DataTypeRefInternal.DefaultNameWithArgs(
            DefaultVarargSignature.name,
            listOf(TypeArgumentInternal.DefaultConcreteTypeArgument(name(fooFqn)))
        )

        assertEquals("org.example.Foo[]", paramTypeFragment(ref, schema))
    }

    @Test
    fun `function key with no parameters has empty arg list`() {
        assertEquals("doIt()", functionKey("doIt", emptyList(), schemaWith()))
    }

    @Test
    fun `function key with a single primitive`() {
        val params = listOf(parameter("count", primitive(DataTypeInternal.DefaultIntDataType)))
        assertEquals("compute(int)", functionKey("compute", params, schemaWith()))
    }

    @Test
    fun `function key with mixed primitives is comma separated with no whitespace`() {
        val params = listOf(
            parameter("name", primitive(DataTypeInternal.DefaultStringDataType)),
            parameter("depth", primitive(DataTypeInternal.DefaultIntDataType)),
            parameter("flag", primitive(DataTypeInternal.DefaultBooleanDataType))
        )
        assertEquals("compute(java.lang.String,int,boolean)", functionKey("compute", params, schemaWith()))
    }

    @Test
    fun `function key with a class type alongside primitives`() {
        val fqn = DefaultFqName.parse("org.example.Foo")
        val cls = dataClass(fqn, "org.example.Foo")
        val schema = schemaWith(types = mapOf(Pair(fqn, cls)))
        val params = listOf(
            parameter("foo", name(fqn)),
            parameter("count", primitive(DataTypeInternal.DefaultIntDataType))
        )
        assertEquals("compute(org.example.Foo,int)", functionKey("compute", params, schema))
    }

    @Test
    fun `function key with a trailing vararg of String`() {
        val schema = schemaWith(signatures = mapOf(Pair(DefaultVarargSignature.name, DefaultVarargSignature)))
        val params = listOf(
            parameter("name", primitive(DataTypeInternal.DefaultStringDataType)),
            parameter(
                "rest",
                DataTypeRefInternal.DefaultNameWithArgs(
                    DefaultVarargSignature.name,
                    listOf(TypeArgumentInternal.DefaultConcreteTypeArgument(primitive(DataTypeInternal.DefaultStringDataType)))
                )
            )
        )
        assertEquals("doIt(java.lang.String,java.lang.String[])", functionKey("doIt", params, schema))
    }

    @Test
    fun `function key with a single vararg`() {
        val schema = schemaWith(signatures = mapOf(Pair(DefaultVarargSignature.name, DefaultVarargSignature)))
        val params = listOf(
            parameter(
                "values",
                DataTypeRefInternal.DefaultNameWithArgs(
                    DefaultVarargSignature.name,
                    listOf(TypeArgumentInternal.DefaultConcreteTypeArgument(primitive(DataTypeInternal.DefaultIntDataType)))
                )
            )
        )
        assertEquals("sum(int[])", functionKey("sum", params, schema))
    }


    private fun primitive(type: DataType.PrimitiveType): DataTypeRef =
        DataTypeRefInternal.DefaultType(type)

    private fun name(fqn: FqName): DataTypeRef =
        DataTypeRefInternal.DefaultName(fqn)

    private fun parameter(name: String, type: DataTypeRef): DataParameter =
        DefaultDataParameter(name, type, isDefault = false, semantics = ParameterSemanticsInternal.DefaultUnknown)

    private fun dataClass(fqn: FqName, javaTypeName: String): DefaultDataClass =
        DefaultDataClass(
            name = fqn,
            javaTypeName = javaTypeName,
            javaTypeArgumentTypeNames = emptyList(),
            supertypes = emptySet(),
            properties = emptyList(),
            memberFunctions = emptyList(),
            constructors = emptyList(),
            metadata = emptyList()
        )

    private fun parameterizedSignature(fqn: FqName, javaTypeName: String): DataType.ParameterizedTypeSignature =
        DataTypeInternal.DefaultParameterizedTypeSignature(
            name = fqn,
            typeParameters = listOf(DataTypeInternal.DefaultParameterizedTypeSignature.TypeParameter("T", isOutVariant = false)),
            javaTypeName = javaTypeName
        )

    private fun schemaWith(
        types: Map<FqName, DataType.ClassDataType> = emptyMap(),
        signatures: Map<FqName, DataType.ParameterizedTypeSignature> = emptyMap()
    ): AnalysisSchema {
        val placeholder = dataClass(DefaultFqName.parse("org.example.Placeholder"), "org.example.Placeholder")
        return DefaultAnalysisSchema(
            topLevelReceiverType = placeholder,
            dataClassTypesByFqName = types,
            genericSignaturesByFqName = signatures,
            genericInstantiationsByFqName = emptyMap(),
            externalFunctionsByFqName = emptyMap(),
            infixFunctionsByFqName = emptyMap(),
            externalObjectsByFqName = emptyMap(),
            defaultImports = emptySet(),
            assignmentAugmentationsByTypeName = emptyMap()
        )
    }
}
