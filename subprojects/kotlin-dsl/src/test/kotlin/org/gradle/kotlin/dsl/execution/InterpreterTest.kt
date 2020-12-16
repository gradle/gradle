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
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.TmpDirTemporaryFileProvider
import org.gradle.api.internal.initialization.ClassLoaderScope

import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.classpath.ClassPath

import org.gradle.internal.hash.HashCode
import org.gradle.internal.resource.TextResource
import org.gradle.internal.service.ServiceRegistry
import org.gradle.kotlin.dsl.fixtures.DummyCompiledScript

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.fixtures.assertStandardOutputOf
import org.gradle.kotlin.dsl.fixtures.classLoaderFor
import org.gradle.kotlin.dsl.fixtures.testRuntimeClassPath

import org.junit.Test

import java.io.File

import java.net.URLClassLoader


class InterpreterTest : TestWithTempFiles() {

    @Test
    fun `caches specialized programs`() {

        val scriptPath =
            "/src/settings.gradle.kts"

        val text = """

            buildscript {
                require(Thread.currentThread().contextClassLoader === this@Settings_gradle.javaClass.classLoader)
                println("stage 1")
            }

            require(Thread.currentThread().contextClassLoader === this@Settings_gradle.javaClass.classLoader)
            println("stage 2")

        """.trimIndent()

        val sourceHash = HashCode.fromInt(42)
        val compilationClassPathHash = HashCode.fromInt(11)
        val stage1TemplateId = "Settings/TopLevel/stage1"
        val stage2TemplateId = "Settings/TopLevel/stage2"

        val scriptSourceResource = mock<TextResource> {
            on { getText() } doReturn text
        }
        val scriptSourceDisplayName = "source display name"

        val scriptSource = mock<ScriptSource> {
            on { fileName } doReturn scriptPath
            on { resource } doReturn scriptSourceResource
            on { displayName } doReturn scriptSourceDisplayName
        }
        val parentClassLoader = mock<ClassLoader>()
        val baseScope = mock<ClassLoaderScope> {
            on { exportClassLoader } doReturn parentClassLoader
        }
        val parentScope = mock<ClassLoaderScope>()
        val targetScopeExportClassLoader = mock<ClassLoader>()
        val targetScope = mock<ClassLoaderScope> {
            on { parent } doReturn parentScope
            on { exportClassLoader } doReturn targetScopeExportClassLoader
        }

        val compilerOperation = mock<AutoCloseable>()

        val classLoaders = mutableListOf<URLClassLoader>()

        val stage1CacheDir = root.resolve("stage1").apply { mkdir() }
        val stage2CacheDir = root.resolve("stage2").apply { mkdir() }

        val mockServiceRegistry = mock<ServiceRegistry> {
            on { get(TmpDirTemporaryFileProvider::class.java) } doReturn TestFiles.tmpDirTemporaryFileProvider(tempFolder.root)
        }

        val host = mock<Interpreter.Host> {

            on { hashOf(eq(testRuntimeClassPath)) } doReturn compilationClassPathHash

            on { serviceRegistryFor(any(), any()) } doReturn mockServiceRegistry

            on { startCompilerOperation(any()) } doReturn compilerOperation

            on { runCompileBuildOperation(any(), any(), any()) } doAnswer { it.getArgument<() -> String>(2)() }

            on {
                cachedDirFor(
                    any(),
                    eq(stage1TemplateId),
                    eq(sourceHash),
                    same(testRuntimeClassPath),
                    same(ClassPath.EMPTY),
                    any()
                )
            } doAnswer {
                it.getArgument<(File) -> Unit>(5).invoke(stage1CacheDir)
                stage1CacheDir
            }

            on {
                cachedDirFor(
                    any(),
                    eq(stage2TemplateId),
                    eq(sourceHash),
                    same(testRuntimeClassPath),
                    same(ClassPath.EMPTY),
                    any()
                )
            } doAnswer {
                it.getArgument<(File) -> Unit>(5).invoke(stage2CacheDir)
                stage2CacheDir
            }

            on {
                compilationClassPathOf(any())
            } doAnswer {
                testRuntimeClassPath
            }

            on {
                loadClassInChildScopeOf(any(), any(), any(), any(), same(ClassPath.EMPTY))
            } doAnswer {

                val location = it.getArgument<File>(2)
                val className = it.getArgument<String>(3)

                val newLocation = relocate(location)

                DummyCompiledScript(
                    classLoaderFor(newLocation)
                        .also { classLoaders += it }
                        .loadClass(className)
                )
            }
        }

        try {

            val target = mock<Settings>()
            val subject = Interpreter(host)
            assertStandardOutputOf("stage 1\nstage 2\n") {
                subject.eval(
                    target,
                    scriptSource,
                    sourceHash,
                    mock(),
                    targetScope,
                    baseScope,
                    true
                )
            }

            inOrder(host, compilerOperation) {

                val stage1ProgramId =
                    ProgramId(stage1TemplateId, sourceHash, parentClassLoader)

                verify(host).cachedClassFor(stage1ProgramId)

                verify(host).compilationClassPathOf(parentScope)

                verify(host).startCompilerOperation(scriptSourceDisplayName)

                verify(compilerOperation).close()

                verify(host).loadClassInChildScopeOf(
                    baseScope,
                    "kotlin-dsl:$scriptPath:$stage1TemplateId",
                    stage1CacheDir,
                    "Program",
                    ClassPath.EMPTY
                )

                verify(host).cache(
                    DummyCompiledScript(classLoaders[0].loadClass("Program")),
                    stage1ProgramId
                )

                val stage2ProgramId =
                    ProgramId(stage2TemplateId, sourceHash, targetScopeExportClassLoader, null, compilationClassPathHash)

                verify(host).cachedClassFor(stage2ProgramId)

                verify(host).compilationClassPathOf(targetScope)

                verify(host).startCompilerOperation(scriptSourceDisplayName)

                verify(compilerOperation).close()

                verify(host).loadClassInChildScopeOf(
                    targetScope,
                    "kotlin-dsl:$scriptPath:$stage2TemplateId",
                    stage2CacheDir,
                    "Program",
                    ClassPath.EMPTY
                )

                val specializedProgram = classLoaders[1].loadClass("Program")
                verify(host).cache(
                    DummyCompiledScript(specializedProgram),
                    stage2ProgramId
                )

                verify(host).onScriptClassLoaded(
                    scriptSource,
                    specializedProgram
                )

                verifyNoMoreInteractions()
            }
        } finally {
            classLoaders.forEach {
                it.close()
            }
        }
    }

    private
    fun relocate(location: File): File {
        val newLocation = location.parentFile.resolve(location.name + "-relocated")
        location.renameTo(newLocation)
        return newLocation
    }
}
