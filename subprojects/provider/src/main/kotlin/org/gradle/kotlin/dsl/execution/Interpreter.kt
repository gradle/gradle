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

import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.initialization.ClassLoaderScope

import org.gradle.groovy.scripts.ScriptSource

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.exceptions.LocationAwareException
import org.gradle.internal.hash.HashCode
import org.gradle.internal.service.ServiceRegistry

import org.gradle.kotlin.dsl.support.KotlinScriptHost
import org.gradle.kotlin.dsl.support.serviceRegistryOf

import java.io.File

import java.lang.reflect.InvocationTargetException


/**
 * An optimised interpreter for the Kotlin DSL based on the idea of
 * [partial evaluation](https://en.wikipedia.org/wiki/Partial_evaluation).
 *
 * Instead of interpreting a given Kotlin DSL script directly, the interpreter emits a
 * specialized program that captures the optimal execution procedure for the particular
 * combination of script structure (does it contain a `buildscript` block? a `plugins` block?
 * a script body? etc), target object and context (top-level or not).
 *
 * The specialized program is then cached via a cheap cache key based on the original,
 * unprocessed contents of the script, the target object type and parent `ClassLoader`.
 *
 * Because each program is specialized to a given script structure, a lot of work is
 * avoided. For example, a top-level script containing a `plugins` block but no body
 * can be compiled down to a specialized program that instantiates the precompiled
 * `plugins` block class directly - without reflection - and does nothing else. The
 * same strategy can be used for a **script plugin** (a non top-level script) with a
 * body but no `buildscript` block since the classpath is completely determined at
 * the time the specialized program is emitted.
 *
 * @see PartialEvaluator
 * @see ResidualProgramCompiler
 */
class Interpreter(val host: Host) {

    interface Host {

        fun cachedClassFor(
            templateId: String,
            sourceHash: HashCode,
            parentClassLoader: ClassLoader
        ): Class<*>?

        fun cache(
            templateId: String,
            sourceHash: HashCode,
            parentClassLoader: ClassLoader,
            specializedProgram: Class<*>
        )

        fun cachedDirFor(
            templateId: String,
            sourceHash: HashCode,
            parentClassLoader: ClassLoader,
            initializer: (File) -> Unit
        ): File

        fun compilationClassPathOf(
            classLoaderScope: ClassLoaderScope
        ): ClassPath

        fun loadClassInChildScopeOf(
            classLoaderScope: ClassLoaderScope,
            childScopeId: String,
            location: File,
            className: String
        ): Class<*>

        fun closeTargetScopeOf(scriptHost: KotlinScriptHost<*>)

        val implicitImports: List<String>
    }

    fun eval(
        target: Any,
        scriptSource: ScriptSource,
        scriptHandler: ScriptHandler,
        targetScope: ClassLoaderScope,
        baseScope: ClassLoaderScope,
        topLevelScript: Boolean
    ) {

        val sourceText =
            scriptSource.resource!!.text

        val sourceHash =
            scriptSourceHash(sourceText)

        val programKind =
            if (topLevelScript) ProgramKind.TopLevel
            else ProgramKind.ScriptPlugin

        val programTarget =
            programTargetFor(target)

        val templateId =
            programTarget.name + "/" + programKind.name + "/stage1"

        val parentClassLoader =
            baseScope.exportClassLoader

        val cachedProgram =
            host.cachedClassFor(templateId, sourceHash, parentClassLoader)

        val scriptHost =
            scriptHostFor(programTarget, target, scriptSource, scriptHandler, targetScope, baseScope)

        if (cachedProgram != null) {
            eval(cachedProgram, scriptHost)
            return
        }

        val specializedProgram =
            emitSpecializedProgramFor(
                scriptSource,
                sourceText,
                sourceHash,
                templateId,
                parentClassLoader,
                targetScope,
                baseScope,
                programKind,
                programTarget)

        host.cache(
            templateId,
            sourceHash,
            parentClassLoader,
            specializedProgram)

        eval(specializedProgram, scriptHost)
    }

    private
    fun programTargetFor(target: Any): ProgramTarget =
        when (target) {
            is Settings -> ProgramTarget.Settings
            is Project -> ProgramTarget.Project
            else -> throw IllegalArgumentException("Unsupported target: $target")
        }

    private
    fun scriptHostFor(
        programTarget: ProgramTarget,
        target: Any,
        scriptSource: ScriptSource,
        scriptHandler: ScriptHandler,
        targetScope: ClassLoaderScope,
        baseScope: ClassLoaderScope
    ) =
        KotlinScriptHost(
            target,
            scriptSource,
            scriptHandler,
            targetScope,
            baseScope,
            serviceRegistryFor(programTarget, target))

    private
    fun serviceRegistryFor(programTarget: ProgramTarget, target: Any): ServiceRegistry = when (programTarget) {
        ProgramTarget.Project -> serviceRegistryOf(target as Project)
        ProgramTarget.Settings -> serviceRegistryOf(target as Settings)
    }

