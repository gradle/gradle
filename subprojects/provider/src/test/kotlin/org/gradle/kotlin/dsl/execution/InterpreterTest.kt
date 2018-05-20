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

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.same

import org.gradle.api.initialization.Settings
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.initialization.ClassLoaderScope

import org.gradle.groovy.scripts.ScriptSource

import org.gradle.internal.resource.TextResource
import org.gradle.internal.service.ServiceRegistry

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.fixtures.classLoaderFor

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.File
import java.net.URLClassLoader


class InterpreterTest : TestWithTempFiles() {

    @Test
    fun `caches specialized programs`() {

        val text = "println(\"stage 2\")"
        val sourceHash = scriptSourceHash(text)
        val stage1TemplateId = TemplateIds.stage1SettingsScript
        val stage2TemplateId = TemplateIds.stage2SettingsScript

        val resource = mock<TextResource> {
            on { getText() } doReturn text
        }
        val scriptPath = "/src/settings.gradle.kts"
        val scriptSource = mock<ScriptSource> {
            on { fileName } doReturn scriptPath
            on { getResource() } doReturn resource
        }
        val parentClassLoader = mock<ClassLoader>()
        val baseScope = mock<ClassLoaderScope> {
            on { exportClassLoader } doReturn parentClassLoader
        }
        val parentScope = mock<ClassLoaderScope>()
        val targetScopeLocalClassLoader = mock<ClassLoader>()
        val targetScope = mock<ClassLoaderScope> {
            on { parent } doReturn parentScope
            on { localClassLoader } doReturn targetScopeLocalClassLoader
        }

        val classLoaders = mutableListOf<URLClassLoader>()

        val stage1CacheDir = root.resolve("stage1").apply { mkdir() }
        val stage2CacheDir = root.resolve("stage2").apply { mkdir() }

        val host = mock<Interpreter.Host> {

            on {
                cachedDirFor(
                    eq(stage1TemplateId),
                    eq(sourceHash),
                    same(parentClassLoader),
                    any())
            } doAnswer {
                it.getArgument<(File) -> Unit>(3).invoke(stage1CacheDir)
                stage1CacheDir
            }

            on {
                cachedDirFor(
                    eq(stage2TemplateId),
                    eq(sourceHash),
                    same(targetScopeLocalClassLoader),
                    any())
            } doAnswer {
                it.getArgument<(File) -> Unit>(3).invoke(stage2CacheDir)
                stage2CacheDir
            }

            on {
                compilationClassPathOf(any())
            } doAnswer {
                testCompilationClassPath
            }

            on {
                loadClassInChildScopeOf(any(), any(), any(), any())
            } doAnswer {
                classLoaderFor(it.getArgument(2))
                    .also { classLoaders.add(it) }
                    .loadClass(it.getArgument(3))
            }
        }

        try {

            val gradle = mock<GradleInternal> {
                on { services } doReturn mock<ServiceRegistry>()
            }
            val target = mock<Settings> {
                on { getGradle() } doReturn gradle
            }

            val subject = Interpreter(host)
            assertThat(
                standardOutputOf {
                    subject.eval(
                        target,
                        scriptSource,
                        mock(),
                        targetScope,
                        baseScope,
                        true)
                },
                equalTo("stage 2\n"))

            inOrder(host) {

                verify(host).cachedClassFor(
                    stage1TemplateId,
                    sourceHash,
                    parentClassLoader)

                verify(host).compilationClassPathOf(parentScope)

                verify(host).loadClassInChildScopeOf(
                    baseScope,
                    "kotlin-dsl:$scriptPath:stage1",
                    file("stage1/stage1"),
                    "Program")

                verify(host).cache(
                    stage1TemplateId,
                    sourceHash,
                    parentClassLoader,
                    classLoaders[0].loadClass("Program"))

                verify(host).cachedClassFor(
                    stage2TemplateId,
                    sourceHash,
                    targetScopeLocalClassLoader)

                verify(host).compilationClassPathOf(targetScope)

                verify(host).loadClassInChildScopeOf(
                    targetScope,
                    "kotlin-dsl:$scriptPath:stage2",
                    stage2CacheDir,
                    "Program")

                verify(host).cache(
                    stage2TemplateId,
                    sourceHash,
                    targetScopeLocalClassLoader,
                    classLoaders[1].loadClass("Program"))
            }
        } finally {
            classLoaders.forEach {
                it.close()
            }
        }
    }
}
