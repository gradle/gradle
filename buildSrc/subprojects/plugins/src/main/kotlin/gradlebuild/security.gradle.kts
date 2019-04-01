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

val allowedSchemes = setOf("https")
val insecureRepos = mutableSetOf<String>()

allprojects {
    configurations.all {
        incoming.beforeResolve {
            repositories.forEach {
                if (it is MavenArtifactRepository) {
                    checkURLs(it, setOf(it.url))
                    checkURLs(it, it.artifactUrls)
                } else if (it is IvyArtifactRepository) {
                    checkURLs(it, setOf(it.url))
                }
            }
        }
    }
}

gradle.buildFinished {
    if (!insecureRepos.isEmpty()) {
        throw GradleException("This build used insecure repositories:\n" + insecureRepos.joinToString("\n") + "Make sure to use HTTPS")
    }
}

fun checkURLs(repository: ArtifactRepository, uris: Collection<URI>) = uris.forEach {
    if (it.scheme.toLowerCase() !in allowedSchemes) {
        insecureRepos.add("   Insecure repository '${repository.name}': $it")
    }
}
