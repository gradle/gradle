/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.binarycompatibility

import org.junit.Test


class SinceAndIncubatingRulesKotlinTest : AbstractBinaryCompatibilityTest() {

    private
    val publicKotlinMembers = """

        fun foo() {}

        val bar: String = "bar"

        val bool: Boolean = true

        val isBool: Boolean = true

        var bazar = "bazar"

        var bazool: Boolean = true

        var isFool: Boolean = true

        fun String.fooExt() {}

        fun Int.fooExt() {}

        val String.barExt: String
            get() = "bar"

        var Int.bazarExt: String
            get() = "bar"
            set(value) = Unit

        operator fun String.invoke(p: String, block: String.() -> Unit) = Unit

    """

    private
    val annotatedKotlinMembers = """

        /** @since 2.0 */
        @Incubating
        fun foo() {}

        /** @since 2.0 */
        @get:Incubating
        val bar: String = "bar"

        /** @since 2.0 */
        @get:Incubating
        val bool: Boolean = true

        /** @since 2.0 */
        @get:Incubating
        val isBool: Boolean = true

        /** @since 2.0 */
        @get:Incubating
        @set:Incubating
        var bazar = "bazar"

        /** @since 2.0 */
        @get:Incubating
        @set:Incubating
        var bazool: Boolean = true

        /** @since 2.0 */
        @get:Incubating
        @set:Incubating
        var isFool: Boolean = true

        /** @since 2.0 */
        @Incubating
        fun String.fooExt() {}

        /** @since 2.0 */
        @Incubating
        fun Int.fooExt() {}

        /** @since 2.0 */
        @get:Incubating
        val String.barExt: String
            get() = "bar"

        /** @since 2.0 */
        @get:Incubating
        @set:Incubating
        var Int.bazarExt: String
            get() = "bar"
            set(value) = Unit

        /** @since 2.0 */
        @Incubating
        operator fun String.invoke(p: String, block: String.() -> Unit) = Unit

    """

    @Test
    fun `new top-level kotlin members`() {

        checkNotBinaryCompatibleKotlin(
            v2 = """

           $publicKotlinMembers

            const val cathedral = "cathedral"

            """
        ) {

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
                added("Method", "SourceKt.getBazool()"),
                added("Method", "SourceKt.getBool()"),
                added("Method", "SourceKt.invoke(java.lang.String,java.lang.String,kotlin.jvm.functions.Function1)"),
                added("Method", "SourceKt.isBool()"),
                added("Method", "SourceKt.isFool()"),
                added("Method", "SourceKt.setBazar(java.lang.String)"),
                added("Method", "SourceKt.setBazarExt(int,java.lang.String)"),
                added("Method", "SourceKt.setBazool(boolean)"),
                added("Method", "SourceKt.setFool(boolean)")
            )
        }

