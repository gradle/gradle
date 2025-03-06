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

package org.gradle.internal.declarativedsl.schemaBuidler

import org.gradle.declarative.dsl.model.annotations.Configuring
import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataType.ParameterizedTypeInstance.TypeArgument.ConcreteTypeArgument
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.assertIs
import org.gradle.internal.declarativedsl.schemaBuilder.DeclarativeDslSchemaBuildingException
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.gradle.internal.declarativedsl.schemaUtils.singleFunctionNamed
import org.gradle.internal.declarativedsl.schemaUtils.typeFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.jupiter.api.assertThrows

class GenericOpaqueTypesTest {
    @Test
    fun `imports generic type parameters and their usages`() {
        val idFun = schemaWithCollections.typeFor<Schema>().singleFunctionNamed("id").function
        with(SchemaTypeRefContext(schemaWithCollections)) {
            val returnValue = resolveRef(idFun.semantics.returnValueType)
            assertIs<DataType.TypeVariableUsage>(returnValue)
            val parameterType = resolveRef(idFun.parameters.single().type)
            assertEquals(returnValue, parameterType)
        }
    }

    @Test
    fun `correctly imports a list return type`() {
        val myListOfFun = schemaWithCollections.typeFor<Schema>().singleFunctionNamed("myListOf").function
        with(SchemaTypeRefContext(schemaWithCollections)) {
            val listType = resolveRef(myListOfFun.returnValueType) as DataType.ParameterizedTypeInstance
            assertIs<DataType.ParameterizedTypeInstance>(listType)
            val listTypeArgument = resolveRef((listType.typeArguments[0] as ConcreteTypeArgument).type) as DataType.TypeVariableUsage
            assertEquals(resolveRef(myListOfFun.parameters[0].type), listTypeArgument)
            val listTypeSignature = listType.typeSignature
            assertSame(schema.genericSignaturesByFqName[listType.name], listTypeSignature)
            assertEquals("E", listTypeSignature.typeParameters.single().name)
        }
    }

    @Test
    fun `correctly imports a list of lists type`() {
        val function = schemaWithCollections.typeFor<Schema>().singleFunctionNamed("factoryFunctionTakingListOfLists")
        with(SchemaTypeRefContext(schemaWithCollections)) {
            val parameterType = resolveRef(function.function.parameters.single().type)
            assertIs<DataType.ParameterizedTypeInstance>(parameterType)
            assertEquals(List::class.qualifiedName, parameterType.typeSignature.name.qualifiedName)

            val typeArg = resolveRef((parameterType.typeArguments.single() as ConcreteTypeArgument).type)
            assertIs<DataType.ParameterizedTypeInstance>(typeArg)
            assertEquals(List::class.qualifiedName, typeArg.typeSignature.name.qualifiedName)

            val valueType = resolveRef((typeArg.typeArguments.single() as ConcreteTypeArgument).type)
            assertIs<DataType.StringDataType>(valueType)
        }
    }

    @Test
    fun `correctly imports a map type`() {
        val myMapFunction = schemaWithCollections.typeFor<Schema>().singleFunctionNamed("myMap")
        with(SchemaTypeRefContext(schemaWithCollections)) {
            val returnType = resolveRef(myMapFunction.function.returnValueType)
            assertIs<DataType.ParameterizedTypeInstance>(returnType)

            assertEquals(Map::class.qualifiedName, returnType.typeSignature.name.qualifiedName)
            assertIs<DataType.IntDataType>(resolveRef((returnType.typeArguments.first() as ConcreteTypeArgument).type))

            val valueType = resolveRef((returnType.typeArguments.last() as ConcreteTypeArgument).type)
            assertIs<DataType.ParameterizedTypeInstance>(valueType)
            assertEquals(GenericType::class.qualifiedName, valueType.typeSignature.name.qualifiedName)
            assertIs<DataType.StringDataType>(resolveRef((valueType.typeArguments.single() as ConcreteTypeArgument).type))
        }
    }

    @Test
    fun `reports illegal parameterized types used as container types`() {
        val exception = assertThrows<DeclarativeDslSchemaBuildingException> {
            schemaFromTypes(OuterTypeWithGenericInside::class, listOf(OuterTypeWithGenericInside::class, GenericType::class))
        }

        assertEquals(
            """
            Cannot use the parameterized class 'class org.gradle.internal.declarativedsl.schemaBuidler.GenericOpaqueTypesTest${'$'}GenericType' as a configurable type
              in configured type 'org.gradle.internal.declarativedsl.schemaBuidler.GenericOpaqueTypesTest.GenericType<kotlin.String>'
              in member 'fun org.gradle.internal.declarativedsl.schemaBuidler.GenericOpaqueTypesTest.OuterTypeWithGenericInside.configure(org.gradle.internal.declarativedsl.schemaBuidler.GenericOpaqueTypesTest.GenericType<kotlin.String>.() -> kotlin.Unit): kotlin.Unit'
              in class 'org.gradle.internal.declarativedsl.schemaBuidler.GenericOpaqueTypesTest.OuterTypeWithGenericInside'
            """.trimIndent(),
            exception.message
        )
    }

    private val schemaWithCollections get() = schemaFromTypes(Schema::class, listOf(Schema::class, List::class, GenericType::class))

    @Suppress("unused")
    class Schema {
        @Restricted
        fun <T> id(t: T): T = t

        @Restricted
        fun <T> myListOf(t1: T, t2: T): List<T> = listOf(t1, t2)

        @Restricted
        fun factoryFunctionTakingListOfLists(listOfLists: List<List<String>>): String = listOfLists.joinToString()

        @Restricted
        fun myMap(): Map<Int, GenericType<String>> = emptyMap()
    }

    @Suppress("unused")
    interface GenericType<T>

    @Suppress("unused")
    interface OuterTypeWithGenericInside {
        @Configuring
        fun configure(f: GenericType<String>.() -> Unit)
    }
}
