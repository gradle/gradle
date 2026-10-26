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
// Thus the plugins built from this checkout replace them. The repository that the property names has them at the published version.
val kotlinDslGroups = "org\\.gradle\\.kotlin(\\..+)?"
val pluginsRepository = providers.gradleProperty("gradlebuild.kotlinDslPluginsRepository").map { File(it) }.orNull?.takeIf { it.isDirectory }

data class ExtraRepository(val name: String, val url: URI, val groups: String, val exclusive: Boolean = false)

val extraRepositories = listOfNotNull(
    // Exclusive, because the Plugin Portal has the same version
    pluginsRepository?.let { ExtraRepository("Locally built Kotlin DSL plugins", it.toURI(), kotlinDslGroups, exclusive = true) },
    // Declared after the repositories of the build. Thus only the Kotlin that they do not have comes from this repository.
    if (embeddedKotlinVersion.matches(Regex(".+-\\d+"))) ExtraRepository("Kotlin dev repository", URI("https://packages.jetbrains.team/maven/p/kt/dev"), "org\\.jetbrains\\.kotlin(\\..+)?") else null,
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
