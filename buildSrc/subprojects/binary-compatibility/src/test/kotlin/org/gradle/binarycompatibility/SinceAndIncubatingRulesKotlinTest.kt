/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.binarycompatibility

import org.junit.Test


class SinceAndIncubatingRulesKotlinTest : AbstractKotlinBinaryCompatibilityTest() {

    private
    val internalKotlinMembers = """

        internal fun foo() {}

        internal val bar: String = "bar"

        internal var bazar = "bazar"

        internal fun String.fooExt() {}

        internal fun Int.fooExt() {}

        internal val String.barExt
            get() = "bar"

    """

    private
    val publicKotlinMembers = """

        fun foo() {}

        val bar: String = "bar"

        var bazar = "bazar"

        fun String.fooExt() {}

        fun Int.fooExt() {}

        val String.barExt
            get() = "bar"

    """

    private
    val annotatedKotlinMembers = """

        /**
         * @since 2.0
         */
        @Incubating
        fun foo() {}

        /**
         * @since 2.0
         */
        @get:Incubating
        val bar: String = "bar"

        /**
         * @since 2.0
         */
        @get:Incubating
        @set:Incubating
        var bazar = "bazar"

        /**
         * @since 2.0
         */
        @Incubating
        fun String.fooExt() {}

        /**
         * @since 2.0
         */
        @Incubating
        fun Int.fooExt() {}

        /**
         * @since 2.0
         */
        @get:Incubating
        val String.barExt
            get() = "bar"

    """

    @Test
    fun `new top-level kotlin members`() {

        checkNotBinaryCompatible(v2 = """

           $publicKotlinMembers

            const val cathedral = "cathedral"

        """).apply {

            assertHasNoInformation()
            assertHasNoWarning()
            assertHasErrors(
                "Field cathedral: Is not annotated with @Incubating.",
                "Field cathedral: Is not annotated with @since 2.0.",
                "Method com.example.SourceKt.foo(): Is not annotated with @Incubating.",
                "Method com.example.SourceKt.foo(): Is not annotated with @since 2.0.",
                "Method com.example.SourceKt.fooExt(java.lang.String): Is not annotated with @Incubating.",
                "Method com.example.SourceKt.fooExt(java.lang.String): Is not annotated with @since 2.0.",
                "Method com.example.SourceKt.fooExt(int): Is not annotated with @Incubating.",
                "Method com.example.SourceKt.fooExt(int): Is not annotated with @since 2.0.",
                "Method com.example.SourceKt.getBar(): Is not annotated with @Incubating.",
                "Method com.example.SourceKt.getBar(): Is not annotated with @since 2.0.",
                "Method com.example.SourceKt.getBarExt(java.lang.String): Is not annotated with @Incubating.",
                "Method com.example.SourceKt.getBarExt(java.lang.String): Is not annotated with @since 2.0.",
                "Method com.example.SourceKt.getBazar(): Is not annotated with @Incubating.",
                "Method com.example.SourceKt.getBazar(): Is not annotated with @since 2.0.",
                "Method com.example.SourceKt.setBazar(java.lang.String): Is not annotated with @Incubating.",
                "Method com.example.SourceKt.setBazar(java.lang.String): Is not annotated with @since 2.0."
            )
        }

        checkBinaryCompatible(v2 = """

           $annotatedKotlinMembers

            /**
             * @since 2.0
             */
            @field:Incubating
            const val cathedral = "cathedral"

        """).apply {

            assertHasNoError()
            assertHasNoWarning()
            assertHasInformation(
                "Field cathedral: New public API in 2.0 (@Incubating)",
                "Method com.example.SourceKt.foo(): New public API in 2.0 (@Incubating)",
                "Method com.example.SourceKt.fooExt(java.lang.String): New public API in 2.0 (@Incubating)",
                "Method com.example.SourceKt.fooExt(int): New public API in 2.0 (@Incubating)",
                "Method com.example.SourceKt.getBar(): New public API in 2.0 (@Incubating)",
                "Method com.example.SourceKt.getBarExt(java.lang.String): New public API in 2.0 (@Incubating)",
                "Method com.example.SourceKt.getBazar(): New public API in 2.0 (@Incubating)",
                "Method com.example.SourceKt.setBazar(java.lang.String): New public API in 2.0 (@Incubating)"
            )
        }
    }

