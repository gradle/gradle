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


class SinceAndIncubatingRulesKotlinTest : AbstractBinaryCompatibilityTest() {

    private
    val publicKotlinMembers = """

        fun foo() {}

        val bar: String = "bar"

        var bazar = "bazar"

        fun String.fooExt() {}

        fun Int.fooExt() {}

        val String.barExt: String
            get() = "bar"

        var Int.bazarExt: String
            get() = "bar"
            set(value) = Unit

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
        val String.barExt: String
            get() = "bar"

        /**
         * @since 2.0
         */
        @get:Incubating
        @set:Incubating
        var Int.bazarExt: String
            get() = "bar"
            set(value) = Unit

    """

    @Test
    fun `new top-level kotlin members`() {

        checkNotBinaryCompatibleKotlin(v2 = """

           $publicKotlinMembers

            const val cathedral = "cathedral"

        """).apply {

            assertHasNoInformation()
            assertHasNoWarning()
            assertHasErrors(

                // Kotlin file-facade classes are ignored by the @since rule
                listOf("Class com.example.SourceKt: Is not annotated with @Incubating."),

                added("Field", "cathedral"),
                added("Method", "SourceKt.foo()"),
                added("Method", "SourceKt.fooExt(java.lang.String)"),
                added("Method", "SourceKt.fooExt(int)"),
                added("Method", "SourceKt.getBar()"),
                added("Method", "SourceKt.getBarExt(java.lang.String)"),
                added("Method", "SourceKt.getBazar()"),
                added("Method", "SourceKt.getBazarExt(int)"),
                added("Method", "SourceKt.setBazar(java.lang.String)"),
                added("Method", "SourceKt.setBazarExt(int,java.lang.String)")
            )
        }

        checkNotBinaryCompatibleKotlin(v2 = """

           $annotatedKotlinMembers

            /**
             * @since 2.0
             */
            @field:Incubating
            const val cathedral = "cathedral"

        """).apply {

            // TODO:kotlin-dsl add @file:Incubating once new wrapper with @Incubating & @Target.TYPE
            assertHasErrors("Class com.example.SourceKt: Is not annotated with @Incubating.")
            assertHasNoWarning()
            assertHasInformation(
                newApi("Field", "cathedral"),
                newApi("Method", "SourceKt.foo()"),
                newApi("Method", "SourceKt.fooExt(java.lang.String)"),
                newApi("Method", "SourceKt.fooExt(int)"),
                newApi("Method", "SourceKt.getBar()"),
                newApi("Method", "SourceKt.getBarExt(java.lang.String)"),
                newApi("Method", "SourceKt.getBazar()"),
                newApi("Method", "SourceKt.getBazarExt(int)"),
                newApi("Method", "SourceKt.setBazar(java.lang.String)"),
                newApi("Method", "SourceKt.setBazarExt(int,java.lang.String)")
            )
        }
    }

    @Test
    fun `new top-level kotlin types`() {

        // Singleton INSTANCE fields of `object`s are public

        checkNotBinaryCompatibleKotlin(v2 = """

            interface Foo

            class Bar

            enum class Bazar

            object Cathedral

        """).apply {

            assertHasNoInformation()
            assertHasNoWarning()
            assertHasErrors(
                added("Class", "Bar"),
                added("Constructor", "Bar()"),
                added("Class", "Bazar"),
                added("Method", "Bazar.valueOf(java.lang.String)"),
                added("Method", "Bazar.values()"),
                added("Class", "Cathedral"),
                added("Field", "INSTANCE"),
                added("Class", "Foo")
            )
        }

        checkBinaryCompatibleKotlin(v2 = """

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
                newApi("Class", "Bar"),
                newApi("Class", "Bazar"),
                newApi("Method", "Bazar.valueOf(java.lang.String)"),
                newApi("Method", "Bazar.values()"),
                newApi("Class", "Cathedral"),
                newApi("Field", "INSTANCE"),
                newApi("Class", "Foo")
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

        checkNotBinaryCompatibleKotlin(v1 = baseline, v2 = """

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
                added("Method", "Bar.foo()"),
                added("Method", "Bar.fooExt(java.lang.String)"),
                added("Method", "Bar.fooExt(int)"),
                added("Method", "Bar.getBar()"),
                added("Method", "Bar.getBarExt(java.lang.String)"),
                added("Method", "Bar.getBazar()"),
                added("Method", "Bar.getBazarExt(int)"),
                added("Method", "Bar.setBazar(java.lang.String)"),
                added("Method", "Bar.setBazarExt(int,java.lang.String)"),
                added("Constructor", "Bar(java.lang.String)"),
                added("Method", "Foo.foo()")
            )
        }

        checkBinaryCompatibleKotlin(v1 = baseline, v2 = """

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
                newApi("Method", "Bar.foo()"),
                newApi("Method", "Bar.fooExt(java.lang.String)"),
                newApi("Method", "Bar.fooExt(int)"),
                newApi("Method", "Bar.getBar()"),
                newApi("Method", "Bar.getBarExt(java.lang.String)"),
                newApi("Method", "Bar.getBazar()"),
                newApi("Method", "Bar.getBazarExt(int)"),
                newApi("Method", "Bar.setBazar(java.lang.String)"),
                newApi("Method", "Bar.setBazarExt(int,java.lang.String)"),
                newApi("Method", "Foo.foo()")
            )
        }
    }
}
