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

package org.gradle.kotlin.dsl

import org.gradle.api.Action
import org.gradle.vcs.VcsMapping
import org.gradle.vcs.git.GitVersionControlSpec

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doNothing
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock

import org.junit.Test


class SourceControlExtensionsTest {

    @Test
    fun `vcs mapping from`() {

        val vcsMapping = mock<VcsMapping>()
        doNothing().`when`(vcsMapping).from(any<Class<GitVersionControlSpec>>(), any<Action<GitVersionControlSpec>>())

        vcsMapping.from<GitVersionControlSpec> { }

        inOrder(vcsMapping) {
            verify(vcsMapping).from(any<Class<GitVersionControlSpec>>(), any<Action<GitVersionControlSpec>>())
            verifyNoMoreInteractions()
        }
    }
}
