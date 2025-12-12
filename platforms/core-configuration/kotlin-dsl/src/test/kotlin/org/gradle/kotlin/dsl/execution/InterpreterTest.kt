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

import org.gradle.api.initialization.Settings
import org.gradle.api.internal.file.temp.GradleUserHomeTemporaryFileProvider
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.initialization.ClassLoaderScopeOrigin
import org.gradle.internal.Describables
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.resource.TextResource
import org.gradle.internal.service.ServiceRegistry
import org.gradle.kotlin.dsl.fixtures.DummyCompiledScript
import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.fixtures.assertStandardOutputOf
import org.gradle.kotlin.dsl.fixtures.classLoaderFor
import org.gradle.kotlin.dsl.fixtures.testRuntimeClassPath
import org.gradle.kotlin.dsl.support.KotlinCompilerOptions
import org.gradle.kotlin.dsl.support.KotlinScriptHost
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import java.io.File
import java.net.URLClassLoader


class InterpreterTest : TestWithTempFiles() {

    @Test
    fun `caches specialized programs`() {

        val scriptPath = "/src/settings.gradle.kts"

        val shortScriptDisplayName = Describables.of("short display name")
        val longScriptDisplayName = Describables.of("long display name")

        val text = """

            buildscript {
                class DummyBuildScriptClass
                require(Thread.currentThread().contextClassLoader === DummyBuildScriptClass::class.java.classLoader)
                println("stage 1")
            }

            class DummyScriptBodyClass
            require(Thread.currentThread().contextClassLoader === DummyScriptBodyClass::class.java.classLoader)
            println("stage 2")

        """.trimIndent()

        val scriptClassName = "Settings_gradle"
        val sourceHash = TestHashCodes.hashCodeFrom(42)
        val compilationClassPathHash = TestHashCodes.hashCodeFrom(11)
        val accessorsClassPathHash = TestHashCodes.hashCodeFrom(0)
        val accessorsClassPath = ClassPath.EMPTY
        val stage1TemplateId = "Settings/TopLevel/stage1"
        val stage2TemplateId = "Settings/TopLevel/stage2"

        val scriptSourceResource = mock<TextResource> {
            on { getText() } doReturn text
        }

        val scriptSource = mock<ScriptSource> {
            on { fileName } doReturn scriptPath
            on { className } doReturn scriptClassName
            on { resource } doReturn scriptSourceResource
            on { shortDisplayName } doReturn shortScriptDisplayName
            on { longDisplayName } doReturn longScriptDisplayName
            on { displayName } doReturn longScriptDisplayName.displayName
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

        val stage1ProgramId = ProgramId(stage1TemplateId, scriptSource.fileName, scriptSource.className, sourceHash, parentClassLoader)
        val stage2ProgramId = ProgramId(stage2TemplateId, scriptSource.fileName, scriptSource.className, sourceHash, targetScopeExportClassLoader, accessorsClassPathHash, compilationClassPathHash)

        val mockServiceRegistry = mock<ServiceRegistry> {
            on { get(GradleUserHomeTemporaryFileProvider::class.java) } doReturn GradleUserHomeTemporaryFileProvider {
                tempFolder.createDir("gradle-user-home")
            }
            on { get(KotlinMetadataCompatibilityChecker::class.java) } doReturn object : KotlinMetadataCompatibilityChecker {
                override fun incompatibleClasspathElements(classPath: ClassPath): List<File> = listOf()
            }
        }

        val host = mock<Interpreter.Host> {

            on { serviceRegistryFor(any(), any()) } doReturn mockServiceRegistry

            on { startCompilerOperation(any()) } doReturn compilerOperation

            on { runCompileBuildOperation(any(), any(), any()) } doAnswer { it.getArgument<() -> String>(2)() }

            on {
                cachedDirFor(
                    any(),
                    eq(stage1ProgramId),
                    same(testRuntimeClassPath),
                    same(accessorsClassPath),
                    any()
                )
            } doAnswer {
                it.getArgument<(File) -> Unit>(4).invoke(stage1CacheDir)
                stage1CacheDir
            }

            on {
                cachedDirFor(
                    any(),
                    eq(stage2ProgramId),
                    same(testRuntimeClassPath),
                    same(accessorsClassPath),
                    any()
                )
            } doAnswer {
                it.getArgument<(File) -> Unit>(4).invoke(stage2CacheDir)
                stage2CacheDir
            }

            on { compilationClassPathOf(any()) } doAnswer { testRuntimeClassPath }
            on { hashOf(eq(testRuntimeClassPath)) } doReturn compilationClassPathHash

            on { accessorsClassPathFor(any()) } doReturn accessorsClassPath
            on { hashOf(eq(accessorsClassPath)) } doReturn accessorsClassPathHash

            on {
                loadClassInChildScopeOf(any(), any(), any(), any(), any(), same(accessorsClassPath))
            } doAnswer {

                val location = it.getArgument<File>(3)
                val className = it.getArgument<String>(4)

                val newLocation = relocate(location)

                DummyCompiledScript(
                    classLoaderFor(newLocation)
                        .also { classLoaders += it }
                        .loadClass(className)
                )
            }

            on { compilerOptions } doReturn KotlinCompilerOptions()
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

                verify(host).cachedClassFor(stage1ProgramId)

                verify(host).compilationClassPathOf(parentScope)

                verify(host).startCompilerOperation(shortScriptDisplayName.displayName)

                verify(compilerOperation).close()

                verify(host).loadClassInChildScopeOf(
                    baseScope,
                    "kotlin-dsl:$scriptPath:$stage1TemplateId",
                    ClassLoaderScopeOrigin.Script(scriptPath, longScriptDisplayName, shortScriptDisplayName),
                    stage1CacheDir,
                    "Program",
                    accessorsClassPath
                )

                verify(host).cache(
                    DummyCompiledScript(classLoaders[0].loadClass("Program")),
                    stage1ProgramId
                )

                verify(host).cachedClassFor(stage2ProgramId)

                verify(host).compilationClassPathOf(targetScope)

                verify(host).startCompilerOperation(shortScriptDisplayName.displayName)

                verify(compilerOperation).close()

                verify(host).loadClassInChildScopeOf(
                    targetScope,
                    "kotlin-dsl:$scriptPath:$stage2TemplateId",
                    ClassLoaderScopeOrigin.Script(scriptPath, longScriptDisplayName, shortScriptDisplayName),
                    stage2CacheDir,
                    "Program",
                    accessorsClassPath
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

    private class TestProgram1 : ExecutableProgram() {
        override fun execute(programHost: ExecutableProgram.Host, scriptHost: KotlinScriptHost<*>) {
            // No-op implementation for testing
        }
    }

    private class TestProgram2 : ExecutableProgram() {
        override fun execute(programHost: ExecutableProgram.Host, scriptHost: KotlinScriptHost<*>) {
            // No-op implementation for testing
        }
    }

    @Test
    fun `eval uses cached program when input is identical`() {
        val scriptText = "println(\"test\")"
        val scriptPath = "/src/settings.gradle.kts"
        val scriptClassName = "Settings_gradle"
        val sourceHash = TestHashCodes.hashCodeFrom(42)

        val scriptSource = createScriptSource(scriptPath, scriptClassName, scriptText)
        val parentClassLoader = mock<ClassLoader>()
        val baseScope = createMockScope(parentClassLoader)
        val targetScope = createMockScope(mock(), baseScope)

        val cachingHost = createCachingHostMock(DummyCompiledScript(TestProgram1::class.java))

        val interpreter = Interpreter(cachingHost.host)
        val target = mock<Settings>()

        // When we eval the same script twice
        interpreter.eval(target, scriptSource, sourceHash, mock(), targetScope, baseScope, true)
        val compilationsAfterFirst = cachingHost.compilationCount

        interpreter.eval(target, scriptSource, sourceHash, mock(), targetScope, baseScope, true)
        val compilationsAfterSecond = cachingHost.compilationCount

        // Then compilation should only happen once (cache hit on the second call)
        assert(compilationsAfterFirst == 1) { "First eval should trigger compilation" }
        assert(compilationsAfterSecond == 1) {
            "Second eval with same script should use cache, not recompile (compilations: $compilationsAfterSecond vs $compilationsAfterFirst)"
        }
    }

    @Test
    fun `eval treats different className as cache miss`() {
        val scriptText = "println(\"test\")"
        val scriptPath = "/src/settings.gradle.kts"
        val sourceHash = TestHashCodes.hashCodeFrom(42)

        // Create two script sources with different classNames
        val scriptSource1 = createScriptSource(scriptPath, "Settings_gradle_v1", scriptText)
        val scriptSource2 = createScriptSource(scriptPath, "Settings_gradle_v2", scriptText)

        val parentClassLoader = mock<ClassLoader>()
        val baseScope = createMockScope(parentClassLoader)
        val targetScope = createMockScope(mock(), baseScope)

        val compiledProgram1 = DummyCompiledScript(TestProgram1::class.java)
        val compiledProgram2 = DummyCompiledScript(TestProgram2::class.java)
        val cachingHost = createCachingHostMock(compiledProgram1, compiledProgram2)

        val interpreter = Interpreter(cachingHost.host)
        val target = mock<Settings>()

        // When we eval the first script with className v1
        interpreter.eval(target, scriptSource1, sourceHash, mock(), targetScope, baseScope, true)
        val compilationsAfterFirst = cachingHost.compilationCount

        // And eval second script with className v2
        interpreter.eval(target, scriptSource2, sourceHash, mock(), targetScope, baseScope, true)
        val compilationsAfterSecond = cachingHost.compilationCount

        // Then both should compile (cache miss due to different className)
        assert(compilationsAfterFirst == 1) { "First script should compile" }
        assert(compilationsAfterSecond == 2) {
            "Second script with different className should also compile (cache miss)"
        }

        // And both should be in the cache with different keys
        assert(cachingHost.programCache.size == 2) { "Cache should contain entries for both classNames" }
        val cachedClassNames = cachingHost.programCache.keys.map { it.className }.toSet()
        assert("Settings_gradle_v1" in cachedClassNames && "Settings_gradle_v2" in cachedClassNames) {
            "Both classNames should be cache keys"
        }
    }

    @Test
    fun `eval treats different scriptFileName as cache miss`() {
        val scriptText = "println(\"test\")"
        val scriptClassName = "Settings_gradle"
        val sourceHash = TestHashCodes.hashCodeFrom(42)

        // Create two script sources with different file names
        val scriptSource1 = createScriptSource("/src/settings.gradle.kts", scriptClassName, scriptText)
        val scriptSource2 = createScriptSource("/src/settings-v2.gradle.kts", scriptClassName, scriptText)

        val parentClassLoader = mock<ClassLoader>()
        val baseScope = createMockScope(parentClassLoader)
        val targetScope = createMockScope(mock(), baseScope)

        val compiledProgram1 = DummyCompiledScript(TestProgram1::class.java)
        val compiledProgram2 = DummyCompiledScript(TestProgram2::class.java)
        val cachingHost = createCachingHostMock(compiledProgram1, compiledProgram2)

        val interpreter = Interpreter(cachingHost.host)
        val target = mock<Settings>()

        // When we eval the first script with the first filename
        interpreter.eval(target, scriptSource1, sourceHash, mock(), targetScope, baseScope, true)
        val compilationsAfterFirst = cachingHost.compilationCount

        // And eval second script with a different filename
        interpreter.eval(target, scriptSource2, sourceHash, mock(), targetScope, baseScope, true)
        val compilationsAfterSecond = cachingHost.compilationCount

        // Then both should compile (cache miss due to different scriptFileName)
        assert(compilationsAfterFirst == 1) { "First script should compile" }
        assert(compilationsAfterSecond == 2) {
            "Second script with different scriptFileName should also compile (cache miss)"
        }

        // And both should be in the cache with different keys
        assert(cachingHost.programCache.size == 2) { "Cache should contain entries for both scriptFileNames" }
        val cachedFileNames = cachingHost.programCache.keys.map { it.scriptFileName }.toSet()
        assert("/src/settings.gradle.kts" in cachedFileNames && "/src/settings-v2.gradle.kts" in cachedFileNames) {
            "Both scriptFileNames should be cache keys"
        }
    }

    @Test
    fun `eval treats different script contents as cache miss`() {
        val scriptPath = "/src/settings.gradle.kts"
        val scriptClassName = "Settings_gradle"

        // Create two script sources with the same fileName and className but different contents
        val scriptText1 = "println(\"version 1\")"
        val scriptText2 = "println(\"version 2\")"
        val sourceHash1 = TestHashCodes.hashCodeFrom(42)
        val sourceHash2 = TestHashCodes.hashCodeFrom(43)

        val scriptSource1 = createScriptSource(scriptPath, scriptClassName, scriptText1)
        val scriptSource2 = createScriptSource(scriptPath, scriptClassName, scriptText2)

        val parentClassLoader = mock<ClassLoader>()
        val baseScope = createMockScope(parentClassLoader)
        val targetScope = createMockScope(mock(), baseScope)

        val compiledProgram1 = DummyCompiledScript(TestProgram1::class.java)
        val compiledProgram2 = DummyCompiledScript(TestProgram2::class.java)
        val cachingHost = createCachingHostMock(compiledProgram1, compiledProgram2)

        val interpreter = Interpreter(cachingHost.host)
        val target = mock<Settings>()

        // When we eval the first script with the first content
        interpreter.eval(target, scriptSource1, sourceHash1, mock(), targetScope, baseScope, true)
        val compilationsAfterFirst = cachingHost.compilationCount

        // And eval a second script with different content (different sourceHash)
        interpreter.eval(target, scriptSource2, sourceHash2, mock(), targetScope, baseScope, true)
        val compilationsAfterSecond = cachingHost.compilationCount

        // Then both should compile (cache miss due to different sourceHash)
        assert(compilationsAfterFirst == 1) { "First script should compile" }
        assert(compilationsAfterSecond == 2) {
            "Second script with different content should also compile (cache miss)"
        }

        // And both should be in the cache with different keys
        assert(cachingHost.programCache.size == 2) { "Cache should contain entries for both sourceHashes" }
        val cachedSourceHashes = cachingHost.programCache.keys.map { it.sourceHash }.toSet()
        assert(sourceHash1 in cachedSourceHashes && sourceHash2 in cachedSourceHashes) {
            "Both sourceHashes should be cache keys"
        }
    }

    private
    class CachingHostMock(
        val host: Interpreter.Host,
        val programCache: MutableMap<ProgramId, CompiledScript>,
        private val compilationCountRef: IntArray
    ) {
        val compilationCount: Int
            get() = compilationCountRef[0]
    }

    private
    fun createCachingHostMock(vararg compiledPrograms: CompiledScript): CachingHostMock {
        val programCache = mutableMapOf<ProgramId, CompiledScript>()
        val compilationCountRef = intArrayOf(0)

        val mockServiceRegistry = mock<ServiceRegistry> {
            on { get(GradleUserHomeTemporaryFileProvider::class.java) } doReturn GradleUserHomeTemporaryFileProvider {
                tempFolder.createDir("gradle-user-home")
            }
            on { get(KotlinMetadataCompatibilityChecker::class.java) } doReturn object : KotlinMetadataCompatibilityChecker {
                override fun incompatibleClasspathElements(classPath: ClassPath): List<File> = listOf()
            }
        }

        val host = mock<Interpreter.Host> {
            on { cachedClassFor(any()) } doAnswer { invocation ->
                val programId = invocation.getArgument<ProgramId>(0)
                programCache[programId]
            }
            on { cache(any(), any()) } doAnswer { invocation ->
                val program = invocation.getArgument<CompiledScript>(0)
                val programId = invocation.getArgument<ProgramId>(1)
                programCache[programId] = program
            }
            on { serviceRegistryFor(any(), any()) } doReturn mockServiceRegistry
            on { compilerOptions } doReturn KotlinCompilerOptions()
            on { compilationClassPathOf(any()) } doReturn testRuntimeClassPath
            on { stage1BlocksAccessorsFor(any()) } doReturn ClassPath.EMPTY
            on { accessorsClassPathFor(any()) } doReturn ClassPath.EMPTY
            on { hashOf(any()) } doReturn TestHashCodes.hashCodeFrom(0)
            on { implicitImports } doReturn emptyList()
            on { startCompilerOperation(any()) } doAnswer {
                compilationCountRef[0]++
                mock<AutoCloseable>()
            }
            on { cachedDirFor(any(), any(), any(), any(), any()) } doAnswer { invocation ->
                val outputDir = root.resolve("compile-${compilationCountRef[0]}").apply { mkdir() }
                invocation.getArgument<(File) -> Unit>(4).invoke(outputDir)
                outputDir
            }
            on { loadClassInChildScopeOf(any(), any(), any(), any(), any(), any()) } doAnswer {
                if (compiledPrograms.isEmpty()) {
                    DummyCompiledScript(TestProgram1::class.java)
                } else {
                    compiledPrograms[(compilationCountRef[0] - 1).coerceIn(0, compiledPrograms.size - 1)]
                }
            }
            on { runCompileBuildOperation(any(), any(), any()) } doAnswer {
                it.getArgument<() -> String>(2)()
            }
        }

        return CachingHostMock(host, programCache, compilationCountRef)
    }

    private
    fun createScriptSource(fileName: String, className: String, text: String): ScriptSource {
        val textResource = mock<TextResource> {
            on { getText() } doReturn text
        }
        val shortDisplayName = Describables.of("<test>")
        val longDisplayName = Describables.of(fileName)
        return mock {
            on { this.fileName } doReturn fileName
            on { this.className } doReturn className
            on { this.shortDisplayName } doReturn shortDisplayName
            on { this.longDisplayName } doReturn longDisplayName
            on { this.displayName } doReturn fileName
            on { resource } doReturn textResource
        }
    }

    private
    fun createMockScope(exportClassLoader: ClassLoader, parent: ClassLoaderScope? = null): ClassLoaderScope = mock {
        on { this.exportClassLoader } doReturn exportClassLoader
        parent?.let { on { this.parent } doReturn it }
    }

    private
    fun relocate(location: File): File {
        val newLocation = location.parentFile.resolve(location.name + "-relocated")
        location.renameTo(newLocation)
        return newLocation
    }
}
