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

package org.gradle.kotlin.dsl.execution

import com.nhaarman.mockito_kotlin.KStubbing
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.TestHashCodes
import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.fixtures.testRuntimeClassPath
import org.gradle.kotlin.dsl.fixtures.withClassLoaderFor
import org.gradle.kotlin.dsl.support.KotlinScriptHost
import org.gradle.plugin.management.PluginManagementSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import java.io.File


abstract class TestWithCompiler : TestWithTempFiles() {

    @Rule
    @JvmField
    val tmpDir = TestNameTestDirectoryProvider(this::class.java)

    internal
    inline fun withExecutableProgramFor(
        program: ResidualProgram,
        sourceHash: HashCode = TestHashCodes.hashCodeFrom(0),
        programKind: ProgramKind = ProgramKind.TopLevel,
        programTarget: ProgramTarget = ProgramTarget.Settings,
        action: ExecutableProgram.() -> Unit
    ) {

        outputDir().let { outputDir ->
            compileProgramTo(outputDir, program, sourceHash, programKind, programTarget)
            withClassLoaderFor(outputDir) {
                val executableProgram = loadClass("Program").getDeclaredConstructor().newInstance()
                action(executableProgram as ExecutableProgram)
            }
        }
    }

    private
    fun compileProgramTo(
        outputDir: File,
        program: ResidualProgram,
        sourceHash: HashCode,
        programKind: ProgramKind,
        programTarget: ProgramTarget
    ) {
        ResidualProgramCompiler(
            outputDir,
            JavaVersion.current(),
            false,
            testRuntimeClassPath,
            sourceHash,
            programKind,
            programTarget,
            temporaryFileProvider = TestFiles.tmpDirTemporaryFileProvider(tmpDir.testDirectory)
        ).compile(program)
    }

    private
    fun outputDir() = root.resolve("classes").apply { mkdir() }

    fun scriptHostWith(
        target: Any = mock(),
        scriptHandler: ScriptHandlerInternal = mock()
    ) = KotlinScriptHost(target, scriptSource(), scriptHandler, mock(), mock(), mock {
        on { get(ObjectFactory::class.java) } doAnswer { mock<ObjectFactory>() }
    })

    private
    fun scriptSource(): ScriptSource = mock { on { fileName } doReturn "script.gradle.kts" }
}


internal
inline fun mockSettings(
    pluginManagementSpec: PluginManagementSpec = mock(),
    stubbing: KStubbing<Settings>.(Settings) -> Unit = {}
): Settings = mock {
    on { pluginManagement } doReturn pluginManagementSpec
    on { pluginManagement(any<Action<PluginManagementSpec>>()) } doAnswer { invocation ->
        invocation
            .getArgument<Action<PluginManagementSpec>>(0)
            .execute(pluginManagementSpec)
    }
    stubbing(it)
}


/**
 * Makes a mock instance of the [ExecutableProgram.Host] that will throw an exception if
 * [ExecutableProgram.Host.handleScriptException] is called.
 */
internal
inline fun safeMockProgramHost(stubbing: KStubbing<ExecutableProgram.Host>.(ExecutableProgram.Host) -> Unit = {}) =
    mock<ExecutableProgram.Host> {
        // Add a custom exception handler so that they don't get clobbered.
        on { handleScriptException(any(), any(), any()) } doAnswer { invocation ->
            val throwable: Throwable = invocation.getArgument(0)
            val scriptClass: Class<*> = invocation.getArgument(1)
            throw AssertionError(
                "Unexpected script exception executing script class `$scriptClass`",
                throwable
            )
        }
        stubbing(it)
    }
