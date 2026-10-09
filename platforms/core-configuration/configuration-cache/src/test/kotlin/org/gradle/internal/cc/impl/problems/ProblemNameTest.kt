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

package org.gradle.internal.cc.impl.problems

import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals


class ProblemNameTest {

    @Test
    fun `single line message is the name`() {
        assertEquals(
            "registration of listener on 'Gradle.buildFinished' is unsupported",
            problemNameOf("registration of listener on 'Gradle.buildFinished' is unsupported")
        )
    }

    @Test
    fun `multi line message is named by its first line`() {
        assertEquals(
            "Class 'a.B' cannot be encoded.",
            problemNameOf("\n  Class 'a.B' cannot be encoded.\n\t- class loader 1\n\t- class loader 2\nPlease report this error.")
        )
    }

    @Test
    fun `control and format characters are removed and whitespace is collapsed`() {
        assertEquals(
            "a b c",
            problemNameOf("a\t\u0000 b \u200Bc")
        )
    }

    @Test
    fun `long message is shortened to the maximum name length`() {
        val name = problemNameOf("x".repeat(5000))

        assertEquals(2000, name.codePointCount(0, name.length))
        assertEquals('…', name.last())
    }
}
