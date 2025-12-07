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
import org.gradle.util.internal.WrapperDistributionUrlConverter
import org.gradle.wrapper.WrapperExecutor
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.util.Properties

private const val DEFAULT_GRADLE_DIST_REPO_HOST_NAME = "services.gradle.org"
private const val DEFAULT_GRADLE_DIST_ARTIFACT_PATTERN = "[module]-[revision](-[classifier])(.[ext])"
private val STANDARD_GRADLE_DIST_FILENAME_REGEX = Regex("gradle-[0-9]+(?:\\.[0-9]+){1,2}(?:-[a-zA-Z0-9+]+)*-(?:bin|all).zip")

class GradleDistRepoDescriptorLocator(
    private val project: Project,
    val gradleVersion: GradleDistVersion = GradleDistVersion(project.gradle.gradleVersion),
    private val explicitRootProjectDir: File? = null
) {
    // `explicitRootProjectDir` is needed for testing only to be able to set some arbitrary non-working URL
    private
    val rootProjectDir
        get() = explicitRootProjectDir ?: project.layout.settingsDirectory.asFile

    private
    val repositoryName
        get() = if (gradleVersion.snapshot) "distributions-snapshots" else "distributions"

    val gradleDistRepository
        get(): GradleDistRepoDescriptor {
            try {
                return findCustomGradleDistRepository() ?: defaultGradleDistRepository
            } catch (ex: Exception) {
                project.logger.warn("Unexpected exception while trying to find the URL for Gradle sources: ${ex.message}", ex)
                return defaultGradleDistRepository
            }
        }

    private
    val defaultGradleDistRepository
        get(): GradleDistRepoDescriptor {
            val capturedRepositoryName = repositoryName
            return GradleDistRepoDescriptor(
                capturedRepositoryName,
                "https://${DEFAULT_GRADLE_DIST_REPO_HOST_NAME}/$capturedRepositoryName",
                "[module]-[revision](-[classifier])(.[ext])",
                null
            )
        }

    private
    fun wrapperCredentials(baseUrl: String? = null): GradleDistRepoCredentials? {
        wrapperCredentialsFromSystemProperties()?.let { return it }
        baseUrl
            ?.let { wrapperCredentialsFromUrl(it) }
            ?.let { return it }
        return null
    }

    private
    fun wrapperCredentialsFromUrl(baseUrl: String): GradleDistRepoCredentials? {
        val userInfo = try {
            URI(baseUrl).userInfo ?: return null
        } catch (_: URISyntaxException) {
            return null
        }

        val urlCredentials = userInfo.split(':', limit = 2)
        if (urlCredentials.size != 2) {
            return null
        }
        return GradleDistRepoCredentials(urlCredentials[0], urlCredentials[1])
    }

    private
    fun wrapperCredentialsFromSystemProperties(): GradleDistRepoCredentials? {
        return GradleDistRepoCredentials(
            System.getProperty(WrapperDistributionUrlConverter.WRAPPER_USER_SYSTEM_PROPERTY_NAME) ?: return null,
            System.getProperty(WrapperDistributionUrlConverter.WRAPPER_PASSWORD_SYSTEM_PROPERTY_NAME) ?: return null
        )
    }

    private
    fun gradleDistRepositoryProperty(baseName: String) = project.providers.gradleProperty("org.gradle.distributions.source.repository.$baseName").orNull

    private
    fun gradleDistCredentialsFromGradleProperties(): GradleDistRepoCredentials? {
        return GradleDistRepoCredentials(
            gradleDistRepositoryProperty("credential.username") ?: return null,
            gradleDistRepositoryProperty("credential.password") ?: return null
        )
    }

    private
    fun findStandardWrapperUri(): URI? {
        val wrapperProperties = WrapperExecutor.wrapperPropertiesForProjectDirectory(rootProjectDir)
        if (!wrapperProperties.exists()) {
            return null
        }

        val currentWrapperUri = Properties()
            .also { props ->
                wrapperProperties.inputStream().use { props.load(it) }
            }
            .getProperty(WrapperExecutor.DISTRIBUTION_URL_PROPERTY)
            ?.let { WrapperDistributionUrlConverter.convertDistributionUrl(it, wrapperProperties.parentFile) }
            ?: return null

        if (currentWrapperUri.host == DEFAULT_GRADLE_DIST_REPO_HOST_NAME ||
            currentWrapperUri.rawFragment != null ||
            currentWrapperUri.rawQuery != null
        ) {
            return null
        }
        return currentWrapperUri
    }

    private
    fun findStandardCustomBasePath(customUri: URI): String? {
        val uriPath = customUri.path ?: return null
        val fileNameSepIndex = uriPath.lastIndexOf('/')
        if (fileNameSepIndex < 0) {
            return null
        }
        val fileNamePath = uriPath.substring(fileNameSepIndex + 1)
        if (!fileNamePath.matches(STANDARD_GRADLE_DIST_FILENAME_REGEX)) {
            return null
        }

        return uriPath.take(fileNameSepIndex)
    }

    private
    fun findCustomGradleDistRepository(): GradleDistRepoDescriptor? {
        val explicitBaseUrl = gradleDistRepositoryProperty("url")
        if (explicitBaseUrl != null) {
            return GradleDistRepoDescriptor(
                "custom",
                explicitBaseUrl,
                gradleDistRepositoryProperty("ivyArtifactPattern") ?: DEFAULT_GRADLE_DIST_ARTIFACT_PATTERN,
                gradleDistCredentialsFromGradleProperties() ?: wrapperCredentials(explicitBaseUrl)
            )
        }

        val currentWrapperUri = findStandardWrapperUri() ?: return null
        val customBasePath = findStandardCustomBasePath(currentWrapperUri) ?: return null

        val baseUrl = URI(
            currentWrapperUri.scheme,
            currentWrapperUri.userInfo,
            currentWrapperUri.host,
            currentWrapperUri.port,
            customBasePath,
            null,
            null
        ).toString()

        return GradleDistRepoDescriptor(
            if (customBasePath.endsWith(repositoryName)) repositoryName else "custom",
            baseUrl,
            DEFAULT_GRADLE_DIST_ARTIFACT_PATTERN,
            wrapperCredentials(baseUrl)
        )
    }
}

data class GradleDistVersion(
    val versionStr: String,
    val snapshot: Boolean = versionStr.contains('+')
)

data class GradleDistRepoCredentials(val username: String, val password: String)

data class GradleDistRepoDescriptor(
    val name: String,
    val repoBaseUrl: String,
    val artifactPattern: String,
    val credentials: GradleDistRepoCredentials?
)
