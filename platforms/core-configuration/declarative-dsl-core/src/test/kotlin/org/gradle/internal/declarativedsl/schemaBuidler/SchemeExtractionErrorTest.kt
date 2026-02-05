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

import org.gradle.api.provider.ListProperty
import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition
import org.gradle.declarative.dsl.model.annotations.VisibleInDefinition
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.analysis.DeclarativeDslInterpretationException
import org.gradle.internal.declarativedsl.assertFailsWith
import org.gradle.internal.declarativedsl.schemaBuilder.DeclarativeDslSchemaBuildingException
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaFailureReporter
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaResult
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.gradle.internal.declarativedsl.schemaUtils.findFunctionFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows


class SchemeExtractionErrorTest {

    @Test
    fun `data type ref conversion of getter return type fails`() {
        val exception = assertFailsWith<DeclarativeDslInterpretationException>(
            block = { schemaFromTypes(ReceiverGetterReturn::class, listOf(ReceiverGetterReturn::class)) }
        )
        assertEquals(
            "Illegal 'IN' variance\n" +
                "  in type argument 'in kotlin.String'\n" +
                "  in return value type 'kotlin.collections.List<in kotlin.String>'\n" +
                "  in member 'fun org.gradle.internal.declarativedsl.schemaBuidler.SchemeExtractionErrorTest.ReceiverGetterReturn.getList(): kotlin.collections.MutableList<in kotlin.String>'\n" +
                "  in class 'org.gradle.internal.declarativedsl.schemaBuidler.SchemeExtractionErrorTest.ReceiverGetterReturn'",
            exception.message
        )
    }

    @Suppress("unused")
    abstract class ReceiverGetterReturn {
        abstract fun getList(): MutableList<in String>
    }

    @Test
    fun `data type ref conversion of property return type fails`() {
        val exception = assertFailsWith<DeclarativeDslInterpretationException>(
            block = { schemaFromTypes(ReceiverPropertyReturn::class, listOf(ReceiverPropertyReturn::class)) }
        )
        assertEquals(
            "Illegal 'OUT' variance\n" +
                "  in type argument 'out kotlin.String'\n" +
                "  in return value type 'kotlin.collections.List<out kotlin.String>'\n" + // (List instead of MutableList because type.classifier is lossy in collection mutability)
                "  in member 'var org.gradle.internal.declarativedsl.schemaBuidler.SchemeExtractionErrorTest.ReceiverPropertyReturn.x: kotlin.collections.MutableList<out kotlin.String>'\n" +
                "  in class 'org.gradle.internal.declarativedsl.schemaBuidler.SchemeExtractionErrorTest.ReceiverPropertyReturn'",
            exception.message,
        )
    }

    abstract class ReceiverPropertyReturn {

        var x: MutableList<out String> = mutableListOf()
    }

    @Test
    fun `data type ref conversion of function param type fails`() {
        val exception = assertFailsWith<DeclarativeDslInterpretationException>(
            block = {
                schemaFromTypes(
                    ReceiverFunctionParam::class,
                    listOf(ListProperty::class, ReceiverFunctionParam::class),
                )
            }
        )
        assertEquals(
            "Illegal 'IN' variance\n" +
                "  in type argument 'in kotlin.String'\n" +
                "  in parameter 'list'\n" +
                "  in member 'fun org.gradle.internal.declarativedsl.schemaBuidler.SchemeExtractionErrorTest.ReceiverFunctionParam.size(org.gradle.api.provider.ListProperty<in kotlin.String>): kotlin.Int'\n" +
                "  in class 'org.gradle.internal.declarativedsl.schemaBuidler.SchemeExtractionErrorTest.ReceiverFunctionParam'",
            exception.message,
        )
    }

    abstract class ReceiverFunctionParam {
        abstract fun size(list: ListProperty<in String>): Int
    }

