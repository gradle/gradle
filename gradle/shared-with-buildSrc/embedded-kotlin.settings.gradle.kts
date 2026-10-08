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

// What a build needs when the Gradle running it is the one under development: the Kotlin it embeds on every
// script classpath, which only the Kotlin dev repository has for a dev build, and Kotlin DSL plugins built against
// that Kotlin. The published plugins are built against the embedded Kotlin of an older Gradle, so the ones built
// from this checkout take their place, from the repository the property names.
val kotlinDslGroups = "org\\.gradle\\.kotlin(\\..+)?"
val pluginsRepository = providers.gradleProperty("gradlebuild.kotlinDslPluginsRepository").map { File(it) }.orNull?.takeIf { it.isDirectory }

data class ExtraRepository(val name: String, val url: URI, val groups: String, val exclusive: Boolean = false)

val extraRepositories = listOfNotNull(
    // Exclusive, as the Plugin Portal may already have the same version, published from another branch
    pluginsRepository?.let { ExtraRepository("Locally built Kotlin DSL plugins", it.toURI(), kotlinDslGroups, exclusive = true) },
    // Declared after the existing repositories, so a stable Kotlin never costs a lookup here
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

// Every settings file declares its plugin repositories: adding to none would replace the default one, and a
// repository added by an init script is not the build's own declaration either
pluginManagement.repositories.addExtraRepositories()
gradle.settingsEvaluated {
    dependencyResolutionManagement.repositories.addExtraRepositories()
}

// The project hook is isolated for the configuration cache, so it must not capture the script
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
