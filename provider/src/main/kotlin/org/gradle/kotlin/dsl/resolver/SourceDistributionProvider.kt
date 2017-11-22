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

package org.gradle.kotlin.dsl.resolver

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.IvyPatternRepositoryLayout
import org.gradle.api.attributes.Attribute
import org.gradle.kotlin.dsl.create
import java.io.File

interface SourceDistributionProvider {
    fun sourceDirs(): Collection<File>
}

class StandardSourceDistributionResolver(val project: Project) : SourceDistributionProvider {
    companion object {
        val artifactType = Attribute.of("artifactType", String::class.java)
        val zipType = "zip"
        val sourceDirectory = "src-directory"
    }

    override fun sourceDirs(): Collection<File> {
        val repositories = project.repositories
        val repos = createSourceRepositories(repositories)
        try {
            registerTransforms()
            return transientConfigurationForSourcesDownload().files
        } finally {
            repos.forEach {
                repositories.remove(it)
            }
        }
    }

    private
    fun transientConfigurationForSourcesDownload(): Configuration {
        val sourceDependency = project.dependencies.create("gradle", "gradle", project.gradle.gradleVersion, null, "src", "zip")
        val configuration = project.configurations.detachedConfiguration(sourceDependency)
        configuration.attributes.attribute(artifactType, sourceDirectory)
        return configuration
    }

    private
    fun createSourceRepositories(repositories: RepositoryHandler): List<IvyArtifactRepository> =
        listOf("distributions", "distributions-snapshots").map { repoName ->
            val repo = repositories.ivy {
                it.setName("Gradle ${repoName}")
                it.setUrl("https://services.gradle.org")
                it.layout("pattern") {
                    val layout = it as IvyPatternRepositoryLayout
                    layout.artifact("/${repoName}/[module]-[revision]-[classifier](.[ext])")
                }
            }
            // push the repository first in the list, for performance
            repositories.remove(repo)
            repositories.addFirst(repo)
            repo
        }

    private
    fun registerTransforms() {
        project.dependencies.registerTransform {
            it.from.attribute(artifactType, zipType)
            it.to.attribute(artifactType, sourceDirectory)
            it.artifactTransform(ExtractGradleSourcesTransform::class.java)
        }
    }

}
