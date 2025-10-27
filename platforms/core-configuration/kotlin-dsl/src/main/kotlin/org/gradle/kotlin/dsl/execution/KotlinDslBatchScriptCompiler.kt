/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.execution.BatchScriptCompiler
import org.gradle.groovy.scripts.internal.ScriptSourceHasher
import org.gradle.initialization.ClassLoaderScopeOrigin
import org.gradle.kotlin.dsl.accessors.Stage1BlocksAccessorClassPathGenerator
import org.gradle.kotlin.dsl.provider.KotlinScriptClassPathProvider
import org.gradle.kotlin.dsl.provider.KotlinScriptClassloadingCache
import org.gradle.kotlin.dsl.provider.compiledScriptOf
import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.KotlinCompilerOptions
import org.gradle.kotlin.dsl.support.kotlinCompilerOptions
import org.gradle.kotlin.dsl.support.serviceOf
import java.io.File
import javax.inject.Inject


internal
class KotlinDslBatchScriptCompiler @Inject constructor(
    @Suppress("unused") private val scriptCache: KotlinScriptClassloadingCache,
    private val implicitImports: ImplicitImports,
    private val scriptSourceHasher: ScriptSourceHasher,
    private val globalScopedCacheBuilderFactory: GlobalScopedCacheBuilderFactory
) : BatchScriptCompiler {

    override fun compile(parent: ProjectState, children: List<ProjectState>) {
        // acquire cache directory...
        // parse
        // partially evaluate
        // batch emit bytecode for all ResidualProgram at this point
        // scriptCache.put(...) all scripts

        globalScopedCacheBuilderFactory.createCacheBuilder("batch-compiler").open().use { builder ->
            for (child in children) {
                compileSingle(
                    child,
                    builder.baseDir.resolve(child.name),
                    parent.mutableModel.serviceOf(),
                    kotlinCompilerOptions(parent.mutableModel.serviceOf<GradleProperties>())
                )
            }
        }
    }

    private fun compileSingle(
        child: ProjectState,
        outputDir: File,
        temporaryFileProvider: TemporaryFileProvider,
        options: KotlinCompilerOptions
    ) {
        val programKind = ProgramKind.TopLevel
        val programTarget = ProgramTarget.Project

        val project = child.mutableModel

        val classPathProvider = project.serviceOf<KotlinScriptClassPathProvider>()
        val targetScope = project.classLoaderScope
        val baseScope = project.baseClassLoaderScope
        val stage1BlocksAccessorsClassPath = project.serviceOf<Stage1BlocksAccessorClassPathGenerator>().stage1BlocksAccessorClassPath(project).bin

        val compilationClassPath = classPathProvider.compilationClassPathOf(targetScope.parent)
        val scriptSource = project.buildScriptSource
        val scriptPath = scriptSource.fileName!!

        val templateId =
            templateIdFor(programTarget, programKind, "stage1")

        val parentClassLoader =
            baseScope.exportClassLoader

        val sourceHash = scriptSourceHasher.hash(scriptSource)

        val programId =
            ProgramId(
                templateId,
                sourceHash,
                parentClassLoader,
                compilerOptions = options
            )

        val sourceText =
            scriptSource.resource!!.text

        val programSource =
            ProgramSource(scriptPath, sourceText)

        val program =
            ProgramParser.parse(programSource, programKind, programTarget)

        val residualProgram = program.map(
            PartialEvaluator(programKind, programTarget)::reduce
        )

        ResidualProgramCompiler(
            outputDir = outputDir,
            compilerOptions = options,
            classPath = compilationClassPath,
            originalSourceHash = programId.sourceHash,
            programKind = programKind,
            programTarget = programTarget,
            implicitImports = implicitImports.list,
            logger = interpreterLogger,
            temporaryFileProvider = temporaryFileProvider,
//            compileBuildOperationRunner = host::runCompileBuildOperation,
            stage1BlocksAccessorsClassPath = stage1BlocksAccessorsClassPath,
            packageName = residualProgram.packageName,
        ).compile(residualProgram.document)

        val compiledScript = compiledScriptOf(
            location = outputDir,
            accessorsClassPath = stage1BlocksAccessorsClassPath,
            baseScope,
            childScopeId = classLoaderScopeIdFor(scriptPath, templateId),
            origin = ClassLoaderScopeOrigin.Script(scriptSource.fileName, scriptSource.longDisplayName, scriptSource.shortDisplayName),
            className = "Program"
        )
        scriptCache.put(programId, compiledScript)
    }
}