    private
    fun emitSpecializedProgramFor(
        scriptSource: ScriptSource,
        sourceText: String,
        sourceHash: HashCode,
        templateId: String,
        parentClassLoader: ClassLoader,
        targetScope: ClassLoaderScope,
        baseScope: ClassLoaderScope,
        programKind: ProgramKind,
        programTarget: ProgramTarget
    ): Class<*> {

        val scriptPath =
            scriptSource.fileName!!

        val cachedDir =
            host.cachedDirFor(templateId, sourceHash, parentClassLoader) { cachedDir ->

                val outputDir =
                    cachedDir.resolve("stage1").apply { mkdir() }

                val residualProgram =
                    PartialEvaluator.reduce(ProgramSource(scriptPath, sourceText), programKind)

                residualProgramCompilerFor(
                    sourceHash,
                    outputDir,
                    targetScope.parent,
                    programKind,
                    programTarget
                ).compile(residualProgram)
            }

        val classesDir =
            cachedDir.resolve("stage1")

        return loadClassInChildScopeOf(
            baseScope,
            scriptPath,
            classesDir,
            "stage1")
    }

    private
    fun loadClassInChildScopeOf(
        baseScope: ClassLoaderScope,
        scriptPath: String,
        classesDir: File,
        stage: String
    ): Class<*> =

        host.loadClassInChildScopeOf(
            baseScope,
            childScopeId = classLoaderScopeIdFor(scriptPath, stage),
            location = classesDir,
            className = "Program")

    private
    fun residualProgramCompilerFor(
        sourceHash: HashCode,
        outputDir: File,
        classLoaderScopeForClassPath: ClassLoaderScope,
        programKind: ProgramKind,
        programTarget: ProgramTarget
    ): ResidualProgramCompiler =

        ResidualProgramCompiler(
            outputDir,
            host.compilationClassPathOf(classLoaderScopeForClassPath),
            sourceHash,
            programKind,
            programTarget,
            host.implicitImports)

    private
    fun eval(specializedProgram: Class<*>, scriptHost: KotlinScriptHost<*>) {
        (specializedProgram.newInstance() as ExecutableProgram)
            .execute(programHost, scriptHost)
    }

    private
    val programHost = ProgramHost()

    private
    inner class ProgramHost : ExecutableProgram.Host {

        override fun handleScriptException(
            exception: Throwable,
            scriptClass: Class<*>,
            scriptHost: KotlinScriptHost<*>
        ) {
            locationAwareExceptionHandlingFor(exception, scriptClass, scriptHost.scriptSource)
        }

        override fun closeTargetScopeOf(scriptHost: KotlinScriptHost<*>) {
            host.closeTargetScopeOf(scriptHost)
        }

        override fun evaluateSecondStageOf(
            program: ExecutableProgram.StagedProgram,
            scriptHost: KotlinScriptHost<*>,
            scriptTemplateId: String,
            sourceHash: HashCode
        ) {
            val parentClassLoader =
                scriptHost.targetScope.localClassLoader

            val cachedProgram =
                host.cachedClassFor(scriptTemplateId, sourceHash, parentClassLoader)

            if (cachedProgram != null) {
                eval(cachedProgram, scriptHost)
                return
            }

            val specializedProgram =
                program.loadSecondStageFor(this, scriptHost, scriptTemplateId, sourceHash)

            host.cache(
                scriptTemplateId,
                sourceHash,
                parentClassLoader,
                specializedProgram)

            eval(specializedProgram, scriptHost)
        }

        override fun compileSecondStageScript(
            scriptPath: String,
            originalScriptPath: String,
            scriptHost: KotlinScriptHost<*>,
            scriptTemplateId: String,
            sourceHash: HashCode,
            programKind: ProgramKind,
            programTarget: ProgramTarget
        ): Class<*> {

            val cacheDir =
                host.cachedDirFor(scriptTemplateId, sourceHash, scriptHost.targetScope.localClassLoader) { outputDir ->
                    residualProgramCompilerFor(
                        sourceHash,
                        outputDir,
                        scriptHost.targetScope,
                        programKind,
                        programTarget
                    ).emitStage2ProgramFor(
                        File(scriptPath),
                        originalScriptPath
                    )
                }

            return loadClassInChildScopeOf(
                scriptHost.targetScope,
                originalScriptPath,
                cacheDir,
                "stage2")
        }
    }
}


internal
fun classLoaderScopeIdFor(scriptPath: String, stage: String) =
    "kotlin-dsl:$scriptPath:$stage"


internal
fun locationAwareExceptionHandlingFor(e: Throwable, scriptClass: Class<*>, scriptSource: ScriptSource) {
    val targetException = maybeUnwrapInvocationTargetException(e)
    val locationAware = locationAwareExceptionFor(targetException, scriptClass, scriptSource)
    throw locationAware ?: targetException
}


private
fun locationAwareExceptionFor(
    original: Throwable,
    scriptClass: Class<*>,
    scriptSource: ScriptSource
): LocationAwareException? {

    val scriptClassName = scriptClass.name
    val scriptClassNameInnerPrefix = "$scriptClassName$"

    fun scriptStackTraceElement(element: StackTraceElement) =
        element.className?.run {
            equals(scriptClassName) || startsWith(scriptClassNameInnerPrefix)
        } == true

    tailrec fun inferLocationFrom(exception: Throwable): LocationAwareException? {

        if (exception is LocationAwareException) {
            return exception
        }

        exception.stackTrace.find(::scriptStackTraceElement)?.run {
            return LocationAwareException(original, scriptSource, lineNumber.takeIf { it >= 0 })
        }

        val cause = exception.cause ?: return null
        return inferLocationFrom(cause)
    }

    return inferLocationFrom(original)
}


private
fun maybeUnwrapInvocationTargetException(e: Throwable) =
    if (e is InvocationTargetException) e.targetException
    else e
