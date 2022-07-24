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

package org.gradle.kotlin.dsl.tooling.builders

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class CommonListPrefixTest {

    @Test
    fun `common is prefix`() {

        val a = listOf("a", "b", "c", "0", "1", "2", "d")
        val b = listOf("a", "b", "c", "3", "4", "5", "d")
        val c = listOf("a", "b", "c", "6", "7", "8", "d")

        assertThat(
            commonPrefixOf(listOf(a, b, c)),
            equalTo(listOf("a", "b", "c"))
        )
    }

    @Test
    fun `same content common is same content`() {

        val a = listOf("a", "b", "c")
        val b = listOf("a", "b", "c")

        assertThat(
            commonPrefixOf(listOf(a, b)),
            equalTo(listOf("a", "b", "c"))
        )
    }

    @Test
    fun `common is empty for different prefix`() {

        val a = listOf("0", "a", "b", "c")
        val b = listOf("1", "a", "b", "c")

        assertThat(
            commonPrefixOf(listOf(a, b)),
            equalTo(emptyList())
        )
    }

    @Test
    fun `doesn't choke on left being larger than right`() {

        val a = listOf("a", "b", "c", "0", "1", "d")
        val b = listOf("a", "b", "c", "3", "d")

        assertThat(
            commonPrefixOf(listOf(a, b)),
            equalTo(listOf("a", "b", "c"))
        )
    }

    @Test
    fun `doesn't choke on right being larger than left`() {

        val a = listOf("a", "b", "c", "0", "d")
        val b = listOf("a", "b", "c", "3", "4", "d")

        assertThat(
            commonPrefixOf(listOf(a, b)),
            equalTo(listOf("a", "b", "c"))
        )
    }

    @Test
    fun `empty lists have empty prefix`() {

        assertThat(
            commonPrefixOf(emptyList()),
            equalTo(emptyList<Any>())
        )
        assertThat(
            commonPrefixOf(listOf(emptyList())),
            equalTo(emptyList<Any>())
        )
        assertThat(
            commonPrefixOf(listOf(emptyList(), emptyList())),
            equalTo(emptyList<Any>())
        )
    }

    @Test
    fun `single list prefix is same list`() {

        assertThat(
            commonPrefixOf(listOf(listOf("a", "b"))),
            equalTo(listOf("a", "b"))
        )
    }
}
