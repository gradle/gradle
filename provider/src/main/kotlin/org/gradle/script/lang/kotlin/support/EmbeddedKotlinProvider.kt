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
package org.gradle.script.lang.kotlin.support

import org.gradle.api.artifacts.ClientModule
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.internal.classpath.ModuleRegistry

import org.gradle.cache.CacheRepository
import org.gradle.cache.PersistentCache

import org.gradle.script.lang.kotlin.embeddedKotlinVersion

import java.io.File

import java.net.URI


private
val embeddedRepositoryCacheKeyVersion = 1


private
data class EmbeddedKotlinModule(
    val group: String,
    val name: String,
    val version: String,
    val dependencies: List<EmbeddedKotlinModule> = emptyList()) {

    val notation = "$group:$name:$version"
    val jarRepoPath = "${group.replace(".", "/")}/$name/$version/$name-$version.jar"
}


private
val embeddedKotlinModules: List<EmbeddedKotlinModule> by lazy {

    fun embeddedKotlin(name: String, dependencies: List<EmbeddedKotlinModule> = emptyList()) =
        EmbeddedKotlinModule("org.jetbrains.kotlin", "kotlin-$name", embeddedKotlinVersion, dependencies)

    // TODO:pm could be generated at build time
    val annotations = EmbeddedKotlinModule("org.jetbrains", "annotations", "13.0")
    listOf(
        annotations,
        embeddedKotlin("stdlib", listOf(annotations)),
        embeddedKotlin("reflect"),
        embeddedKotlin("compiler-embeddable"))
}


class EmbeddedKotlinProvider constructor(
    private val cacheRepository: CacheRepository,
    private val moduleRegistry: ModuleRegistry) {

    fun addRepository(repositories: RepositoryHandler) {

        repositories.maven { repo ->
            repo.name = "Embedded Kotlin Repository"
            repo.url = embeddedKotlinRepositoryURI()
        }
    }


    fun addDependencies(
        dependencies: DependencyHandler,
        configuration: String,
        vararg kotlinModules: String) {

        kotlinModules.map { getEmbeddedKotlinModule(it) }.forEach { embeddedKotlinModule ->
            dependencies.add(configuration, clientModuleFor(dependencies, embeddedKotlinModule))
        }
    }


    fun pinDependencies(configuration: Configuration, vararg kotlinModules: String) {

        val dependenciesModules = kotlinModules.map { getEmbeddedKotlinModule(it) }
        configuration.resolutionStrategy.eachDependency { details ->
            findEmbeddedModule(details.requested, dependenciesModules)?.let { module ->
                details.useTarget(module.notation)
            }
        }
    }


    private
    fun embeddedKotlinRepositoryURI(): URI =
        embeddedKotlinRepositoryDir().toURI()

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
        embeddedKotlinModules.forEach { module ->
            fileFor(module).copyTo(File(repoDirFrom(cache), module.jarRepoPath))
        }
    }

    private
    fun fileFor(module: EmbeddedKotlinModule) =
        moduleRegistry.getExternalModule(module.name).classpath.asFiles.first()

    private
    fun repoDirCacheKey() =
        "embedded-kotlin-repo-$embeddedKotlinVersion-$embeddedRepositoryCacheKeyVersion"

    private
    fun repoDirFrom(cache: PersistentCache) =
        File(cache.baseDir, "repo")

    private
    fun clientModuleFor(dependencies: DependencyHandler, embeddedModule: EmbeddedKotlinModule): ClientModule =
        (dependencies.module(embeddedModule.notation) as ClientModule).apply {
            embeddedModule.dependencies.forEach { dependency ->
                addDependency(clientModuleFor(dependencies, dependency))
            }
        }

    private
    fun getEmbeddedKotlinModule(kotlinModule: String) =
        embeddedKotlinModules.first { it.group == "org.jetbrains.kotlin" && it.name == "kotlin-$kotlinModule" }

    private
    fun findEmbeddedModule(requested: ModuleVersionSelector, embeddedModules: List<EmbeddedKotlinModule>) =
        embeddedModules.find { it.group == requested.group && it.name == requested.name }
}
