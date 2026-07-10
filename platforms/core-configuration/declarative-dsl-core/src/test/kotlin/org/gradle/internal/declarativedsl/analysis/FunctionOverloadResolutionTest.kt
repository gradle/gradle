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

package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.internal.declarativedsl.assertIs
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.Assert
import org.junit.Test

class FunctionOverloadResolutionTest {

    @Test
    fun `selects the most specific overload by one parameter`() {
        val schema = schemaFromTypes(TopLevel::class)

        Assert.assertTrue(
            schema.resolve(
                """
                fFoo(foo())
                fFoo(subFoo())
                """.trimIndent()
            ).errors.isEmpty()
        )
    }

    @Test
    fun `selects the most specific overload by two parameters`() {
        val schema = schemaFromTypes(TopLevel::class)

        Assert.assertTrue(
            schema.resolve(
                """
                fFooBar(foo(), bar())
                fFooBar(foo(), subBar())
                fFooBar(subFoo(), bar())
                fFooBar(subFoo(), subBar())
                """.trimIndent()
            ).errors.isEmpty()
        )
    }

    @Test
    fun `fails to disambiguate overloads when there is no single most specific candidate`() {
        val schema = schemaFromTypes(TopLevel::class)

        val errorReason = assertIs<ErrorReason.AmbiguousFunctions>(
            schema.resolve("fFoo(bothSubFoo())").errors.single().errorReason
        )
        Assert.assertEquals(2, errorReason.functions.size)
    }

    interface Foo
    interface SubFoo : Foo
    interface OtherSubFoo : Foo
    interface BothSubFoo : SubFoo, OtherSubFoo

    interface Bar
    interface SubBar : Bar

    interface TopLevel {
        @Adding
        fun fFoo(foo: Foo)

        @Adding
        fun fFoo(subFoo: SubFoo)

        @Adding
        fun fFoo(otherSubFoo: OtherSubFoo)

        @Adding
        fun fFooBar(foo: Foo, bar: Bar)

        @Adding
        fun fFooBar(subFoo: SubFoo, bar: Bar)

        @Adding
        fun fFooBar(foo: Foo, bar: SubBar)

        @Adding
        fun fFooBar(subFoo: SubFoo, bar: SubBar)

        fun foo(): Foo
        fun subFoo(): SubFoo
        fun bothSubFoo(): BothSubFoo

        fun bar(): Bar
        fun subBar(): SubBar
    }
}
