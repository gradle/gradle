/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.kotlin.dsl.resolver.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.AuthenticationSupported
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.internal.GradleInternal
import org.gradle.initialization.layout.BuildLayoutFactory
import org.gradle.util.internal.WrapperCredentials
import org.gradle.util.internal.WrapperDistributionUrlConverter
import org.gradle.wrapper.WrapperExecutor
import java.net.URI
import java.util.Properties

private const val DEFAULT_GRADLE_DIST_REPO_HOST_NAME = "services.gradle.org"
private const val DEFAULT_GRADLE_DIST_ARTIFACT_PATTERN = "[module]-[revision](-[classifier])(.[ext])"
private val STANDARD_GRADLE_DIST_FILENAME_REGEX = Regex("gradle-[0-9]+(?:\\.[0-9]+){1,2}(?:-[a-zA-Z0-9+]+)*-(?:bin|all).zip")

class GradleDistRepoDescriptorLocator(
    private val project: Project,
    val gradleVersion: GradleDistVersion = GradleDistVersion(project.gradle.gradleVersion)
) {
    private
    val repositoryName = if (gradleVersion.isSnapshot) "distributions-snapshots" else "distributions"

    val gradleDistRepository: GradleDistRepoDescriptor
        get() = try {
            findCustomGradleDistRepository() ?: defaultGradleDistRepository
        } catch (ex: Exception) {
            project.logger.warn("Unexpected exception while trying to find the URL for Gradle sources: ${ex.message}", ex)
            defaultGradleDistRepository
        }

    private
    val defaultGradleDistRepository: GradleDistRepoDescriptor
        get() = gradleDistRepoDescriptor(
            repositoryName,
            URI.create("https://${DEFAULT_GRADLE_DIST_REPO_HOST_NAME}/$repositoryName"),
            "[module]-[revision](-[classifier])(.[ext])",
            null
        )

    private
    fun wrapperCredentials(baseUrl: URI): WrapperCredentials? =
        WrapperCredentials.findCredentials(baseUrl) { System.getProperty(it) }

    private
    fun findStandardWrapperUri(): URI? {
        val wrapperProperties = WrapperExecutor.wrapperPropertiesForProjectDirectory(rootBuildDir())
        if (wrapperProperties.exists()) {

            val currentWrapperUri = Properties()
                .apply { wrapperProperties.inputStream().use { load(it) } }
                .getProperty(WrapperExecutor.DISTRIBUTION_URL_PROPERTY)
                ?.let { WrapperDistributionUrlConverter.convertDistributionUrl(it, wrapperProperties.parentFile) }

            if (currentWrapperUri != null &&
                currentWrapperUri.host != DEFAULT_GRADLE_DIST_REPO_HOST_NAME &&
                currentWrapperUri.rawFragment == null &&
                currentWrapperUri.rawQuery == null
            ) {
                return currentWrapperUri
            }
        }
        return null
    }

    private
    fun rootBuildDir() =
        // project.gradle.root.settings may not be available, so we need to compute the root directory from the start parameters
        BuildLayoutFactory().getLayoutFor((project.gradle as GradleInternal).root.startParameter.toBuildLayoutConfiguration()).rootDirectory

    private
    fun findStandardCustomBasePath(customUri: URI): String? {
        val uriPath = customUri.path ?: return null
        val fileNameSepIndex = uriPath.lastIndexOf('/')
        if (fileNameSepIndex >= 0) {
            val fileNamePath = uriPath.substring(fileNameSepIndex + 1)
            if (fileNamePath.matches(STANDARD_GRADLE_DIST_FILENAME_REGEX)) {
                return uriPath.take(fileNameSepIndex)
            }
        }
        return null
    }

    private
    fun findCustomGradleDistRepository(): GradleDistRepoDescriptor? {
        val currentWrapperUri = findStandardWrapperUri() ?: return null
        val customBasePath = findStandardCustomBasePath(currentWrapperUri) ?: return null

        val baseUrl = URI(
            currentWrapperUri.scheme,
            null,
            currentWrapperUri.host,
            currentWrapperUri.port,
            customBasePath,
            null,
            null
        )

        return gradleDistRepoDescriptor(
            if (customBasePath.endsWith(repositoryName)) repositoryName else "custom",
            baseUrl,
            DEFAULT_GRADLE_DIST_ARTIFACT_PATTERN,
            wrapperCredentials(currentWrapperUri)
        )
    }

    private
    fun gradleDistRepoDescriptor(
        repoName: String,
        repoBaseUrl: URI,
        artifactPattern: String,
        credentials: WrapperCredentials?
    ) = GradleDistRepoDescriptor(repoName, repoBaseUrl, artifactPattern) { repo ->
        if (credentials != null) {
            when (val usernameAndPassword = credentials.usernameAndPassword()) {
                null -> repo.credentials(HttpHeaderCredentials::class.java) {
                    val header = credentials.authorizationHeader()
                    name = header.key
                    value = header.value
                }

                else -> repo.credentials {
                    username = usernameAndPassword.key
                    password = usernameAndPassword.value
                }
            }
        }
    }
}

data class GradleDistVersion(val versionString: String) {
    val isSnapshot: Boolean = versionString.contains('+')
}

data class GradleDistRepoDescriptor(
    val name: String,
    val repoBaseUrl: URI,
    val artifactPattern: String,
    val credentialsApplier: (AuthenticationSupported) -> Unit
)
