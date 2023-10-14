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

package org.gradle.kotlin.dsl.fixtures

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.gradle.api.JavaVersion
import org.gradle.api.internal.file.temp.GradleUserHomeTemporaryFileProvider
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.configuration.DefaultImportsReader
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.initialization.ClassLoaderScopeOrigin
import org.gradle.internal.Describables
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.resource.StringTextResource
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.ServiceRegistry
import org.gradle.kotlin.dsl.execution.CompiledScript
import org.gradle.kotlin.dsl.execution.Interpreter
import org.gradle.kotlin.dsl.execution.ProgramId
import org.gradle.kotlin.dsl.execution.ProgramTarget
import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.KotlinScriptHost
import org.gradle.plugin.management.internal.PluginRequests
import java.io.File
import java.net.URLClassLoader


/**
 * Evaluates the given Kotlin [script] against the given [target] writing compiled classes
 * to sub-directories of [baseCacheDir].
 */
fun eval(
    script: String,
    target: Any,
    baseCacheDir: File,
    baseTempDir: File,
    scriptCompilationClassPath: ClassPath = testRuntimeClassPath,
    scriptRuntimeClassPath: ClassPath = ClassPath.EMPTY
) {
    SimplifiedKotlinScriptEvaluator(
        baseCacheDir,
        baseTempDir,
        scriptCompilationClassPath,
        scriptRuntimeClassPath = scriptRuntimeClassPath
    ).use {
        it.eval(script, target)
    }
}


/**
 * A simplified Service Registry, suitable for cheaper testing of the DSL outside of Gradle.
 */
private
class SimplifiedKotlinDefaultServiceRegistry(
    private val baseTempDir: File,
) : DefaultServiceRegistry() {
    init {
        register {
            add(GradleUserHomeTemporaryFileProvider::class.java, GradleUserHomeTemporaryFileProvider { baseTempDir })
        }
    }
}


/**
 * A simplified Kotlin script evaluator, suitable for cheaper testing of the DSL outside Gradle.
 */
private
class SimplifiedKotlinScriptEvaluator(
    private val baseCacheDir: File,
    private val baseTempDir: File,
    private val scriptCompilationClassPath: ClassPath,
    private val serviceRegistry: ServiceRegistry = SimplifiedKotlinDefaultServiceRegistry(baseTempDir),
    private val scriptRuntimeClassPath: ClassPath = ClassPath.EMPTY
) : AutoCloseable {

    fun eval(script: String, target: Any, topLevelScript: Boolean = false) {
        Interpreter(InterpreterHost()).eval(
            target,
            scriptSourceFor(script),
            Hashing.md5().hashString(script),
            mock(),
            targetScope,
            baseScope,
            topLevelScript
        )
    }

    override fun close() {
        classLoaders.forEach(URLClassLoader::close)
    }

    private
    val classLoaders = mutableListOf<URLClassLoader>()

    private
    val parentClassLoader = mock<ClassLoader>()

    private
    val baseScope = mock<ClassLoaderScope>(name = "baseScope") {
        on { exportClassLoader } doReturn parentClassLoader
    }

    private
    val targetScopeExportClassLoader = mock<ClassLoader>()

    private
    val targetScope = mock<ClassLoaderScope>(name = "targetScope") {
        on { parent } doReturn baseScope
        on { exportClassLoader } doReturn targetScopeExportClassLoader
    }

    private
    fun scriptSourceFor(script: String): ScriptSource = mock {
        on { fileName } doReturn "script.gradle.kts"
        on { shortDisplayName } doReturn Describables.of("<test script>")
        on { resource } doReturn StringTextResource("<test script>", script)
    }

    private
    inner class InterpreterHost : Interpreter.Host {

        override fun serviceRegistryFor(programTarget: ProgramTarget, target: Any): ServiceRegistry =
            serviceRegistry

        override fun cachedClassFor(programId: ProgramId): CompiledScript? =
            null

        override fun cache(specializedProgram: CompiledScript, programId: ProgramId) = Unit

        override fun cachedDirFor(
            scriptHost: KotlinScriptHost<*>,
            programId: ProgramId,
            compilationClassPath: ClassPath,
            accessorsClassPath: ClassPath,
            initializer: (File) -> Unit
        ): File = baseCacheDir.resolve(programId.sourceHash.toString()).resolve(programId.templateId).also { cacheDir ->
            cacheDir.mkdirs()
            initializer(cacheDir)
        }

        override fun compilationClassPathOf(classLoaderScope: ClassLoaderScope): ClassPath =
            scriptCompilationClassPath

        override fun abiClassPathOf(classPath: ClassPath): ClassPath =
            classPath

        override fun stage1BlocksAccessorsFor(scriptHost: KotlinScriptHost<*>): ClassPath =
            ClassPath.EMPTY

        override fun accessorsClassPathFor(scriptHost: KotlinScriptHost<*>): ClassPath =
            ClassPath.EMPTY

        override fun startCompilerOperation(description: String): AutoCloseable =
            mock()

        override fun loadClassInChildScopeOf(
            classLoaderScope: ClassLoaderScope,
            childScopeId: String,
            origin: ClassLoaderScopeOrigin,
            location: File,
            className: String,
            accessorsClassPath: ClassPath
        ): CompiledScript =
            DummyCompiledScript(
                classLoaderFor(scriptRuntimeClassPath + DefaultClassPath.of(location))
                    .also { classLoaders += it }
                    .loadClass(className)
            )

        override fun applyPluginsTo(scriptHost: KotlinScriptHost<*>, pluginRequests: PluginRequests) = Unit

        override fun applyBasePluginsTo(project: ProjectInternal) = Unit

        override fun setupEmbeddedKotlinFor(scriptHost: KotlinScriptHost<*>) = Unit

        override fun closeTargetScopeOf(scriptHost: KotlinScriptHost<*>) = Unit

        override fun hashOf(classPath: ClassPath): HashCode =
            TestHashCodes.hashCodeFrom(0)

        override fun runCompileBuildOperation(scriptPath: String, stage: String, action: () -> String): String =
            action()

        override fun onScriptClassLoaded(scriptSource: ScriptSource, specializedProgram: Class<*>) = Unit

        override val implicitImports: List<String>
            get() = ImplicitImports(DefaultImportsReader()).list

        override val jvmTarget: JavaVersion
            get() = JavaVersion.current()

        override val allWarningsAsErrors: Boolean
            get() = false
    }
}


class DummyCompiledScript(override val program: Class<*>) : CompiledScript {

    override val classPath: ClassPath
        get() = ClassPath.EMPTY

    override fun onReuse() {
    }

    override fun equals(other: Any?) = when {
        other === this -> true
        other == null || other.javaClass != this.javaClass -> false
        else -> program == (other as DummyCompiledScript).program
    }
}


val testRuntimeClassPath: ClassPath by lazy {
    ClasspathUtil.getClasspath(SimplifiedKotlinScriptEvaluator::class.java.classLoader)
}
