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


class KotlinBinaryCompatibilityTest : AbstractKotlinBinaryCompatibilityTest() {

    // TODO:kotlin-dsl move cases to below test
    @Test
    fun `internal members are ignored`() {

        checkBinaryCompatible(
            v1 = """

                /**
                 * @since 1.0
                 */
                @Incubating
                class PublicClass

            """,
            v2 = """

                internal fun internalTopLevelFunction() {}

                internal val internalTopLevelVal = "bar"

                internal var internalTopLevelVar = "bazar"

                internal const val internalTopLevelConst = "cathedral"

                /**
                 * @since 1.0
                 */
                @Incubating
                class PublicClass {

                    internal val internalVal = "foo"
                    internal var internalVar = "foo"
                    internal fun internalFunction() {}
                }

                internal class InternalClass {

                    val someVal = "foo"
                    var someVar = "foo"
                    fun someFunction() {}
                }

            """
        ).assertEmptyReport()
    }

    @Test
    fun `internal members are filtered from comparison`() {

        // top-level internal members that are in "file-facade" classes
        // aren't distinguishable from public ones without Kotlin metadata
        // japicmp alone won't catch the difference
        // this test exercises internal members filtering

        val internalMembers = """
                internal val foo = "foo"
            """

        val publicMembers = """
            val foo = "foo"
        """

        val annotatedPublicMembers = """
            /**
             * @since 2.0
             */
            @get:Incubating
            val foo = "foo"
        """

        checkNotBinaryCompatible(
            v1 = publicMembers,
            v2 = internalMembers
        ).apply {
            assertHasError(
                "Method com.example.SourceKt.getFoo(): Is not binary compatible.",
                "Method has been removed"
            )
            assertHasNoWarning()
            assertHasNoInformation()
        }

        checkNotBinaryCompatible(
            v1 = internalMembers,
            v2 = publicMembers
        ).apply {
            assertHasErrors(
                "Method com.example.SourceKt.getFoo(): Is not annotated with @Incubating.",
                "Method com.example.SourceKt.getFoo(): Is not annotated with @since 2.0."
            )
            assertHasNoWarning()
            assertHasNoInformation()
        }

        checkBinaryCompatible(
            v1 = internalMembers,
            v2 = annotatedPublicMembers
        ).apply {
            assertHasInformation(
                "Method com.example.SourceKt.getFoo(): New public API in 2.0 (@Incubating)"
            )
            assertHasNoWarning()
            assertHasNoError()
        }
    }
}
