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
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.initialization.ClassLoaderScope

import org.gradle.groovy.scripts.ScriptSource

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.hash.HashCode
import org.gradle.kotlin.dsl.KotlinSettingsScript

import org.gradle.kotlin.dsl.support.KotlinScriptHost
import org.gradle.kotlin.dsl.support.kotlinScriptHostFor
import org.gradle.kotlin.dsl.support.loggerFor

import java.io.File


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

        fun cache(
            templateId: String,
            sourceHash: HashCode,
            parentClassLoader: ClassLoader,
            specializedProgram: Class<*>
        )

        fun closeTargetScopeOf(scriptHost: KotlinScriptHost<*>)

        val implicitImports: List<String>
    }

    private
    val logger =
        loggerFor<Interpreter>()

    private
    val programHost =
        object : ExecutableProgram.Host {

            override fun closeTargetScopeOf(scriptHost: KotlinScriptHost<*>) {
                host.closeTargetScopeOf(scriptHost)
            }

            override fun evaluateDynamicScriptOf(
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
                    program.loadScriptFor(this, scriptHost)

                host.cache(
                    scriptTemplateId,
                    sourceHash,
                    parentClassLoader,
                    specializedProgram)

                eval(specializedProgram, scriptHost)
            }

            override fun compileScriptOf(
                scriptHost: KotlinScriptHost<*>,
                scriptPath: String,
                originalPath: String,
                sourceHash: HashCode
            ): Class<*> {

                val cacheDir =
                    host.cachedDirFor(KotlinSettingsScript::class.qualifiedName!!, sourceHash, scriptHost.targetScope.localClassLoader) {
                        residualProgramCompilerFor(it, scriptHost.targetScope)
                            .emitStage2ProgramFor(File(scriptPath), originalPath)
                    }

                return loadClassInChildScopeOf(
                    scriptHost.targetScope,
                    originalPath,
                    cacheDir,
                    "stage2")
            }
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

        val templateId =
            ExecutableProgram::class.qualifiedName!!

        val parentClassLoader =
            baseScope.exportClassLoader

        val cachedProgram =
            host.cachedClassFor(templateId, sourceHash, parentClassLoader)

        val scriptHost =
            kotlinScriptHostFor(target as Settings, scriptSource, scriptHandler, targetScope, baseScope)

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
                baseScope)

        host.cache(
            templateId,
            sourceHash,
            parentClassLoader,
            specializedProgram)

        eval(specializedProgram, scriptHost)
    }

    private
    fun emitSpecializedProgramFor(
        scriptSource: ScriptSource,
        sourceText: String,
        sourceHash: HashCode,
        templateId: String,
        parentClassLoader: ClassLoader,
        targetScope: ClassLoaderScope,
        baseScope: ClassLoaderScope
    ): Class<*> {

        val scriptPath =
            scriptSource.fileName!!

        val cachedDir =
            host.cachedDirFor(templateId, sourceHash, parentClassLoader) { cachedDir ->

                val outputDir =
                    cachedDir.resolve("stage1").apply { mkdir() }

                val residualProgram =
                    PartialEvaluator.reduce(ProgramSource(scriptPath, sourceText))

                residualProgramCompilerFor(outputDir, targetScope.parent)
                    .compile(residualProgram)
            }

        val classesDir =
            cachedDir.resolve("stage1")

        return loadClassInChildScopeOf(baseScope, scriptPath, classesDir, "stage1")
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
    fun residualProgramCompilerFor(outputDir: File, classLoaderScopeForClassPath: ClassLoaderScope): ResidualProgramCompiler =
        ResidualProgramCompiler(
            outputDir,
            logger,
            host.compilationClassPathOf(classLoaderScopeForClassPath),
            host.implicitImports)

    private
    fun eval(specializedProgram: Class<*>, scriptHost: KotlinScriptHost<*>) {
        (specializedProgram.newInstance() as ExecutableProgram)
            .execute(programHost, scriptHost)
    }
}


internal
fun classLoaderScopeIdFor(scriptPath: String, stage: String) =
    "kotlin-dsl:$scriptPath:$stage"
