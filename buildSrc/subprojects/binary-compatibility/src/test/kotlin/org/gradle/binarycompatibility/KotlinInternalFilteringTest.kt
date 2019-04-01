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


/**
 * Asserts Kotlin `internal` members are filtered from the comparison.
 */
class KotlinInternalFilteringTest : AbstractKotlinBinaryCompatibilityTest() {

    private
    val existingMembers = """

    """

    private
    val internalMembers = """
        internal val foo = "foo"
    """

    private
    val publicMembers = """
        val foo = "foo"
    """

    private
    val existingSource = """

        $existingMembers

        class ExistingClass {

            $existingMembers

            class ExistingNestedClass
        }
    """

    private
    val internalSource = """

        $internalMembers

        class ExistingClass() {

            internal constructor(bar: String) : this()

            $internalMembers

            class ExistingNestedClass {

                $internalMembers

            }
        }

        internal class AddedClass() {

            constructor(bar: String) : this()

            $publicMembers
        }

        internal object AddedObject {
            $publicMembers
        }

        internal enum class AddedEnum {
            FOO;

            $publicMembers
        }
    """

    private
    val publicSource = """

        $publicMembers

        class ExistingClass() {

            constructor(bar: String) : this()

            $publicMembers

            class ExistingNestedClass {

                $publicMembers

            }
        }

        class AddedClass() {

            constructor(bar: String) : this()

            $publicMembers
        }

        object AddedObject {
            $publicMembers
        }

        enum class AddedEnum {
            FOO;

            $publicMembers
        }
    """

    @Test
    fun `added internal members are filtered from the comparison`() {

        checkBinaryCompatible(
            v1 = existingSource,
            v2 = internalSource
        ).assertEmptyReport()
    }

    @Test
    fun `existing internal members made public appear as added`() {

        checkNotBinaryCompatible(
            v1 = internalSource,
            v2 = publicSource
        ).apply {
            assertHasErrors(
                added("Class", "AddedClass"),
                added("Method", "AddedClass.getFoo()"),
                added("Constructor", "AddedClass(java.lang.String)"),
                added("Constructor", "AddedClass()"),
                added("Class", "AddedEnum"),
                added("Field", "FOO"),
                added("Method", "AddedEnum.getFoo()"),
                added("Method", "AddedEnum.valueOf(java.lang.String)"),
                added("Method", "AddedEnum.values()"),
                added("Class", "AddedObject"),
                added("Field", "INSTANCE"),
                added("Method", "AddedObject.getFoo()"),
                added("Method", "ExistingClass.getFoo()"),
                added("Constructor", "ExistingClass(java.lang.String)"),
                added("Method", "ExistingClass${'$'}ExistingNestedClass.getFoo()"),
                added("Method", "SourceKt.getFoo()")
            )
            assertHasNoWarning()
            assertHasNoInformation()
        }
    }

    @Test
    fun `existing public members made internal appear as removed`() {

        checkNotBinaryCompatible(
            v1 = publicSource,
            v2 = internalSource
        ).apply {
            assertHasErrors(
                removed("Class", "AddedClass"),
                removed("Method", "AddedClass.getFoo()"),
                removed("Constructor", "AddedClass(java.lang.String)"),
                removed("Constructor", "AddedClass()"),
                removed("Class", "AddedEnum"),
                removed("Field", "FOO"),
                removed("Method", "AddedEnum.getFoo()"),
                removed("Method", "AddedEnum.valueOf(java.lang.String)"),
                removed("Method", "AddedEnum.values()"),
                removed("Class", "AddedObject"),
                removed("Field", "INSTANCE"),
                removed("Method", "AddedObject.getFoo()"),
                removed("Method", "ExistingClass.getFoo()"),
                removed("Constructor", "ExistingClass(java.lang.String)"),
                removed("Method", "ExistingClass${'$'}ExistingNestedClass.getFoo()"),
                removed("Method", "SourceKt.getFoo()")
            )
            assertHasNoWarning()
            assertHasNoInformation()
        }
    }

    private
    fun added(thing: String, desc: String): List<String> =
        listOf(
            "$thing ${describe(thing, desc)}: Is not annotated with @Incubating.",
            "$thing ${describe(thing, desc)}: Is not annotated with @since 2.0."
        )

    private
    fun removed(thing: String, desc: String): Pair<String, List<String>> =
        "$thing ${describe(thing, desc)}: Is not binary compatible." to listOf("$thing has been removed")

    private
    fun describe(thing: String, desc: String) =
        if (thing == "Field") desc else "com.example.$desc"
}
