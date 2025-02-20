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
import org.gradle.declarative.dsl.model.annotations.Configuring
import org.gradle.internal.declarativedsl.assertFailsWith
import org.gradle.internal.declarativedsl.schemaBuilder.DeclarativeDslSchemaBuildingException
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.Assert
import org.junit.Test

class AddingAndConfiguringFunctionTest {

    @Test
    fun `reports an error on Unit-returning @Adding functions with configuring lambdas`() {
        assertFailsWith<DeclarativeDslSchemaBuildingException> {
            schemaFromTypes(UnitReturningAddingFunctionWithLambda::class, listOf(UnitReturningAddingFunctionWithLambda::class))
        }.apply {
            Assert.assertEquals(
                """
                The @Adding function can only accept a lambda if it returns the configured object
                  in member 'fun org.gradle.internal.declarativedsl.schemaBuidler.AddingAndConfiguringFunctionTest.UnitReturningAddingFunctionWithLambda.add(kotlin.Any.() -> kotlin.Unit): kotlin.Unit'
                  in class 'org.gradle.internal.declarativedsl.schemaBuidler.AddingAndConfiguringFunctionTest.UnitReturningAddingFunctionWithLambda'
                """.trimIndent(),
                message
            )
        }
    }

    @Test
    fun `reports an error on @Adding functions that return a type that is not a subtype of the configured type`() {
        assertFailsWith<DeclarativeDslSchemaBuildingException> {
            schemaFromTypes(InconsistentlyTypedAddingFunction::class, listOf(InconsistentlyTypedAddingFunction::class))
        }.apply {
            Assert.assertEquals(
                """
                The @Adding function must return a subtype of its configured type 'kotlin.String'
                  in member 'fun org.gradle.internal.declarativedsl.schemaBuidler.AddingAndConfiguringFunctionTest.InconsistentlyTypedAddingFunction.add(kotlin.String.() -> kotlin.Unit): kotlin.Int'
                  in class 'org.gradle.internal.declarativedsl.schemaBuidler.AddingAndConfiguringFunctionTest.InconsistentlyTypedAddingFunction'
                """.trimIndent(),
                message
            )
        }
    }

    @Test
    fun `reports configuring functions with no lambda`() {
        assertFailsWith<DeclarativeDslSchemaBuildingException> {
            schemaFromTypes(ConfiguringFunctionWithNoLambda::class, listOf(ConfiguringFunctionWithNoLambda::class))
        }.apply {
            Assert.assertEquals(
                """
                The @Configuring function must accept a function object as the last parameter
                  in member 'fun org.gradle.internal.declarativedsl.schemaBuidler.AddingAndConfiguringFunctionTest.ConfiguringFunctionWithNoLambda.configure(kotlin.Int): kotlin.Unit'
                  in class 'org.gradle.internal.declarativedsl.schemaBuidler.AddingAndConfiguringFunctionTest.ConfiguringFunctionWithNoLambda'
                """.trimIndent(),
                message
            )
        }
    }

    @Test
    fun `reports configuring functions that return an unrelated type`() {
        assertFailsWith<DeclarativeDslSchemaBuildingException> {
            schemaFromTypes(ConfiguringFunctionReturningUnrelatedType::class, listOf(ConfiguringFunctionReturningUnrelatedType::class))
        }.apply {
            Assert.assertEquals(
                """
                The @Configuring function can only return the configured type 'kotlin.Any' or no object (Unit, void)
                  in member 'fun org.gradle.internal.declarativedsl.schemaBuidler.AddingAndConfiguringFunctionTest.ConfiguringFunctionReturningUnrelatedType.configure(kotlin.Any.() -> kotlin.Unit): kotlin.String'
                  in class 'org.gradle.internal.declarativedsl.schemaBuidler.AddingAndConfiguringFunctionTest.ConfiguringFunctionReturningUnrelatedType'
                """.trimIndent(),
                message
            )
        }
    }

    interface UnitReturningAddingFunctionWithLambda {
        @Adding
        fun add(configure: Any.() -> Unit)
    }

    interface InconsistentlyTypedAddingFunction {
        @Adding
        fun add(configure: String.() -> Unit): Int
    }

    interface ConfiguringFunctionWithNoLambda {
        @Configuring
        fun configure(int: Int)
    }

    interface ConfiguringFunctionReturningUnrelatedType {
        @Configuring
        fun configure(configure: Any.() -> Unit): String
    }
}
