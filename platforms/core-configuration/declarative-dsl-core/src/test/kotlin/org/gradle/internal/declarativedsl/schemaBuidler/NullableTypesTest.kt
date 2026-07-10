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

package org.gradle.internal.declarativedsl.schemaBuidler

import org.gradle.internal.declarativedsl.analysis.ErrorReason
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.analysis.isWriteOnly
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.language.DataTypeInternal
import org.gradle.internal.declarativedsl.schemaBuilder.DeclarativeDslSchemaBuildingException
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.gradle.internal.declarativedsl.schemaUtils.functionFor
import org.gradle.internal.declarativedsl.schemaUtils.propertyFor
import org.junit.Assert
import org.junit.Test
import org.junit.jupiter.api.assertThrows

class NullableTypesTest {
    @Test
    fun `nullable read-write property is imported into the schema as a write-only property`() {
        val schema = schemaFromTypes(HasNullableReadWriteProperty::class)
        schema.propertyFor(HasNullableReadWriteProperty::nullableReadWriteProperty).run {
            Assert.assertTrue(property.isWriteOnly)
            Assert.assertEquals(DataTypeInternal.DefaultStringDataType, SchemaTypeRefContext(schema).resolveRef(property.valueType))
        }

        Assert.assertTrue(schema.resolve("${HasNullableReadWriteProperty::nullableReadWriteProperty.name} = \"foo\"").errors.isEmpty())

        schema.resolve("${HasNullableReadWriteProperty::nullableReadWriteProperty.name} = ${HasNullableReadWriteProperty::nestedNullableProp.name}.${Nested::value.name}()").run {
            Assert.assertTrue(errors.any { it.errorReason is ErrorReason.NonReadableProperty })
        }
    }

    @Test
    fun `functions can use nullable parameter types`() {
        val schema = schemaFromTypes(HasNullableFunctionParam::class)

        schema.functionFor(HasNullableFunctionParam::nullableParam).function.run {
            Assert.assertEquals(DataTypeInternal.DefaultStringDataType, SchemaTypeRefContext(schema).resolveRef(parameters.single().type))
        }
    }

    @Test
    fun `functions cannot return nullable types`() {
        assertThrows<DeclarativeDslSchemaBuildingException> { schemaFromTypes(HasNullableFunctionReturnType::class) }.run {
            Assert.assertEquals("""
                |Unsupported usage of a nullable type
                |  in return value type 'kotlin.String?'
                |  in member 'fun org.gradle.internal.declarativedsl.schemaBuidler.NullableTypesTest.HasNullableFunctionReturnType.nullableReturnType(kotlin.Int): kotlin.String?'
                |  in class 'org.gradle.internal.declarativedsl.schemaBuidler.NullableTypesTest.HasNullableFunctionReturnType'
            """.trimMargin("|"), message)
        }
    }

    @Test
    fun `nullable read-only properties with nullable types are not supported`() {
        assertThrows<DeclarativeDslSchemaBuildingException> { schemaFromTypes(HasNullableReadOnlyProperty::class) }.run {
            Assert.assertEquals("""
                |Unsupported property declaration: nullable read-only property
                |  in member 'val org.gradle.internal.declarativedsl.schemaBuidler.NullableTypesTest.HasNullableReadOnlyProperty.nullableReadOnly: kotlin.String?'
                |  in class 'org.gradle.internal.declarativedsl.schemaBuidler.NullableTypesTest.HasNullableReadOnlyProperty'
            """.trimMargin("|"), message)
        }
    }

    @Test
    fun `nullable type arguments are not supported`() {
        assertThrows<DeclarativeDslSchemaBuildingException> { schemaFromTypes(HasNullableTypeArg::class) }.run {
            Assert.assertEquals("""
                |Unsupported usage of a nullable type
                |  in type argument 'kotlin.String?'
                |  in return value type 'org.gradle.internal.declarativedsl.schemaBuidler.NullableTypesTest.Box<kotlin.String?>'
                |  in member 'var org.gradle.internal.declarativedsl.schemaBuidler.NullableTypesTest.HasNullableTypeArg.nullableProp: org.gradle.internal.declarativedsl.schemaBuidler.NullableTypesTest.Box<kotlin.String?>'
                |  in class 'org.gradle.internal.declarativedsl.schemaBuidler.NullableTypesTest.HasNullableTypeArg'
            """.trimMargin("|"), message)
        }
    }

    @Test
    fun `nullable configured types are not supported`() {
        assertThrows<DeclarativeDslSchemaBuildingException> { schemaFromTypes(HasNullableConfiguredType::class) }.run {
            Assert.assertEquals("""
                |Unsupported usage of a nullable type
                |  in configured type 'org.gradle.internal.declarativedsl.schemaBuidler.NullableTypesTest.Nested?'
                |  in member 'fun org.gradle.internal.declarativedsl.schemaBuidler.NullableTypesTest.HasNullableConfiguredType.configure((org.gradle.internal.declarativedsl.schemaBuidler.NullableTypesTest.Nested?) -> kotlin.Unit): kotlin.Unit'
                |  in class 'org.gradle.internal.declarativedsl.schemaBuidler.NullableTypesTest.HasNullableConfiguredType'
            """.trimMargin("|"), message)
        }
    }

    interface HasNullableReadOnlyProperty {
        @Suppress("unused")
        val nullableReadOnly: String?
    }

    interface HasNullableReadWriteProperty {
        @Suppress("unused")
        var nullableReadWriteProperty: String?

        var nestedNullableProp: Nested?
    }

    interface HasNullableTypeArg {
        @Suppress("unused")
        var nullableProp: Box<String?>
    }

    interface HasNullableConfiguredType {
        fun configure(action: Nested?.() -> Unit)
    }

    interface HasNullableFunctionParam {
        fun nullableParam(arg: String?): String
    }

    interface HasNullableFunctionReturnType {
        @Suppress("unused")
        fun nullableReturnType(x: Int): String?
    }

    interface Box<T>

    interface Nested {
        fun value(): String
    }

}
