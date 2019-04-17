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

package org.gradle.instantexecution

import org.gradle.util.Path.path
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class InstantExecutionTest {

    @Test
    fun `mind the gaps`() {
        assertThat(
            fillTheGapsOf(
                sortedSetOf(
                    path(":a:b"),
                    path(":a:c"),
                    path(":d:e:f")
                )
            ),
            equalTo(
                listOf(
                    path(":"),
                    path(":a"),
                    path(":a:b"),
                    path(":a:c"),
                    path(":d"),
                    path(":d:e"),
                    path(":d:e:f")
                )
            )
        )
    }

    @Test
    fun `don't mind no gaps`() {
        assertThat(
            fillTheGapsOf(
                sortedSetOf(
                    path(":"),
                    path(":a"),
                    path(":a:b"),
                    path(":a:c")
                )
            ),
            equalTo(
                listOf(
                    path(":"),
                    path(":a"),
                    path(":a:b"),
                    path(":a:c")
                )
            )
        )
    }
}
