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
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.IvyPatternRepositoryLayout
import org.gradle.api.attributes.Attribute
import org.gradle.kotlin.dsl.create
import java.io.File
import java.lang.Integer.max

interface SourceDistributionProvider {
    fun sourceDirs(): Collection<File>
}

class StandardSourceDistributionResolver(val project: Project) : SourceDistributionProvider {
    companion object {
        val artifactType = Attribute.of("artifactType", String::class.java)
        val zipType = "zip"
        val sourceDirectory = "src-directory"
    }

    override
    fun sourceDirs(): Collection<File> = createSourceRepository().run {
        try {
            registerTransforms()
            return transientConfigurationForSourcesDownload().files
        } finally {
            project.repositories.remove(this)
        }
    }


    private
    fun transientConfigurationForSourcesDownload(): Configuration {
        val sourceDependency = project.dependencies.create(
            "gradle",
            "gradle",
            dependencyVersion(project.gradle.gradleVersion),
            null,
            "src",
            "zip")
        val configuration = project.configurations.detachedConfiguration(sourceDependency)
        configuration.attributes.attribute(artifactType, sourceDirectory)
        return configuration
    }

    private
    fun createSourceRepository(): IvyArtifactRepository = project.repositories.ivy {
            val repoName = repositoryName(project.gradle.gradleVersion)
            it.setName("Gradle ${repoName}")
            it.setUrl("https://services.gradle.org/${repoName}")
            it.layout("pattern") {
                val layout = it as IvyPatternRepositoryLayout
                if (isSnapshot(project.gradle.gradleVersion)) {
                    layout.ivy("/dummy") // avoids a lookup that interferes with version listing
                }
                layout.artifact("[module]-[revision](-[classifier])(.[ext])")
            }
        }.apply {
            // push the repository first in the list, for performance
            project.repositories.remove(this)
            project.repositories.addFirst(this)
        }


    private
    fun registerTransforms() = project.dependencies.registerTransform {
            it.from.attribute(artifactType, zipType)
            it.to.attribute(artifactType, sourceDirectory)
            it.artifactTransform(ExtractGradleSourcesTransform::class.java)
    }

    private
    fun repositoryName(gradleVersion: String) =
        if (isSnapshot(gradleVersion)) "distributions-snapshots" else "distributions"

    private
    fun dependencyVersion(gradleVersion: String) =
        if (isSnapshot(gradleVersion)) toVersionRange(gradleVersion) else gradleVersion

    private
    fun isSnapshot(gradleVersion: String) = gradleVersion.contains('+')

    private
    fun toVersionRange(gradleVersion: String) =
        "(${previousMinor(gradleVersion)}, $gradleVersion]"

    private
    fun previousMinor(gradleVersion: String): String =
        gradleVersion.split('.')
            .take(2)
            .map { it.takeWhile { it != '-' }. toInt() }
            .mapIndexed { i, v -> if (i==0) v else max(v-1,0) }
            .map(Int::toString)
            .joinToString(".")
}
