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

package org.gradle.kotlin.dsl.provider.plugins.precompiled.tasks

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.CoreMatchers.equalTo

import org.junit.Test


class ReduceGraphTest {

    @Test
    fun `only external node references are left in the graph`() {

        assertThat(
            reduceGraph(
                mapOf(
                    "a" to listOf("b", "c"),
                    "b" to listOf("e1"),
                    "c" to listOf("d"),
                    "d" to listOf("b", "e2")
                )
            ),
            equalTo(
                mapOf(
                    "a" to setOf("e1", "e2"),
                    "b" to setOf("e1"),
                    "c" to setOf("e1", "e2"),
                    "d" to setOf("e1", "e2")
                )
            )
        )
    }
}
