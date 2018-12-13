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
package org.gradle.kotlin.dsl.support

import org.gradle.api.artifacts.ClientModule
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.internal.classpath.ModuleRegistry

import org.gradle.cache.CacheRepository
import org.gradle.cache.PersistentCache

import org.gradle.kotlin.dsl.embeddedKotlinVersion

import java.io.File

import java.net.URI
import java.util.*


private
const val embeddedRepositoryCacheKeyVersion = 2


class EmbeddedKotlinProvider constructor(
    private val cacheRepository: CacheRepository,
    private val moduleRegistry: ModuleRegistry
) {

    fun addRepositoryTo(repositories: RepositoryHandler) {

        repositories.maven { repo ->
            repo.name = "Embedded Kotlin Repository"
            repo.url = embeddedKotlinRepositoryURI
            repo.metadataSources { sources ->
                sources.artifact()
            }
        }
    }

    fun addDependenciesTo(
        dependencies: DependencyHandler,
        configuration: String,
        vararg kotlinModules: String
    ) {

        embeddedKotlinModulesFor(kotlinModules).forEach { embeddedKotlinModule ->
            dependencies.add(configuration, clientModuleFor(dependencies, embeddedKotlinModule))
        }
    }

    internal
    fun pinDependenciesOn(configuration: Configuration, pinnedDependencies: Set<EmbeddedModule>) {
        configuration.resolutionStrategy.eachDependency { details ->
            pinnedDependencies.findWithSameGroupAndNameAs(details.requested)?.let { pinned ->
                details.useTarget(pinned.notation)
            }
        }
    }

    private
    val embeddedKotlinRepositoryURI: URI by lazy {
        embeddedKotlinRepositoryDir().toURI()
    }

    private
    fun embeddedKotlinRepositoryDir(): File =
        cacheFor(repoDirCacheKey()).withInitializer { cache ->
            copyEmbeddedKotlinModulesTo(cache)
        }.open().use { cache ->
            repoDirFrom(cache)
        }

    private
    fun cacheFor(cacheKey: String) =
        cacheRepository.cache(cacheKey)

    private
    fun copyEmbeddedKotlinModulesTo(cache: PersistentCache) {
        embeddedModules.forEach { module ->
            fileFor(module).copyTo(File(repoDirFrom(cache), module.jarRepoPath))
        }
    }

    private
    fun fileFor(module: EmbeddedModule) =
        moduleRegistry.getExternalModule(module.name).classpath.asFiles.first()

    private
    fun repoDirCacheKey() =
        "embedded-kotlin-repo-$embeddedKotlinVersion-$embeddedRepositoryCacheKeyVersion"

    private
    fun repoDirFrom(cache: PersistentCache) =
        File(cache.baseDir, "repo")

    private
    fun clientModuleFor(dependencies: DependencyHandler, embeddedModule: EmbeddedModule): ClientModule =
        (dependencies.module(embeddedModule.notation) as ClientModule).apply {
            embeddedModule.dependencies.forEach { dependency ->
                addDependency(clientModuleFor(dependencies, dependency))
            }
        }

    private
    fun Iterable<EmbeddedModule>.findWithSameGroupAndNameAs(requested: ModuleVersionSelector) =
        find { it.name == requested.name && it.group == requested.group }
}


internal
data class EmbeddedModule(
    val group: String,
    val name: String,
    val version: String,
    val dependencies: List<EmbeddedModule> = emptyList()
) {

    val notation = "$group:$name:$version"
    val jarRepoPath = "${group.replace(".", "/")}/$name/$version/$name-$version.jar"
}


private
val embeddedModules: List<EmbeddedModule> by lazy {

    fun embeddedKotlin(name: String, dependencies: List<EmbeddedModule> = emptyList()) =
        EmbeddedModule("org.jetbrains.kotlin", "kotlin-$name", embeddedKotlinVersion, dependencies)

    // TODO:pm could be generated at build time
    val annotations = EmbeddedModule("org.jetbrains", "annotations", "13.0")
    val stdlib = embeddedKotlin("stdlib", listOf(annotations))
    val stdlibJdk7 = embeddedKotlin("stdlib-jdk7", listOf(stdlib))
    val stdlibJdk8 = embeddedKotlin("stdlib-jdk8", listOf(stdlibJdk7))
    val reflect = embeddedKotlin("reflect", listOf(stdlib))
    val compilerEmbeddable = embeddedKotlin("compiler-embeddable")
    val scriptRuntime = embeddedKotlin("script-runtime")
    val samWithReceiverCompilerPlugin = embeddedKotlin("sam-with-receiver-compiler-plugin")
    listOf(
        annotations,
        stdlib, stdlibJdk7, stdlibJdk8,
        reflect,
        compilerEmbeddable,
        scriptRuntime,
        samWithReceiverCompilerPlugin)
}


internal
fun transitiveClosureOf(vararg kotlinModules: String) =
    transitiveClosureOf(embeddedKotlinModulesFor(kotlinModules))


private
fun transitiveClosureOf(modules: Collection<EmbeddedModule>): Set<EmbeddedModule> =
    identitySetOf<EmbeddedModule>().apply {
        val q = ArrayDeque(modules)
        while (q.isNotEmpty()) {
            val module = q.removeFirst()
            if (add(module)) {
                q.addAll(module.dependencies)
            }
        }
    }


private
fun embeddedKotlinModulesFor(kotlinModules: Array<out String>) =
    kotlinModules.map { embeddedKotlinModuleFor(it) }


private
fun embeddedKotlinModuleFor(kotlinModule: String) =
    embeddedModules.first { it.group == "org.jetbrains.kotlin" && it.name == "kotlin-$kotlinModule" }


private
fun <T> identitySetOf(): MutableSet<T> =
    Collections.newSetFromMap(IdentityHashMap<T, Boolean>())
