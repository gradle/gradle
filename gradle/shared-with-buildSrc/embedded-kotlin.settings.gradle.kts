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

// A build run by a Gradle embedding a Kotlin dev build needs that Kotlin on every script classpath, which only
// the Kotlin dev repository has.
data class ExtraRepository(val name: String, val url: URI, val groups: String)

val extraRepositories = listOf(
    // Declared after the existing repositories, so a stable Kotlin never costs a lookup here
    ExtraRepository("Kotlin dev repository", URI("https://packages.jetbrains.team/maven/p/kt/dev"), "org\\.jetbrains\\.kotlin(\\..+)?"),
)

fun normalizeUrl(url: Any) = url.toString().replace("https://", "http://").trimEnd('/') + "/"

fun RepositoryHandler.addExtraRepositories() {
    extraRepositories.forEach { repository ->
        if (none { it is MavenArtifactRepository && normalizeUrl(it.url) == normalizeUrl(repository.url) }) {
            maven {
                name = repository.name
                url = repository.url
                content {
                    includeGroupByRegex(repository.groups)
                }
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
