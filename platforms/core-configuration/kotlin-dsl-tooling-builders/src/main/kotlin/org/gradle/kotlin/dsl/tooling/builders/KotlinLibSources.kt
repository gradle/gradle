/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.kotlin.dsl.tooling.builders

import org.gradle.api.Project
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.query.ArtifactResolutionQuery
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.initialization.dsl.ScriptHandler.CLASSPATH_CONFIGURATION
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.jvm.JvmLibrary
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.artifact.SourcesArtifact
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
import org.gradle.tooling.provider.model.internal.IntermediateToolingModelProvider

internal
data class KotlinLibSourceParameter(
    val skipDependencies: Set<ComponentIdentifier>,
    val resolveKotlinLibSources: Boolean
)


internal
data class KotlinLibsSources(
    val resolvedDependencies: List<ComponentIdentifier>,
    val sourcePath: ClassPath,
    val kotlinLibSourcePath: ClassPath
)


internal
object IsolatedKotlinLibSourceBuilder : ParameterizedToolingModelBuilder<KotlinLibSourceParameter> {

    override fun canBuild(modelName: String): Boolean =
        modelName == KotlinLibsSources::class.qualifiedName

    override fun getParameterType(): Class<KotlinLibSourceParameter> = KotlinLibSourceParameter::class.java

    override fun buildAll(modelName: String, parameter: KotlinLibSourceParameter, project: Project): KotlinLibsSources {
        return build(project, parameter)
    }

    override fun buildAll(modelName: String, project: Project): KotlinLibsSources {
        return build(project, KotlinLibSourceParameter(emptySet(), true))
    }

    private
    fun build(project: Project, parameter: KotlinLibSourceParameter): KotlinLibsSources {
        return kotlinLibsSources(project, parameter.skipDependencies, parameter.resolveKotlinLibSources)
    }
}


internal
fun interface SourcePathBuilder {
    fun buildSourcePath(): ClassPath
}


internal
class ScriptHandlerSourcePathBuilder(
    private val scriptHandlers: List<ScriptHandler>,
) : SourcePathBuilder {

    constructor(scriptHandler: ScriptHandler) : this(listOf(scriptHandler))

    override fun buildSourcePath(): ClassPath = sourcePathFor(scriptHandlers)

}


internal
class ProjectScriptSourcePathBuilder(
    private val project: Project,
    private val intermediateToolingModelProvider: IntermediateToolingModelProvider
) : SourcePathBuilder {

    override fun buildSourcePath(): ClassPath {
        var sourcePath = ClassPath.EMPTY
        var kotlinLibSourcePath = ClassPath.EMPTY

        val resolvedDependencies = hashSetOf<ComponentIdentifier>()
        val rootToCurrent = project.projectHierarchyPath
        for (projectInHierarchy in rootToCurrent) {
            val isolatedSources = resolveSourcesImpl(projectInHierarchy, resolvedDependencies.toSet(), kotlinLibSourcePath.isEmpty)
            resolvedDependencies += isolatedSources.resolvedDependencies
            sourcePath += isolatedSources.sourcePath

            if (kotlinLibSourcePath.isEmpty) {
                kotlinLibSourcePath = isolatedSources.kotlinLibSourcePath
            }
        }

        if (!containsBuiltinKotlinModules(resolvedDependencies)) {
            sourcePath += kotlinLibSourcePath
        }

        return sourcePath
    }

    private
    fun resolveSourcesImpl(projectInHierarchy: Project, skipDependencies: Set<ComponentIdentifier>, resolveKotlinLibSources: Boolean): KotlinLibsSources {
        return if (project == projectInHierarchy) {
            kotlinLibsSources(project, skipDependencies, resolveKotlinLibSources)
        } else {
            resolveSourcesForOther(projectInHierarchy, skipDependencies, resolveKotlinLibSources)
        }
    }

    private
    fun resolveSourcesForOther(other: Project, skipDependencies: Set<ComponentIdentifier>, resolveKotlinLibSources: Boolean): KotlinLibsSources {
        val parameter = KotlinLibSourceParameter(skipDependencies, resolveKotlinLibSources)
        return intermediateToolingModelProvider.getModels(listOf(other), KotlinLibsSources::class.java, parameter).first()
    }
}


