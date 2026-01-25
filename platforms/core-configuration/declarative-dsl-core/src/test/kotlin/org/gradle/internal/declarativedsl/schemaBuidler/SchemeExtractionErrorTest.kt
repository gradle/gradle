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
import org.gradle.internal.declarativedsl.analysis.DeclarativeDslInterpretationException
import org.gradle.internal.declarativedsl.assertFailsWith
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File


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
                "  in member 'fun org.gradle.internal.declarativedsl.schemaBuidler.SchemeExtractionErrorTest.ReceiverGetterReturn<T>.getList(): kotlin.collections.MutableList<in kotlin.String>'\n" +
                "  in class 'org.gradle.internal.declarativedsl.schemaBuidler.SchemeExtractionErrorTest.ReceiverGetterReturn'",
            exception.message
        )
    }

    @Suppress("unused")
    abstract class ReceiverGetterReturn<T> {
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

    @Suppress("unused")
    abstract class ReceiverFunctionReturn {

        abstract fun mood(): ListProperty<in String>
    }

    interface UsageOfTypeOutsideSchema {
        fun configure(fn: File.() -> Unit)
    }
}
