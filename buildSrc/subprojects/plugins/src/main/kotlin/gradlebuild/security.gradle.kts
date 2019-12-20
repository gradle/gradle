/*
 * Copyright 2019 the original author or authors.
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
package gradlebuild

import java.net.URI
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

val lock = ReentrantLock()
val allowedSchemes = setOf("https", "file")
val alreadyChecked = mutableSetOf<ArtifactRepository>()
val insecureRepos = mutableSetOf<InsecureRepository>()
val repoToSource = mutableMapOf<ArtifactRepository, String>()

allprojects {
    repositories.whenObjectAdded {
        Exception().stackTrace.forEachIndexed { idx, it ->
            if (idx > 1 && !repoToSource.containsKey(this) && it.isBuildScript()) {
                repoToSource.put(this, "${it.fileName}:${it.lineNumber} in ${it.className}")
            }
        }
    }
    val project = this
    configurations.all {
        incoming.beforeResolve {
            repositories.checkRepositories(project)
        }
    }
    afterEvaluate {
        extensions.findByType(PublishingExtension::class.java)?.run {
            repositories.checkRepositories(project)
        }
    }
}

gradle.buildFinished {
    if (!insecureRepos.isEmpty()) {
        val byProject = insecureRepos
            .groupBy(InsecureRepository::projectPath)
            .mapKeys {
                "In project '${it.key}' :"
            }.mapValues {
                it.value.map { repo ->
                    val location = if (repo.location != null) {
                        "declared at ${repo.location}"
                    } else {
                        "(unknown location)"
                    }
                    "      - Repository ${repo.repositoryName} : ${repo.url} $location"
                }.joinToString("\n")
            }
        val detail = byProject.map {
            val (key, value) = it
            "   * $key\n$value"
        }.joinToString("\n")

        throw GradleException("This build used insecure repositories:\n${detail}\n\nMake sure to use HTTPS")
    }
}

fun RepositoryHandler.checkRepositories(project: Project) = forEach {
    lock.withLock {
        if (alreadyChecked.add(it)) {
            if (it is MavenArtifactRepository) {
                checkURLs(project, it, setOf(it.url))
                checkURLs(project, it, it.artifactUrls)
            } else if (it is IvyArtifactRepository) {
                checkURLs(project, it, setOf(it.url))
            }
        }
    }
}

fun checkURLs(project: Project, repository: ArtifactRepository, uris: Collection<URI>) = uris.forEach {
    if (it.scheme.toLowerCase() !in allowedSchemes) {
        insecureRepos.add(InsecureRepository(project.path, repository.name, it, repoToSource.get(repository)))
    }
}

data class InsecureRepository(val projectPath: String, val repositoryName: String, val url: URI, val location: String?)

fun StackTraceElement.isBuildScript() = fileName != null && (fileName.endsWith(".gradle") || fileName.endsWith(".gradle.kts"))
