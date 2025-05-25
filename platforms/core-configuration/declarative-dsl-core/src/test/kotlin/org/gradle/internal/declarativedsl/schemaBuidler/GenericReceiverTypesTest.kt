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

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.Configuring
import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.analysis.ResolutionError
import org.gradle.internal.declarativedsl.analysis.SchemaTypeRefContext
import org.gradle.internal.declarativedsl.assertFailsWith
import org.gradle.internal.declarativedsl.assertIs
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.schemaBuilder.DeclarativeDslSchemaBuildingException
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.gradle.internal.declarativedsl.schemaUtils.singleFunctionNamed
import org.gradle.internal.declarativedsl.schemaUtils.typeFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenericReceiverTypesTest {
    @Test
    fun `the schema does not contain the generic supertype but contains the subtypes with properly substituted types`() {
        assertNull(schema.dataClassTypesByFqName[DefaultFqName.parse(GenericSuperType::class.qualifiedName!!)])

        with(SchemaTypeRefContext(schema)) {
            val subIntString = schema.typeFor<GenericSubtypeIntString>()
            assertIs<DataType.IntDataType>(resolveRef(subIntString.singleFunctionNamed("t1").function.returnValueType))
            assertIs<DataType.StringDataType>(resolveRef(subIntString.singleFunctionNamed("t2").function.returnValueType))

            val subStringBoolean = schema.typeFor<GenericSubtypeStringBoolean>()
            assertIs<DataType.StringDataType>(resolveRef(subStringBoolean.singleFunctionNamed("t1").function.returnValueType))
            assertIs<DataType.BooleanDataType>(resolveRef(subStringBoolean.singleFunctionNamed("t2").function.returnValueType))
        }
    }

    @Test
    fun `the resolver accepts the imported subtype of the generic supertype`() {
        val result = schema.resolve(
            """
            sub1 { // GenericSubtypeIntString: GenericSupertype<T1: Int, T2: String>
                addT1(123) // T1 = Int, so 123 should be accepted as T1
                str = t2() // T2 = String, so t2(): String should be assignable to String
                addStr(t2()) // addStr is a member of GenericSubtype, it should be available
                nested {
                    nestedStr = t2()
                }
            }

            sub2 { // GenericSubtypeStringBoolean: GenericSupertype<T1: String, T2: Boolean>
                addT1("one") // T1 = Int, so 123 should be accepted as T1
                bool = t2() // T2 = Boolean, so t2(): Boolean should be assignable to Boolean
                nested {
                    nestedStr = t1()
                }
            }
            """.trimIndent()
        )

        assertEquals(emptyList<ResolutionError>(), result.errors)
    }

    @Test
    fun `fails importing a schema that configures a generic type`() {
        assertFailsWith<DeclarativeDslSchemaBuildingException> {
            schemaFromTypes(
                SchemaConfiguringGenericType::class,
                listOf(SchemaConfiguringGenericType::class, GenericSubtypeIntString::class, GenericSubtypeStringBoolean::class, GenericSuperType::class, Nested::class)
            )
        }.apply {
            assertEquals("""
                Cannot use the parameterized class 'class org.gradle.internal.declarativedsl.schemaBuidler.GenericReceiverTypesTest${'$'}GenericSuperType' as a configurable type
                  in configured type 'org.gradle.internal.declarativedsl.schemaBuidler.GenericReceiverTypesTest.GenericSuperType<kotlin.Int, kotlin.String>'
                  in member 'fun org.gradle.internal.declarativedsl.schemaBuidler.GenericReceiverTypesTest.SchemaConfiguringGenericType.sup(org.gradle.internal.declarativedsl.schemaBuidler.GenericReceiverTypesTest.GenericSuperType<kotlin.Int, kotlin.String>.() -> kotlin.Unit): kotlin.Unit'
                  in class 'org.gradle.internal.declarativedsl.schemaBuidler.GenericReceiverTypesTest.SchemaConfiguringGenericType'
            """.trimIndent(), message)
        }
    }

    @Test
    fun `fails importing a schema that configures a type variable`() {
        assertFailsWith<DeclarativeDslSchemaBuildingException> {
            schemaFromTypes(
                SchemaConfiguringTypeArgument::class,
                listOf(SchemaConfiguringTypeArgument::class)
            )
        }.apply {
            assertEquals("""
                Illegal usage of a type parameter
                  in configured type 'T'
                  in member 'fun org.gradle.internal.declarativedsl.schemaBuidler.GenericReceiverTypesTest.SchemaConfiguringTypeArgument.sup(T.() -> kotlin.Unit): T'
                  in class 'org.gradle.internal.declarativedsl.schemaBuidler.GenericReceiverTypesTest.SchemaConfiguringTypeArgument'
            """.trimIndent(), message)
        }
    }


    private val schema get() = schemaFromTypes(Schema::class, listOf(Schema::class, GenericSubtypeIntString::class, GenericSubtypeStringBoolean::class, GenericSuperType::class, Nested::class))

    @Suppress("unused")
    interface Schema {
        @Configuring
        fun sub1(configure: GenericSubtypeIntString.() -> Unit)

        @Configuring
        fun sub2(configure: GenericSubtypeStringBoolean.() -> Unit)
    }

    /**
     * This mimics the pattern found in some plugins: there is a
     */
    @Suppress("unused")
    interface GenericSuperType<T1, T2> {
        @get:Restricted
        var int: Int

        @get:Restricted
        var str: String

        @get:Restricted
        var bool: Boolean

        @Adding
        fun addT1(t1: T1)

        @Restricted
        fun t1(): T1

        @Restricted
        fun t2(): T2

        @Configuring
        fun nested(configure: Nested.() -> Unit)
    }

    @Suppress("unused")
    interface GenericSubtypeIntString : GenericSuperType<Int, String> {
        @Adding
        fun addStr(string: String)
    }

    @Suppress("unused")
    interface GenericSubtypeStringBoolean : GenericSuperType<String, Boolean> {
        @Adding
        fun addBool(boolean: Boolean)
    }

    @Suppress("unused")
    interface Nested {
        @get:Restricted
        var nestedStr: String
    }

    @Suppress("unused")
    interface SchemaConfiguringGenericType {
        @Configuring
        fun sup(configure: GenericSuperType<Int, String>.() -> Unit)
    }

    @Suppress("unused")
    interface SchemaConfiguringTypeArgument {
        @Adding
        fun <T> sup(configure: T.() -> Unit): T
    }
}