private
fun kotlinLibsSources(
    project: Project,
    skipDependencies: Set<ComponentIdentifier>,
    resolveKotlinLibSources: Boolean
): KotlinLibsSources {
    val buildscript = project.buildscript
    val unresolvedDependencies = classpathDependenciesOf(buildscript, skipDependencies)
    val sourcePath = if (unresolvedDependencies.isEmpty()) ClassPath.EMPTY else resolveSources(buildscript, unresolvedDependencies)
    val kotlinLibSourcePath = if (resolveKotlinLibSources) kotlinLibSourcesFor(listOf(buildscript)) else ClassPath.EMPTY
    return KotlinLibsSources(unresolvedDependencies, sourcePath, kotlinLibSourcePath)
}


/**
 * List of parent projects from the root and down to the current project.
 */
private
val Project.projectHierarchyPath: List<Project>
    get() = sequence {
        var project = this@projectHierarchyPath
        yield(project)
        while (project != project.rootProject) {
            project = project.parent!!
            yield(project)
        }
    }.toList().reversed()


internal
fun sourcePathFor(scriptHandlers: List<ScriptHandler>): ClassPath {
    var sourcePath = ClassPath.EMPTY

    val resolvedDependencies = hashSetOf<ComponentIdentifier>()
    for (buildscript in scriptHandlers.asReversed()) {
        val unresolvedDependencies = classpathDependenciesOf(buildscript).filter { it !in resolvedDependencies }
        if (unresolvedDependencies.isNotEmpty()) {
            sourcePath += resolveSourcesUsing(buildscript.dependencies) {
                forComponents(unresolvedDependencies)
            }
            resolvedDependencies += unresolvedDependencies
        }
    }

    if (!containsBuiltinKotlinModules(resolvedDependencies)) {
        sourcePath += kotlinLibSourcesFor(scriptHandlers)
    }

    return sourcePath
}

private
fun resolveSources(
    scriptHandler: ScriptHandler,
    dependenciesToResolve: List<ComponentIdentifier>
) = resolveSourcesUsing(scriptHandler.dependencies) {
    forComponents(dependenciesToResolve)
}

private
fun containsBuiltinKotlinModules(resolvedDependencies: Collection<ComponentIdentifier>): Boolean {
    val resolvedModules = resolvedDependencies.filterIsInstance<ModuleComponentIdentifier>()
    return builtinKotlinModules.all { kotlinModule ->
        resolvedModules.any { resolved ->
            resolved.module == kotlinModule && resolved.version == embeddedKotlinVersion
        }
    }
}

private
fun classpathDependenciesOf(buildscript: ScriptHandler, skipDependencies: Set<ComponentIdentifier>): List<ComponentIdentifier> =
    classpathDependenciesOf(buildscript).filter { it !in skipDependencies }

private
fun classpathDependenciesOf(buildscript: ScriptHandler): List<ComponentIdentifier> =
    buildscript
        .configurations[CLASSPATH_CONFIGURATION]
        .incoming
        .artifactView { it.lenient(true) }
        .artifacts
        .map { it.id.componentIdentifier }


private
fun kotlinLibSourcesFor(scriptHandlers: List<ScriptHandler>): ClassPath =
    scriptHandlers
        .asSequence()
        .filter { it.repositories.isNotEmpty() }
        .map { resolveKotlinLibSourcesUsing(it.dependencies) }
        .find { !it.isEmpty } ?: ClassPath.EMPTY


private
fun resolveKotlinLibSourcesUsing(dependencyHandler: DependencyHandler): ClassPath =
    resolveSourcesUsing(dependencyHandler) {
        builtinKotlinModules.forEach { kotlinModule ->
            forModule("org.jetbrains.kotlin", kotlinModule, embeddedKotlinVersion)
        }
    }


private
fun resolveSourcesUsing(dependencyHandler: DependencyHandler, query: ArtifactResolutionQuery.() -> Unit): ClassPath =
    DefaultClassPath.of(
        dependencyHandler
            .createArtifactResolutionQuery()
            .apply(query)
            .withArtifacts(JvmLibrary::class.java, SourcesArtifact::class.java)
            .execute()
            .resolvedComponents
            .flatMap { it.getArtifacts(SourcesArtifact::class.java) }
            .filterIsInstance<ResolvedArtifactResult>()
            .map { it.file }
            .sorted()
    ) // TODO remove sorting once https://github.com/gradle/gradle/issues/5507 is fixed


private
val builtinKotlinModules = listOf("kotlin-stdlib", "kotlin-reflect")
