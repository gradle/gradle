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

import org.gradle.api.Project
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.execution.BatchScriptCompiler
import org.gradle.groovy.scripts.internal.ScriptSourceHasher
import org.gradle.initialization.ClassLoaderScopeOrigin
import org.gradle.kotlin.dsl.accessors.Stage1BlocksAccessorClassPathGenerator
import org.gradle.kotlin.dsl.execution.ResidualProgramCompiler.BatchItem
import org.gradle.kotlin.dsl.provider.KotlinScriptClassPathProvider
import org.gradle.kotlin.dsl.provider.KotlinScriptClassloadingCache
import org.gradle.kotlin.dsl.provider.compiledScriptOf
import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.kotlinCompilerOptions
import org.gradle.kotlin.dsl.support.serviceOf
import javax.inject.Inject


internal
class KotlinDslBatchScriptCompiler @Inject constructor(
    @Suppress("unused") private val scriptCache: KotlinScriptClassloadingCache,
    private val implicitImports: ImplicitImports,
    private val scriptSourceHasher: ScriptSourceHasher,
    private val globalScopedCacheBuilderFactory: GlobalScopedCacheBuilderFactory
) : BatchScriptCompiler {

    companion object {

        fun isEnabled() = java.lang.Boolean.getBoolean("org.gradle.internal.kotlin-dsl.batch")

        fun maybeExpectCacheHit(target: Any, programId: ProgramId) {
            if (isEnabled() && target is Project && target.parent != null) {
                error("Expecting cache hit for $target ($programId)")
            }
        }
    }

    override fun compile(parent: ProjectState, children: List<ProjectState>) {

        if (!isEnabled() || isBatchCompiled(parent)) {
            return
        }

        // acquire cache directory...
        // parse
        // partially evaluate
        // batch emit bytecode for all ResidualProgram at this point
        // scriptCache.put(...) all scripts

        globalScopedCacheBuilderFactory.createCacheBuilder("batch-compiler").open().use { builder ->
            val programKind = ProgramKind.TopLevel
            val programTarget = ProgramTarget.Project
            val outputDir = builder.baseDir
            val parentProject = parent.mutableModel
            val temporaryFileProvider = parentProject.serviceOf<TemporaryFileProvider>()
            val options = kotlinCompilerOptions(parentProject.serviceOf<GradleProperties>())
            val stage1BlocksAccessorsClassPath = parentProject.serviceOf<Stage1BlocksAccessorClassPathGenerator>()
                .stage1BlocksAccessorClassPath(parentProject)
                .bin
            val templateId = templateIdFor(programTarget, programKind, "stage1")

            // TODO: check what's the right scope to use here
            val compilationClassPath = parentProject.serviceOf<KotlinScriptClassPathProvider>()
                .compilationClassPathOf(parentProject.baseClassLoaderScope)

            val compiler = ResidualProgramCompiler(
                outputDir = outputDir,
                compilerOptions = options,
                classPath = compilationClassPath,
                programKind = programKind,
                programTarget = programTarget,
                implicitImports = implicitImports.list,
                logger = interpreterLogger,
                temporaryFileProvider = temporaryFileProvider,
                //            compileBuildOperationRunner = host::runCompileBuildOperation,
                stage1BlocksAccessorsClassPath = stage1BlocksAccessorsClassPath,
            )

            val batchItems = ArrayList<BatchItem>(children.size)
            for (child in children) {
                val project = child.mutableModel
                val scriptSource = project.buildScriptSource
                val scriptPath = scriptSource.fileName!!
                val sourceHash = scriptSourceHasher.hash(scriptSource)
                val sourceText = scriptSource.resource!!.text
                val programSource = ProgramSource(scriptPath, sourceText)
                val program = ProgramParser.parse(programSource, programKind, programTarget)
                val residualProgram = program.map(
                    PartialEvaluator(programKind, programTarget)::reduce
                )
                batchItems.add(
                    BatchItem(
                        sourceHash,
                        residualProgram.packageName,
                        residualProgram.document
                    )
                )
            }

            compiler.compileBatch(batchItems)

            for (child in children) {
                val project = child.mutableModel
                val scriptSource = project.buildScriptSource
                val scriptPath = scriptSource.fileName!!
                val sourceHash = scriptSourceHasher.hash(scriptSource)
                val baseScope = project.baseClassLoaderScope
                val parentClassLoader = baseScope.exportClassLoader
                val programId =
                    ProgramId(
                        templateId,
                        sourceHash,
                        parentClassLoader,
                        compilerOptions = options
                    )

                val compiledScript = compiledScriptOf(
                    location = outputDir,
                    accessorsClassPath = stage1BlocksAccessorsClassPath,
                    baseScope,
                    childScopeId = classLoaderScopeIdFor(scriptPath, templateId),
                    origin = ClassLoaderScopeOrigin.Script(
                        scriptSource.fileName,
                        scriptSource.longDisplayName,
                        scriptSource.shortDisplayName
                    ),
                    className = "P$sourceHash"
                )
                scriptCache.put(programId, compiledScript)
            }
        }
    }

    private fun isBatchCompiled(project: ProjectState): Boolean {
        val guard = javaClass.getName()
        val extraProperties = project.mutableModel.extensions.extraProperties
        if (extraProperties.has(guard)) {
            return true
        }
        extraProperties.set(guard, guard)
        return false
    }

}
