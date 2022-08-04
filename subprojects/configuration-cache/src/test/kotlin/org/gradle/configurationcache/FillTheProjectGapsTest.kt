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

package org.gradle.configurationcache

import com.nhaarman.mockitokotlin2.mock
import org.gradle.api.internal.project.ProjectState
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import kotlin.reflect.KProperty


@Suppress("LocalVariableName")
class FillTheProjectGapsTest {

    @Test
    fun `mind the gaps`() {
        val root by projectMock(null)
        val a by projectMock(root)
        val a_b by projectMock(a)
        val a_c by projectMock(a)
        val d by projectMock(root)
        val d_e by projectMock(d)
        val d_e_f by projectMock(d_e)
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
        val root by projectMock(null)
        val a by projectMock(root)
        val a_b by projectMock(a)
        val a_c by projectMock(a)
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

    @Test
    fun `deduplicate when filling gaps`() {

        val root by projectMock(null)

        val a by projectMock(root)
        val a_a by projectMock(a)
        val a_a_a by projectMock(a_a)

        val b by projectMock(root)
        val b_a by projectMock(b)
        val b_a_a by projectMock(b_a)

        assertThat(
            fillTheGapsOf(
                listOf(
                    b_a_a, a_a_a, a_a_a, b_a_a, root
                )
            ),
            equalTo(
                listOf(
                    root,
                    b, b_a, b_a_a,
                    a, a_a, a_a_a
                )
            )
        )
    }

    @Suppress("ClassName")
    private
    class projectMock(private val parent: ProjectState?) {

        private
        lateinit var memoizedMock: ProjectState

        operator fun getValue(thisRef: ProjectState?, property: KProperty<*>): ProjectState {
            if (!this::memoizedMock.isInitialized) {
                memoizedMock = mock {
                    on(mock.parent).thenReturn(parent)
                    on(mock.toString()).thenReturn(property.name)
                }
            }
            return memoizedMock
        }
    }
}
