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
import org.gradle.declarative.dsl.model.annotations.Restricted
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual.equalTo
import kotlin.test.Test
import kotlin.test.assertFailsWith


object SchemeExtractionErrorTest {

    @Test
    fun `data type ref conversion of getter return type fails`() {
        val exception = assertFailsWith<IllegalStateException>(
            block = { schemaFromTypes(ReceiverGetterReturn::class, listOf(ReceiverGetterReturn::class)) }
        )
        assertThat(
            exception.message,
            equalTo("Conversion to data types failed for return type of ReceiverGetterReturn.getList: kotlin.collections.List<kotlin.String?>?")
        )
    }

    abstract class ReceiverGetterReturn {
        @Restricted
        abstract fun getList(): List<String?>?

    }

    @Test
    fun `data type ref conversion of property return type fails`() {
        val exception = assertFailsWith<IllegalStateException>(
            block = { schemaFromTypes(ReceiverPropertyReturn::class, listOf(ReceiverPropertyReturn::class)) }
        )
        assertThat(
            exception.message,
            equalTo("Conversion to data types failed for return type of ReceiverPropertyReturn.x: kotlin.collections.List<kotlin.String>")
        )
    }

    abstract class ReceiverPropertyReturn {

        @get:Restricted
        var x: List<String> = emptyList()

    }

    @Test
    fun `data type ref conversion of function param type fails`() {
        val exception = assertFailsWith<IllegalStateException>(
            block = {
                schemaFromTypes(
                    ReceiverFunctionParam::class,
                    listOf(ListProperty::class, ReceiverFunctionParam::class)
                )
            }
        )
        assertThat(
            exception.message,
            equalTo("Conversion to data types failed for parameter type of function ReceiverFunctionParam.size: org.gradle.api.provider.ListProperty<kotlin.String>")
        )
    }

    abstract class ReceiverFunctionParam {

        @Restricted
        abstract fun size(list: ListProperty<String>): Int

    }

    @Test
    fun `data type ref conversion of function return type fails`() {
        val exception = assertFailsWith<IllegalStateException>(
            block = {
                schemaFromTypes(
                    ReceiverFunctionReturn::class,
                    listOf(ListProperty::class, ReceiverFunctionReturn::class)
                )
            }
        )
        assertThat(
            exception.message,
            equalTo("Conversion to data types failed for return type of ReceiverFunctionReturn.mood: org.gradle.api.provider.ListProperty<kotlin.String>")
        )
    }

    @Test
    fun `type used not in schema scope`() {
        val exception = assertFailsWith<IllegalStateException>(
            block = {
                schemaFromTypes(
                    ReceiverFunctionReturn::class,
                    listOf(ReceiverFunctionReturn::class)
                )
            }
        )
        assertThat(
            exception.message,
            equalTo("Type used in function ReceiverFunctionReturn.mood is not in schema scope: org.gradle.api.provider.ListProperty<kotlin.String>")
        )
    }

    abstract class ReceiverFunctionReturn {

        @Restricted
        abstract fun mood(): ListProperty<String>

    }

}
