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


class KotlinModifiersChangeTest : AbstractBinaryCompatibilityTest() {

    @Test
    fun `operator or infix modifier change is breaking`() {

        checkNotBinaryCompatibleKotlin(
            v1 = """
                fun Int.invoke(some: Int) {}
                operator fun Boolean.invoke(some: Int) {}

                fun String.invoke(some: Int) {}
                operator fun Long.invoke(some: Int) {}

                fun Float.invoke(some: Int) {}
                infix fun Byte.invoke(some: Int) {}

                interface Source {
                    infix fun String.plus(some: List<Int>): String
                    operator fun Int.plus(some: List<Int>): Int
                }
            """,
            v2 = """
                fun Int.invoke(some: Int) {}
                operator fun Boolean.invoke(some: Int) {}

                operator fun String.invoke(some: Int) {}
                fun Long.invoke(some: Int) {}

                infix fun Float.invoke(some: Int) {}
                fun Byte.invoke(some: Int) {}

                interface Source {
                    operator fun String.plus(some: List<Int>): String
                    infix fun Int.plus(some: List<Int>): Int
                }
            """
        ) {
            assertHasErrors(
                "Method com.example.Source.plus(java.lang.String,java.util.List): Breaking Kotlin modifier change.",
                "Method com.example.Source.plus(int,java.util.List): Breaking Kotlin modifier change.",
                "Method com.example.SourceKt.invoke(java.lang.String,int): Breaking Kotlin modifier change.",
                "Method com.example.SourceKt.invoke(long,int): Breaking Kotlin modifier change.",
                "Method com.example.SourceKt.invoke(float,int): Breaking Kotlin modifier change.",
                "Method com.example.SourceKt.invoke(byte,int): Breaking Kotlin modifier change."
            )
            assertHasNoWarning()
            assertHasNoInformation()
        }
    }
}
