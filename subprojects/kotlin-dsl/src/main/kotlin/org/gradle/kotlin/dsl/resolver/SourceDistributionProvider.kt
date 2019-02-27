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
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.transform.TransformSpec
import org.gradle.api.attributes.Attribute
import org.gradle.kotlin.dsl.*
import java.io.File
import java.lang.Integer.max


interface SourceDistributionProvider {
    fun sourceDirs(): Collection<File>
}


class SourceDistributionResolver(val project: Project) : SourceDistributionProvider {

    companion object {
        val artifactType = Attribute.of("artifactType", String::class.java)
        val zipType = "zip"
        val unzippedDistributionType = "unzipped-distribution"
        val sourceDirectory = "src-directory"
    }

    override fun sourceDirs(): Collection<File> =
        try {
            collectSourceDirs()
        } catch (ex: Exception) {
            project.logger.warn("Unexpected exception while resolving Gradle distribution sources: ${ex.message}", ex)
            emptyList()
        }

    private
    fun collectSourceDirs() =
        withSourceRepository {
            registerTransforms()
            transientConfigurationForSourcesDownload().files
        }

    private
    fun <T> withSourceRepository(produce: () -> T): T =
        createSourceRepository().let {
            try {
                produce()
            } finally {
                repositories.remove(it)
            }
        }

    private
    fun registerTransforms() {
        registerTransform<UnzipDistribution> {
            from.attribute(artifactType, zipType)
            to.attribute(artifactType, unzippedDistributionType)
        }
        registerTransform<FindGradleSources> {
            from.attribute(artifactType, unzippedDistributionType)
            to.attribute(artifactType, sourceDirectory)
        }
    }

    private
    fun transientConfigurationForSourcesDownload() =
        detachedConfigurationFor(gradleSourceDependency()).apply {
            attributes.attribute(artifactType, sourceDirectory)
        }

    private
    fun detachedConfigurationFor(dependency: Dependency) =
        configurations.detachedConfiguration(dependency)

    private
    fun gradleSourceDependency() = dependencies.create(
        group = "gradle",
        name = "gradle",
        version = dependencyVersion(gradleVersion),
        configuration = null,
        classifier = "src",
        ext = "zip")

    private
    fun createSourceRepository() = ivy {
        val repoName = repositoryNameFor(gradleVersion)
        name = "Gradle $repoName"
        setUrl("https://services.gradle.org/$repoName")
        metadataSources { sources ->
            sources.artifact()
        }
        patternLayout { layout ->
            if (isSnapshot(gradleVersion)) {
                layout.ivy("/dummy") // avoids a lookup that interferes with version listing
            }
            layout.artifact("[module]-[revision](-[classifier])(.[ext])")
        }
    }.also {
        // push the repository first in the list, for performance
        makeItFirstInTheList(it)
    }

    private
    fun repositoryNameFor(gradleVersion: String) =
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
        gradleVersion
            .split('.')
            .take(2)
            .map { it.takeWhile { it != '-' }.toInt() }
            .mapIndexed { i, v -> if (i == 0) v else max(v - 1, 0) }
            .joinToString(".") { it.toString() }

    private
    fun makeItFirstInTheList(repository: ArtifactRepository) {
        repositories.apply {
            remove(repository)
            addFirst(repository)
        }
    }

    private
    inline fun <reified T : TransformAction<TransformParameters.None>> registerTransform(crossinline configure: TransformSpec<TransformParameters.None>.() -> Unit) =
        dependencies.registerTransform(T::class.java) { configure(it) }

    private
    fun ivy(configure: IvyArtifactRepository.() -> Unit) =
        repositories.ivy { configure(it) }

    private
    val repositories
        get() = project.repositories

    private
    val configurations
        get() = project.configurations

    private
    val dependencies
        get() = project.dependencies

    private
    val gradleVersion
        get() = project.gradle.gradleVersion
}
