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

import com.nhaarman.mockitokotlin2.mock
import org.gradle.api.Project
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class InstantExecutionTest {

    @Test
    fun `mind the gaps`() {
        val root = project(null)
        val a = project(root)
        val a_b = project(a)
        val a_c = project(a)
        val d = project(root)
        val d_e = project(d)
        val d_e_f = project(d_e)
        assertThat(
            fillTheGapsOf(
                listOf(
                    a_b,
                    a_c,
                    d_e_f
                )
            ),
            equalTo(
                listOf(
                    root,
                    a,
                    a_b,
                    a_c,
                    d,
                    d_e,
                    d_e_f
                )
            )
        )
    }

    @Test
    fun `don't mind no gaps`() {
        val root = project(null)
        val a = project(root)
        val a_b = project(a)
        val a_c = project(a)
        assertThat(
            fillTheGapsOf(
                listOf(
                    root,
                    a,
                    a_b,
                    a_c
                )
            ),
            equalTo(
                listOf(
                    root,
                    a,
                    a_b,
                    a_c
                )
            )
        )
    }

    fun project(parent: Project?): Project {
        return mock {
            on(mock.parent).thenReturn(parent)
        }
    }
}
