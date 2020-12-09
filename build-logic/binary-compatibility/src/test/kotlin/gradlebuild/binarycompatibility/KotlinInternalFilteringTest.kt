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


/**
 * Asserts Kotlin `internal` members are filtered from the comparison.
 */
class KotlinInternalFilteringTest : AbstractBinaryCompatibilityTest() {

    private
    val internalMembers = """

        internal fun foo() {}

        internal val bar = "bar"

        internal var bazar = "bazar"

        internal fun String.fooExt() {}

        internal fun Int.fooExt() {}

        internal val String.barExt: String
            get() = "bar"

        internal var Int.bazarExt: String
            get() = "bar"
            set(value) = Unit

    """

    private
    val publicMembers = """

        fun foo() {}

        val bar = "bar"

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
    val existingSource = """

        class ExistingClass {

            class ExistingNestedClass
        }

        val valTurnedIntoVar: String
            get() = ""

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

        var valTurnedIntoVar: String
            get() = ""
            internal set(value) = Unit

        internal const val cathedral = "cathedral"

        internal class AddedClass() {

            constructor(bar: ExistingTypeAlias) : this()

            $publicMembers
        }

        internal object AddedObject {

            $publicMembers

            const val cathedral = "cathedral"
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

        var valTurnedIntoVar: String
            get() = ""
            set(value) = Unit

        const val cathedral = "cathedral"

        class AddedClass() {

            constructor(bar: ExistingTypeAlias) : this()

            $publicMembers
        }

        object AddedObject {

            $publicMembers

            const val cathedral = "cathedral"
        }

        enum class AddedEnum {
            FOO;

            $publicMembers
        }
    """

    @Test
    fun `added internal members are filtered from the comparison`() {

        checkBinaryCompatibleKotlin(
            v1 = existingSource,
            v2 = internalSource
        ).assertEmptyReport()
    }

    @Test
    fun `existing internal members made public appear as added`() {

        checkNotBinaryCompatibleKotlin(
            v1 = internalSource,
            v2 = publicSource
        ).apply {
            assertHasErrors(
                *reportedMembers.map {
                    added(it.first, it.second)
                }.toTypedArray()
            )
            assertHasNoWarning()
            assertHasNoInformation()
        }
    }

    @Test
    fun `existing public members made internal appear as removed`() {

        checkNotBinaryCompatibleKotlin(
            v1 = publicSource,
            v2 = internalSource
        ).apply {
            assertHasErrors(
                *reportedMembers.map {
                    removed(it.first, it.second)
                }.toTypedArray()
            )
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
        "Field" to "INSTANCE",
        "Field" to "cathedral"
    ) + reportedMembersFor("AddedObject") + reportedMembersFor("ExistingClass") + listOf(
        "Constructor" to "ExistingClass(java.lang.String)"
    ) + reportedMembersFor("ExistingClass${'$'}ExistingNestedClass") + listOf(
        "Field" to "cathedral"
    ) + reportedMembersFor("SourceKt") + listOf(
        "Method" to "SourceKt.setValTurnedIntoVar(java.lang.String)"
    )

    private
    fun reportedMembersFor(containingType: String) =
        listOf(
            "Method" to "$containingType.foo()",
            "Method" to "$containingType.fooExt(java.lang.String)",
            "Method" to "$containingType.fooExt(int)",
            "Method" to "$containingType.getBar()",
            "Method" to "$containingType.getBarExt(java.lang.String)",
            "Method" to "$containingType.getBazar()",
            "Method" to "$containingType.getBazarExt(int)",
            "Method" to "$containingType.setBazar(java.lang.String)",
            "Method" to "$containingType.setBazarExt(int,java.lang.String)"
        )
}