    @Test
    fun `new top-level kotlin types`() {

        // Singleton INSTANCE fields of `object`s are public

        checkNotBinaryCompatible(v2 = """

            interface Foo

            class Bar

            enum class Bazar

            object Cathedral

        """).apply {

            assertHasNoInformation()
            assertHasNoWarning()
            assertHasErrors(
                "Class com.example.Bar: Is not annotated with @Incubating.",
                "Class com.example.Bar: Is not annotated with @since 2.0.",
                "Constructor com.example.Bar(): Is not annotated with @Incubating.",
                "Constructor com.example.Bar(): Is not annotated with @since 2.0.",
                "Class com.example.Bazar: Is not annotated with @Incubating.",
                "Class com.example.Bazar: Is not annotated with @since 2.0.",
                "Method com.example.Bazar.valueOf(java.lang.String): Is not annotated with @Incubating.",
                "Method com.example.Bazar.valueOf(java.lang.String): Is not annotated with @since 2.0.",
                "Method com.example.Bazar.values(): Is not annotated with @Incubating.",
                "Method com.example.Bazar.values(): Is not annotated with @since 2.0.",
                "Class com.example.Cathedral: Is not annotated with @Incubating.",
                "Class com.example.Cathedral: Is not annotated with @since 2.0.",
                "Field INSTANCE: Is not annotated with @Incubating.",
                "Field INSTANCE: Is not annotated with @since 2.0.",
                "Class com.example.Foo: Is not annotated with @Incubating.",
                "Class com.example.Foo: Is not annotated with @since 2.0."
            )
        }

        checkBinaryCompatible(v2 = """

            /**
             * @since 2.0
             */
            @Incubating
            interface Foo

            /**
             * @since 2.0
             */
            @Incubating
            class Bar

            /**
             * @since 2.0
             */
            @Incubating
            enum class Bazar

            /**
             * @since 2.0
             */
            @Incubating
            object Cathedral

        """).apply {

            assertHasNoError()
            assertHasNoWarning()
            assertHasInformation(
                "Class com.example.Bar: New public API in 2.0 (@Incubating)",
                "Class com.example.Bazar: New public API in 2.0 (@Incubating)",
                "Method com.example.Bazar.valueOf(java.lang.String): New public API in 2.0 (@Incubating)",
                "Method com.example.Bazar.values(): New public API in 2.0 (@Incubating)",
                "Class com.example.Cathedral: New public API in 2.0 (@Incubating)",
                "Field INSTANCE: New public API in 2.0 (@Incubating)",
                "Class com.example.Foo: New public API in 2.0 (@Incubating)"
            )
        }
    }

    @Test
    fun `new kotlin types members`() {

        val baseline = """

            /**
             * @since 1.0
             */
            interface Foo

            /**
             * @since 1.0
             */
            class Bar()

        """

        checkNotBinaryCompatible(v1 = baseline, v2 = """

            /**
             * @since 1.0
             */
            interface Foo : AutoCloseable {
                fun foo()
                override fun close()
            }

            /**
             * @since 1.0
             */
            class Bar() {

                constructor(bar: String) : this()

                $publicKotlinMembers
            }

        """).apply {

            assertHasNoInformation()
            assertHasNoWarning()
            assertHasErrors(
                "Method com.example.Bar.foo(): Is not annotated with @Incubating.",
                "Method com.example.Bar.foo(): Is not annotated with @since 2.0.",
                "Method com.example.Bar.fooExt(java.lang.String): Is not annotated with @Incubating.",
                "Method com.example.Bar.fooExt(java.lang.String): Is not annotated with @since 2.0.",
                "Method com.example.Bar.fooExt(int): Is not annotated with @Incubating.",
                "Method com.example.Bar.fooExt(int): Is not annotated with @since 2.0.",
                "Method com.example.Bar.getBar(): Is not annotated with @Incubating.",
                "Method com.example.Bar.getBar(): Is not annotated with @since 2.0.",
                "Method com.example.Bar.getBarExt(java.lang.String): Is not annotated with @Incubating.",
                "Method com.example.Bar.getBarExt(java.lang.String): Is not annotated with @since 2.0.",
                "Method com.example.Bar.getBazar(): Is not annotated with @Incubating.",
                "Method com.example.Bar.getBazar(): Is not annotated with @since 2.0.",
                "Method com.example.Bar.setBazar(java.lang.String): Is not annotated with @Incubating.",
                "Method com.example.Bar.setBazar(java.lang.String): Is not annotated with @since 2.0.",
                "Constructor com.example.Bar(java.lang.String): Is not annotated with @Incubating.",
                "Constructor com.example.Bar(java.lang.String): Is not annotated with @since 2.0.",
                "Method com.example.Foo.foo(): Is not annotated with @Incubating.",
                "Method com.example.Foo.foo(): Is not annotated with @since 2.0."
            )
        }

        checkBinaryCompatible(v1 = baseline, v2 = """

            /**
             * @since 1.0
             */
            interface Foo {

                /**
                 * @since 2.0
                 */
                @Incubating
                fun foo()
            }

            /**
             * @since 1.0
             */
            class Bar() {

                /**
                 * @since 2.0
                 */
                @Incubating
                constructor(bar: String) : this()

                $annotatedKotlinMembers
            }

        """).apply {

            assertHasNoError()
            assertHasNoWarning()
            assertHasInformation(
                "Method com.example.Bar.foo(): New public API in 2.0 (@Incubating)",
                "Method com.example.Bar.fooExt(java.lang.String): New public API in 2.0 (@Incubating)",
                "Method com.example.Bar.fooExt(int): New public API in 2.0 (@Incubating)",
                "Method com.example.Bar.getBar(): New public API in 2.0 (@Incubating)",
                "Method com.example.Bar.getBarExt(java.lang.String): New public API in 2.0 (@Incubating)",
                "Method com.example.Bar.getBazar(): New public API in 2.0 (@Incubating)",
                "Method com.example.Bar.setBazar(java.lang.String): New public API in 2.0 (@Incubating)",
                "Method com.example.Foo.foo(): New public API in 2.0 (@Incubating)"
            )
        }
    }
}
