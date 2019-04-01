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

            class ExistingNestedClass {

                $existingMembers

            }
        }

        typealias ExistingTypeAlias = String
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

        typealias ExistingTypeAlias = String

        internal class AddedClass() {

            constructor(bar: ExistingTypeAlias) : this()

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

        typealias ExistingTypeAlias = String

        class AddedClass() {

            constructor(bar: ExistingTypeAlias) : this()

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
            assertHasErrors(*reportedMembers.map {
                added(it.first, it.second)
            }.toTypedArray())
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
            assertHasErrors(*reportedMembers.map {
                removed(it.first, it.second)
            }.toTypedArray())
            assertHasNoWarning()
            assertHasNoInformation()
        }
    }

    private
    val reportedMembers = listOf(
        "Class" to "AddedClass"
    ) + reportedMembersFor("AddedClass") + listOf(
        "Constructor" to "AddedClass(java.lang.String)",
        "Constructor" to "AddedClass()",
        "Class" to "AddedEnum",
        "Field" to "FOO"
    ) + reportedMembersFor("AddedEnum") + listOf(
        "Method" to "AddedEnum.valueOf(java.lang.String)",
        "Method" to "AddedEnum.values()",
        "Class" to "AddedObject",
        "Field" to "INSTANCE"
    ) + reportedMembersFor("AddedObject") + reportedMembersFor("ExistingClass") + listOf(
        "Constructor" to "ExistingClass(java.lang.String)"
    ) + reportedMembersFor("ExistingClass${'$'}ExistingNestedClass") + reportedMembersFor("SourceKt")

    private
    fun reportedMembersFor(containingType: String) =
        listOf(
            "Method" to "$containingType.getFoo()"
        )
}
