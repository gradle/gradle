/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.execution

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class ProgramTextTest {

    @Test
    fun `preserve`() {

        val text = text("0123456789")
        assertThat(
            text.preserve(0..1),
            equalTo(text("01        "))
        )

        assertThat(
            text.preserve(0..1, 7..9),
            equalTo(text("01     789"))
        )

        assertThat(
            text.preserve(7..9, 0..1),
            equalTo(text("01     789"))
        )
    }
}