        // with existing non-incubating file-facade class, new members must be annotated with @Incubating and @since
        checkBinaryCompatibleKotlin(
            v1 = """
                val existing = "file-facade-class"
            """,
            v2 = """
                val existing = "file-facade-class"

                $annotatedKotlinMembers

                /** @since 2.0 */
                @field:Incubating
                const val cathedral = "cathedral"
            """
        ) {

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
                newApi("Method", "SourceKt.getBazool()"),
                newApi("Method", "SourceKt.getBool()"),
                newApi("Method", "SourceKt.invoke(java.lang.String,java.lang.String,kotlin.jvm.functions.Function1)"),
                newApi("Method", "SourceKt.isBool()"),
                newApi("Method", "SourceKt.isFool()"),
                newApi("Method", "SourceKt.setBazar(java.lang.String)"),
                newApi("Method", "SourceKt.setBazarExt(int,java.lang.String)"),
                newApi("Method", "SourceKt.setBazool(boolean)"),
                newApi("Method", "SourceKt.setFool(boolean)")
            )
        }

        // new file-facade class can be annotated with @Incubating, members must be annotated with @since
        checkBinaryCompatible(
            v2 = {
                withFile(
                    "kotlin/com/example/Source.kt",
                    """
                    @file:Incubating
                    package com.example

                    import org.gradle.api.Incubating

                    ${annotatedKotlinMembers.lineSequence().filter { !it.contains("Incubating") }.joinToString("\n")}

                    /** @since 2.0 */
                    const val cathedral = "cathedral"
                    """
                )
            }
        ) {

            assertHasNoWarning()
            assertHasInformation(
                newApi("Class", "SourceKt"),
                newApi("Field", "cathedral"),
                newApi("Method", "SourceKt.foo()"),
                newApi("Method", "SourceKt.fooExt(java.lang.String)"),
                newApi("Method", "SourceKt.fooExt(int)"),
                newApi("Method", "SourceKt.getBar()"),
                newApi("Method", "SourceKt.getBarExt(java.lang.String)"),
                newApi("Method", "SourceKt.getBazar()"),
                newApi("Method", "SourceKt.getBazarExt(int)"),
                newApi("Method", "SourceKt.getBazool()"),
                newApi("Method", "SourceKt.getBool()"),
                newApi("Method", "SourceKt.invoke(java.lang.String,java.lang.String,kotlin.jvm.functions.Function1)"),
                newApi("Method", "SourceKt.isBool()"),
                newApi("Method", "SourceKt.isFool()"),
                newApi("Method", "SourceKt.setBazar(java.lang.String)"),
                newApi("Method", "SourceKt.setBazarExt(int,java.lang.String)"),
                newApi("Method", "SourceKt.setBazool(boolean)"),
                newApi("Method", "SourceKt.setFool(boolean)")
            )
        }
    }

    @Test
    fun `new top-level kotlin types`() {

        // Singleton INSTANCE fields of `object`s are public

        checkNotBinaryCompatibleKotlin(
            v2 = """

            interface Foo

            class Bar

            enum class Bazar

            object Cathedral

            """
        ) {

            assertHasNoInformation()
            assertHasNoWarning()
            assertHasErrors(
                added("Class", "Bar"),
                added("Constructor", "Bar()"),
                added("Class", "Bazar"),
                added("Method", "Bazar.getEntries()"),
                added("Method", "Bazar.valueOf(java.lang.String)"),
                added("Method", "Bazar.values()"),
                added("Class", "Cathedral"),
                added("Field", "INSTANCE"),
                added("Class", "Foo")
            )
        }

        checkNotBinaryCompatibleKotlin(
            v2 = """

            /** @since 2.0 */
            @Incubating
            interface Foo

            /** @since 2.0 */
            @Incubating
            class Bar

            /** @since 2.0 */
            @Incubating
            enum class Bazar

            /** @since 2.0 */
            @Incubating
            object Cathedral

            """
        ) {

            assertHasNoInformation()
            assertHasNoWarning()
            assertHasErrors(
                added("Constructor", "Bar()"),
                added("Method", "Bazar.getEntries()"),
                added("Method", "Bazar.valueOf(java.lang.String)"),
                added("Method", "Bazar.values()"),
                added("Field", "INSTANCE"),
            )
        }
    }

    @Test
    fun `new kotlin types members`() {

        val baseline = """

            /** @since 1.0 */
            interface Foo

            /** @since 1.0 */
            class Bar()

        """

        checkNotBinaryCompatibleKotlin(
            v1 = baseline,
            v2 = """

            /** @since 1.0 */
            interface Foo : AutoCloseable {
                fun foo()
                override fun close()
            }

            /** @since 1.0 */
            class Bar() {

                constructor(bar: String) : this()

                $publicKotlinMembers
            }

            """
        ) {

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
                added("Method", "Bar.getBazool()"),
                added("Method", "Bar.getBool()"),
                added("Method", "Bar.invoke(java.lang.String,java.lang.String,kotlin.jvm.functions.Function1)"),
                added("Method", "Bar.isBool()"),
                added("Method", "Bar.isFool()"),
                added("Method", "Bar.setBazar(java.lang.String)"),
                added("Method", "Bar.setBazarExt(int,java.lang.String)"),
                added("Method", "Bar.setBazool(boolean)"),
                added("Method", "Bar.setFool(boolean)"),
                added("Constructor", "Bar(java.lang.String)"),
                added("Method", "Foo.foo()")
            )
        }

        checkBinaryCompatibleKotlin(
            v1 = baseline,
            v2 = """

            /** @since 1.0 */
            interface Foo {

                /** @since 2.0 */
                @Incubating
                fun foo()
            }

            /** @since 1.0 */
            class Bar() {

                /** @since 2.0 */
                @Incubating
                constructor(bar: String) : this()

                $annotatedKotlinMembers
            }

            """
        ) {

            assertHasNoWarning()
            assertHasInformation(
                newApi("Method", "Bar.foo()"),
                newApi("Method", "Bar.fooExt(java.lang.String)"),
                newApi("Method", "Bar.fooExt(int)"),
                newApi("Method", "Bar.getBar()"),
                newApi("Method", "Bar.getBarExt(java.lang.String)"),
                newApi("Method", "Bar.getBazar()"),
                newApi("Method", "Bar.getBazarExt(int)"),
                newApi("Method", "Bar.getBazool()"),
                newApi("Method", "Bar.getBool()"),
                newApi("Method", "Bar.invoke(java.lang.String,java.lang.String,kotlin.jvm.functions.Function1)"),
                newApi("Method", "Bar.isBool()"),
                newApi("Method", "Bar.isFool()"),
                newApi("Method", "Bar.setBazar(java.lang.String)"),
                newApi("Method", "Bar.setBazarExt(int,java.lang.String)"),
                newApi("Method", "Bar.setBazool(boolean)"),
                newApi("Method", "Bar.setFool(boolean)"),
                newApi("Method", "Foo.foo()")
            )
        }
    }
}
