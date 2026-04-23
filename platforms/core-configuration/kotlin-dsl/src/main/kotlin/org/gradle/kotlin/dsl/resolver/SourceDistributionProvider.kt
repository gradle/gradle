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

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.Try
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.resolver.internal.GradleDistRepoDescriptor
import org.gradle.kotlin.dsl.resolver.internal.GradleDistRepoDescriptorLocator
import org.gradle.kotlin.dsl.resolver.internal.GradleDistVersion
import org.gradle.util.GradleVersion
import java.io.File
import kotlin.jvm.optionals.getOrNull

interface SourceDistributionProvider {
    fun sourceDirs(): Collection<File>
}


class SourceDistributionResolver(private val project: Project) : SourceDistributionProvider {

    companion object {
        val artifactType: Attribute<String> = Attribute.of("artifactType", String::class.java)
        const val ZIP_TYPE = "zip"
        const val SOURCE_DIRECTORY = "src-directory"
    }

    private val repoLocator = GradleDistRepoDescriptorLocator(project)

    override fun sourceDirs(): Collection<File> =
        try {
            sourceDirs
        } catch (ex: Exception) {
            project.logger.warn("Could not resolve Gradle distribution sources. See debug logs for details.")
            project.logger.debug("Gradle distribution sources resolution failure", ex)
            emptyList()
        }

    private
    val sourceDirs by lazy {
        resolveSourceDirsFromRepositories(candidateRepositories())
    }

    private
    fun candidateRepositories() =
        listOfNotNull(
            // The repository inferred from the project configuration (e.g. wrapper properties)
            repoLocator.primaryRepository,
            // The fallback solution if the inferred repository is not available
            repoLocator.fallbackRepository
        )

    private
    fun resolveSourceDirsFromRepositories(repositories: List<GradleDistRepoDescriptor>): Set<File> {
        val results = buildMap {
            for (repo in repositories) {
                val result = Try.ofFailable { resolveSourceDirsFrom(repo) }
                put(repo, result)
                if (result.isSuccessful) {
                    val files = result.get()
                    if (files.isNotEmpty()) {
                        return files
                    }
                }
            }
        }
        if (results.all { it.value.isSuccessful }) {
            return emptySet()
        }
        val tried = results.keys.joinToString(separator = "\n") { "  - ${it.repoBaseUrl}" }
        throw GradleException("Unable to resolve Gradle distribution sources, tried:\n$tried").apply {
            results.mapNotNull { it.value.failure.getOrNull() }.forEach {
                addSuppressed(it)
            }
        }
    }

    private
    fun resolveSourceDirsFrom(repo: GradleDistRepoDescriptor): Set<File> {
        val resolver = projectInternal.newDetachedResolver()
        resolver.repositories.createSourceRepository(repo)
        resolver.dependencies.registerTransform()
        val dependency = resolver.dependencies.createDependency()
        val configuration = resolver.configurations.createConfiguration(dependency)
        return configuration.files
    }

    private
    fun RepositoryHandler.createSourceRepository(repo: GradleDistRepoDescriptor) = ivy {
        name = "Gradle ${repo.name}"
        url = repo.repoBaseUrl
        metadataSources {
            artifact()
        }
        patternLayout {
            if (repoLocator.gradleVersion.isSnapshot) {
                ivy("/dummy") // avoids a lookup that interferes with version listing
            }
            artifact(repo.artifactPattern)
        }
        repo.credentialsApplier(this)
    }

    private
    fun DependencyHandler.registerTransform() =
        registerTransform(FindGradleSources::class.java) {
            from.attribute(artifactType, ZIP_TYPE)
            to.attribute(artifactType, SOURCE_DIRECTORY)
        }

    private
    fun DependencyHandler.createDependency() =
        create("gradle:gradle:${dependencyVersion(repoLocator.gradleVersion)}") {
            artifact {
                classifier = "src"
                type = "zip"
            }
        }

    private
    fun ConfigurationContainer.createConfiguration(dependency: Dependency) =
        detachedConfiguration(dependency).apply {
            attributes.attribute(artifactType, SOURCE_DIRECTORY)
        }

    private
    fun dependencyVersion(gradleVersion: GradleDistVersion): String =
        if (gradleVersion.isSnapshot) toVersionRange(gradleVersion.versionString)
        else gradleVersion.versionString

    private
    fun toVersionRange(gradleVersion: String) =
        "(${minimumGradleVersion()}, $gradleVersion]"

    private
    fun minimumGradleVersion(): String {
        val baseVersionString = GradleVersion.version(repoLocator.gradleVersion.versionString).baseVersion.version
        val (major, minor) = baseVersionString.split('.')
        return when (minor) {
            // TODO:kotlin-dsl consider commenting out this clause once the 1st 6.0 snapshot is out
            "0" -> {
                // When testing against a `major.0` snapshot we need to take into account
                // that source distributions matching the major version might not have
                // been published yet. In that case we adjust the constraint to include
                // source distributions beginning from the previous major version.
                "${previous(major)}.0"
            }

            else -> {
                // Otherwise include source distributions beginning from the previous minor version only.
                "$major.${previous(minor)}"
            }
        }
    }

    private
    fun previous(versionDigit: String) =
        Integer.parseInt(versionDigit) - 1

    private
    val projectInternal
        get() = project as ProjectInternal
}
