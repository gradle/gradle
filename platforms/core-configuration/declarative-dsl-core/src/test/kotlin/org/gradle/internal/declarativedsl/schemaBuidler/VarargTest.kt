/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataType.ParameterizedTypeInstance.TypeArgument.ConcreteTypeArgument
import org.gradle.declarative.dsl.schema.VarargParameter
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.assertIs
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.gradle.internal.declarativedsl.schemaUtils.singleFunctionNamed
import org.gradle.internal.declarativedsl.schemaUtils.typeFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VarargTest {
    @Test
    fun `schema builder imports vararg function parameters of concrete types`() {
        with(SchemaTypeRefContext(schemaFromTypes)) {
            val varargParameter = schemaFromTypes.typeFor<Schema>()
                .singleFunctionNamed("acceptsStringVarargs").function
                .parameters.single()
            assertTrue(varargParameter is VarargParameter)
            val type = resolveRef(varargParameter.type)
            assertIs<DataType.ParameterizedTypeInstance>(type)
            assertIs<DataType.VarargSignature>(type.typeSignature)
            val elementType = resolveRef((type.typeArguments.single() as ConcreteTypeArgument).type)
            assertIs<DataType.StringDataType>(elementType)
        }
    }

    @Test
    fun `schema builder imports vararg function parameters of genericTypes`() {
        with(SchemaTypeRefContext(schemaFromTypes)) {
            val varargParameter = schemaFromTypes.typeFor<Schema>()
                .singleFunctionNamed("acceptsListOfStringVarargs").function
                .parameters.single()
            assertTrue(varargParameter is VarargParameter)
            val type = resolveRef(varargParameter.type)
            assertIs<DataType.ParameterizedTypeInstance>(type)
            assertIs<DataType.VarargSignature>(type.typeSignature)
            val elementType = resolveRef((type.typeArguments.single() as ConcreteTypeArgument).type)
            assertIs<DataType.ParameterizedTypeInstance>(elementType)
            assertEquals(List::class.qualifiedName, elementType.typeSignature.name.qualifiedName)
        }
    }

    val schemaFromTypes = schemaFromTypes(Schema::class, listOf(Schema::class, List::class))

    private interface Schema {
        @Suppress("unused")
        @Adding
        fun acceptsIntVarargs(vararg strings: String)

        @Suppress("unused")
        @Adding
        fun acceptsStringVarargs(vararg strings: String)

        @Suppress("unused")
        @Adding
        fun acceptsListOfStringVarargs(vararg strings: List<String>)
    }
}
