/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.kotlin.dsl.support.expectedKotlinDslPluginsVersion
import java.net.URI

// A build that runs on the Gradle under development needs two things from extra repositories.
// First, it needs the Kotlin that this Gradle embeds, on every script classpath. For a dev build, only the Kotlin dev repository has this Kotlin.
// Second, it needs Kotlin DSL plugins built against this Kotlin. The published plugins are built against the embedded Kotlin of an older Gradle.
// Thus the plugins built from this checkout replace them. They come from the repository that the property names.
val kotlinDslGroups = "org\\.gradle\\.kotlin(\\..+)?"
val pluginsRepository = providers.gradleProperty("gradlebuild.kotlinDslPluginsRepository").map { File(it) }.orNull?.takeIf { it.isDirectory }

data class ExtraRepository(val name: String, val url: URI, val groups: String, val exclusive: Boolean = false)

val extraRepositories = listOfNotNull(
    // Exclusive, because the Plugin Portal can already have the same version, published from another branch
    pluginsRepository?.let { ExtraRepository("Locally built Kotlin DSL plugins", it.toURI(), kotlinDslGroups, exclusive = true) },
    // Declared after the existing repositories. Thus a stable Kotlin never causes a lookup in this repository.
    ExtraRepository("Kotlin dev repository", URI("https://packages.jetbrains.team/maven/p/kt/dev"), "org\\.jetbrains\\.kotlin(\\..+)?"),
)

fun normalizeUrl(url: Any) = url.toString().replace("https://", "http://").trimEnd('/') + "/"

fun RepositoryHandler.addExtraRepositories() {
    extraRepositories.forEach { repository ->
        if (any { it is MavenArtifactRepository && normalizeUrl(it.url) == normalizeUrl(repository.url) }) {
            return@forEach
        }
        val declaration: MavenArtifactRepository.() -> Unit = {
            name = repository.name
            url = repository.url
        }
        if (repository.exclusive) {
            exclusiveContent {
                forRepository { maven(declaration) }
                filter { includeGroupByRegex(repository.groups) }
            }
        } else {
            maven {
                declaration()
                content { includeGroupByRegex(repository.groups) }
            }
        }
    }
}

// Every settings file declares its plugin repositories. If a settings file declares none, an added repository replaces the default repository.
// A repository that an init script adds is not a declaration of the build either.
pluginManagement.repositories.addExtraRepositories()
gradle.settingsEvaluated {
    dependencyResolutionManagement.repositories.addExtraRepositories()
}

// The configuration cache isolates the project hook. Thus the hook must not capture the script.
class LocalKotlinDslPlugins(private val groups: String, private val expectedVersion: String, private val version: String) {

    private fun replaces(group: String, version: String?) = group.matches(Regex(groups)) && version == expectedVersion

    fun usePluginVersion(details: PluginResolveDetails) {
        if (replaces(details.requested.id.id, details.requested.version)) {
            details.useVersion(version)
        }
    }

    fun substituteDependencies(configurations: ConfigurationContainer) {
        configurations.configureEach {
            resolutionStrategy.dependencySubstitution.all {
                val selector = requested as? ModuleComponentSelector ?: return@all
                if (replaces(selector.group, selector.version)) {
                    useTarget("${selector.group}:${selector.module}:$version")
                }
            }
        }
    }
}

if (pluginsRepository != null) {
    val metadata = pluginsRepository.resolve("org/gradle/kotlin/gradle-kotlin-dsl-plugins/maven-metadata.xml").readText()
    val localVersion = Regex("<release>(.+?)</release>").find(metadata)?.groupValues?.get(1)
        ?: error("No Kotlin DSL plugins are published to $pluginsRepository")
    with(LocalKotlinDslPlugins(kotlinDslGroups, expectedKotlinDslPluginsVersion, localVersion)) {
        pluginManagement.resolutionStrategy.eachPlugin {
            usePluginVersion(this)
        }
        gradle.lifecycle.beforeProject {
            substituteDependencies(configurations)
            substituteDependencies(buildscript.configurations)
        }
    }
}
