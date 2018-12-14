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


class LexerTest {

    @Test
    fun `extracts comments and top-level blocks`() {

        val (comments, topLevelBlocks) =

            lex(
                "/* ... */" +
                    "\n// ..." +
                    "\nbuildscript { /*" +
                    "\n ... */" +
                    "\n}" +
                    "\nplugins { /*" +
                    "\n ... */" +
                    "\n}" +
                    "\n// ...",
                "buildscript", "plugins")

        assertThat(
            comments,
            equalTo(
                listOf(
                    0..8,
                    10..15,
                    31..40,
                    54..63,
                    67..72)))

        assertThat(
            topLevelBlocks,
            equalTo(
                listOf(
                    topLevelBlock("buildscript", 17..27, 29..42),
                    topLevelBlock("plugins", 44..50, 52..65))))
    }
}