    @Test
    fun `data type ref conversion of function return type fails`() {
        val exception = assertFailsWith<DeclarativeDslInterpretationException>(
            block = {
                schemaFromTypes(
                    ReceiverFunctionReturn::class,
                    listOf(ListProperty::class, ReceiverFunctionReturn::class),
                )
            }
        )
        assertEquals(
            "Illegal 'IN' variance\n" +
                "  in type argument 'in kotlin.String'\n" +
                "  in return value type 'org.gradle.api.provider.ListProperty<in kotlin.String>'\n" +
                "  in member 'fun org.gradle.internal.declarativedsl.schemaBuidler.SchemeExtractionErrorTest.ReceiverFunctionReturn.mood(): org.gradle.api.provider.ListProperty<in kotlin.String>'\n" +
                "  in class 'org.gradle.internal.declarativedsl.schemaBuidler.SchemeExtractionErrorTest.ReceiverFunctionReturn'",
            exception.message,
        )
    }

    @Test
    fun `schema builder passes multiple failures from different stages to the reporter`() {
        assertThrows<DeclarativeDslSchemaBuildingException> { schemaFromTypes(MultipleInvalidMembers::class) }
            .run {
                assertEquals(
                    """
                    |Multiple failures in building the declarative schema:
                    |
                    |* Conflicting annotations: @VisibleInDefinition and @HiddenInDefinition are present
                    |  in class 'org.gradle.internal.declarativedsl.schemaBuidler.SchemeExtractionErrorTest.VisibleAndHidden'
                    |
                    |* Conflicting annotations: @VisibleInDefinition and @HiddenInDefinition are present
                    |  in member 'var org.gradle.internal.declarativedsl.schemaBuidler.SchemeExtractionErrorTest.MultipleInvalidMembers.s: kotlin.String'
                    |  in class 'org.gradle.internal.declarativedsl.schemaBuidler.SchemeExtractionErrorTest.MultipleInvalidMembers'
                    |
                    |* Unsupported property declaration: nullable read-only property
                    |  in member 'val org.gradle.internal.declarativedsl.schemaBuidler.SchemeExtractionErrorTest.MultipleInvalidMembers.y: kotlin.Int?'
                    |  in class 'org.gradle.internal.declarativedsl.schemaBuidler.SchemeExtractionErrorTest.MultipleInvalidMembers'
                    |
                    |* Illegal 'IN' variance
                    |  in type argument 'in kotlin.String'
                    |  in parameter 'x'
                    |  in member 'fun org.gradle.internal.declarativedsl.schemaBuidler.SchemeExtractionErrorTest.MultipleInvalidMembers.f(org.gradle.api.provider.ListProperty<in kotlin.String>): kotlin.Int'
                    |  in class 'org.gradle.internal.declarativedsl.schemaBuidler.SchemeExtractionErrorTest.MultipleInvalidMembers'
                    """.trimMargin(),
                    message
                )
            }
    }

    @Test
    fun `schema builder passes the partial schema to the reporter`() {
        var schema: AnalysisSchema? = null

        val reporter = object : SchemaFailureReporter {
            override fun report(partialSchema: AnalysisSchema, failures: List<SchemaResult.Failure>) {
                schema = partialSchema
                assertTrue(failures.isNotEmpty())
            }
        }

        schemaFromTypes(MultipleInvalidMembers::class, failureReporter = reporter)

        assertNotNull(schema)
        assertNotNull(schema.findFunctionFor(MultipleInvalidMembers::thisOneIsValid))
        assertNull(schema.findFunctionFor(MultipleInvalidMembers::f))
    }


    @Suppress("unused")
    abstract class ReceiverFunctionReturn {

        abstract fun mood(): ListProperty<in String>
    }

    interface MultipleInvalidMembers {
        fun f(x: ListProperty<in String>): Int

        val y: Int?

        @get:HiddenInDefinition
        @get:VisibleInDefinition
        var s: String

        val visibleAndHidden: VisibleAndHidden

        fun thisOneIsValid(): Int
    }

    @VisibleInDefinition
    @HiddenInDefinition
    interface VisibleAndHidden {
        var x: Int
    }
}
