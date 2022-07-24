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

package org.gradle.kotlin.dsl.fixtures

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import org.gradle.api.initialization.IncludedBuild
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.plugins.ExtensionContainerInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.invocation.Gradle
import org.junit.Test


class SimplifiedKotlinScriptEvaluatorTest : TestWithTempFiles() {

    @Test
    fun `can eval script against Project mock`() {

        val project = project()
        eval(
            script = """
                version = "1.0"
            """,
            target = project
        )
        verify(project).version = "1.0"
    }

    @Test
    fun `can eval script against Settings mock`() {

        val settings = mock<Settings>()
        eval(
            script = """
                include("foo")
            """,
            target = settings
        )
        verify(settings).include("foo")
    }

    @Test
    fun `can eval script against Gradle mock`() {

        val includedBuild = mock<IncludedBuild>()
        val gradle = mock<Gradle>() {
            on { includedBuild(any()) } doReturn includedBuild
        }
        eval(
            script = """
                includedBuild("foo")
            """,
            target = gradle
        )
        verify(gradle).includedBuild("foo")
    }

    private
    fun project() = mock<ProjectInternal> {
        on { extensions } doReturn mock<ExtensionContainerInternal>()
    }

    private
    fun eval(script: String, target: Any): Any =
        eval(script, target, baseCacheDir = file("cache"), baseTempDir = file("temp"))
}
