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

import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import java.net.URI

// A build that runs on the Gradle under development needs two things from extra repositories.
// First, it needs the Kotlin that this Gradle embeds, on every script classpath. For a dev build, only the Kotlin dev repository has this Kotlin.
// Second, it needs Kotlin DSL plugins built against this Kotlin. The published plugins are built against the embedded Kotlin of an older Gradle.
// Thus the plugins built from this checkout replace them: the repository that the property names has them, and the build passes
// their version in the system property that makes Gradle expect it (see KOTLIN_DSL_PLUGINS_VERSION_OVERRIDE_PROPERTY in the kotlin-dsl module).
val kotlinDslGroups = "org\\.gradle\\.kotlin(\\..+)?"
val pluginsRepository = providers.gradleProperty("gradlebuild.kotlinDslPluginsRepository").map { File(it) }.orNull?.takeIf { it.isDirectory }
// Only this version: the repository can have other versions from an earlier build
val pluginsVersion = providers.systemProperty("org.gradle.internal.kotlin.dsl.plugins.version.override").orNull

data class ExtraRepository(val name: String, val url: URI, val groups: String, val version: String? = null)

// Declared after the repositories of the build. Thus only the versions that they do not have come from these repositories.
val extraRepositories = listOfNotNull(
    if (pluginsRepository != null && pluginsVersion != null) ExtraRepository("Locally built Kotlin DSL plugins", pluginsRepository.toURI(), kotlinDslGroups, pluginsVersion) else null,
    if (embeddedKotlinVersion.matches(Regex(".+-\\d+"))) ExtraRepository("Kotlin dev repository", URI("https://packages.jetbrains.team/maven/p/kt/dev"), "org\\.jetbrains\\.kotlin(\\..+)?") else null,
)

fun normalizeUrl(url: Any) = url.toString().replace("https://", "http://").trimEnd('/') + "/"

fun RepositoryHandler.addExtraRepositories() {
    extraRepositories.forEach { repository ->
        if (any { it is MavenArtifactRepository && normalizeUrl(it.url) == normalizeUrl(repository.url) }) {
            return@forEach
        }
        maven {
            name = repository.name
            url = repository.url
            content {
                if (repository.version != null) {
                    includeVersionByRegex(repository.groups, ".*", Regex.escape(repository.version))
                } else {
                    includeGroupByRegex(repository.groups)
                }
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
