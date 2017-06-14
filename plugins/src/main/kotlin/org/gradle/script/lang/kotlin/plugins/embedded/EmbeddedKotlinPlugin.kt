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
package org.gradle.script.lang.kotlin.plugins.embedded

import org.gradle.api.Plugin
import org.gradle.api.Project

import org.gradle.api.artifacts.ClientModule
import org.gradle.api.artifacts.ModuleVersionSelector

import org.gradle.api.internal.classpath.ModuleRegistry

import org.gradle.cache.CacheRepository

import org.gradle.script.lang.kotlin.embeddedKotlinVersion

import java.io.File

import java.net.URI

import javax.inject.Inject


internal
data class EmbeddedModule(
    val group: String,
    val name: String,
    val version: String,
    val dependencies: List<EmbeddedModule> = emptyList(),
    val autoDependency: Boolean = false) {

    val notation: String = "$group:$name:$version"
    val jarRepoPath: String = "${group.replace(".", "/")}/$name/$version/$name-$version.jar"
}


internal
val embeddedModules: List<EmbeddedModule> by lazy {

    fun embeddedKotlin(name: String, dependencies: List<EmbeddedModule> = emptyList(), autoDependency: Boolean = false) =
        EmbeddedModule("org.jetbrains.kotlin", "kotlin-$name", embeddedKotlinVersion, dependencies, autoDependency)

    // TODO:pm could be generated at build time
    val annotations = EmbeddedModule("org.jetbrains", "annotations", "13.0")
    listOf(
        annotations,
        embeddedKotlin("stdlib", listOf(annotations), autoDependency = true),
        embeddedKotlin("reflect", autoDependency = true),
        embeddedKotlin("compiler-embeddable"))
}


private
val embeddedRepositoryCacheKeyVersion = 1


/**
 * The `embedded-kotlin` plugin.
 *
 * Applies the `org.jetbrains.kotlin.jvm` plugin,
 * adds implementation dependencies on `kotlin-stdlib` and `kotlin-reflect,
 * configures an embedded repository that contains all embedded Kotlin libraries,
 * and pins them to the embedded Kotlin version.
 */
open class EmbeddedKotlinPlugin @Inject constructor(
    val cacheRepository: CacheRepository,
    val moduleRegistry: ModuleRegistry) : Plugin<Project> {

    override fun apply(project: Project) {
        project.run {

            applyKotlinPlugin()

            addRepository()
            addDependencies()
            pinDependencies()
        }
    }

    private
    fun Project.applyKotlinPlugin() {

        plugins.apply("org.jetbrains.kotlin.jvm")
    }

    private
    fun Project.addRepository() {

        repositories.maven { repo ->
            repo.name = "Embedded Kotlin Repository"
            repo.url = initializeRepository()
        }
    }

    private
    fun Project.initializeRepository(): URI {

        val cacheKey = "embedded-kotlin-repo-$embeddedKotlinVersion-$embeddedRepositoryCacheKeyVersion"
        cacheRepository.cache(cacheKey).withInitializer { cache ->
            embeddedModules.forEach { module ->
                val fromDistro = moduleRegistry.getExternalModule(module.name).classpath.asFiles.first()
                fromDistro.copyTo(File(File(cache.baseDir, "repo"), module.jarRepoPath))
            }
        }.open().use { cache ->
            return uri(File(cache.baseDir, "repo"))
        }
    }

    private
    fun Project.addDependencies() {

        embeddedModules.filter { it.autoDependency }.forEach { embeddedModule ->
            dependencies.add("implementation", clientModuleFor(embeddedModule))
        }
    }

    private
    fun Project.clientModuleFor(embeddedModule: EmbeddedModule): ClientModule {

        val clientModule = dependencies.module(embeddedModule.notation) as ClientModule
        embeddedModule.dependencies.forEach { dependency ->
            clientModule.addDependency(clientModuleFor(dependency))
        }
        return clientModule
    }

    private
    fun Project.pinDependencies() {

        configurations.all { configuration ->
            configuration.resolutionStrategy.eachDependency { details ->
                findEmbeddedModule(details.requested)?.let { module ->
                    details.useTarget(module.notation)
                }
            }
        }
    }

    private
    fun findEmbeddedModule(requested: ModuleVersionSelector) =
        embeddedModules.find { it.group == requested.group && it.name == requested.name }
}
