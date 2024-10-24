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
package org.gradle.kotlin.dsl

import org.gradle.api.Incubating
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File
import java.net.URI
import java.net.URISyntaxException


/**
 * Adds and configures a Maven repository.
 *
 * The provided [url] value is evaluated as per [org.gradle.api.Project.uri]. This means, for example, you can pass in a `File` object, or a relative path to be evaluated relative
 * to the project directory.
 *
 * @param url the base URL of this repository. This URL is used to find both POMs and artifact files.
 * @return The added repository.
 *
 * @see [RepositoryHandler.maven]
 * @see [MavenArtifactRepository.getUrl]
 * @deprecated Use ```maven { url = ... }``` instead.
 */
@Deprecated("Use maven { url = ... } instead.", replaceWith = ReplaceWith("maven { url = ... }"))
fun RepositoryHandler.maven(url: Any) =
    maven { getUrl().set(convertToURI(url)) }

/**
 * Adds and configures a Maven repository.
 *
 * The provided [url] value is evaluated as per [org.gradle.api.Project.uri]. This means, for example, you can pass in a `File` object, or a relative path to be evaluated relative
 * to the project directory.
 *
 * @param url The base URL of this repository. This URL is used to find both POMs and artifact files.
 * @param action The action to use to configure the repository.
 * @return The added repository.
 *
 * @see [RepositoryHandler.maven]
 * @see [MavenArtifactRepository.setUrl]
 * @deprecated Use ```maven { url = ... }``` instead.
 */
@Deprecated("Use maven { url = ... } instead.", replaceWith = ReplaceWith("maven { url = ... }"))
fun RepositoryHandler.maven(url: Any, action: MavenArtifactRepository.() -> Unit) =
    maven {
        getUrl().set(convertToURI(url))
        action()
    }

/**
 * Adds and configures an Ivy repository.
 *
 * The provided [url] value is evaluated as per [org.gradle.api.Project.uri]. This means, for example, you can pass in a `File` object, or a relative path to be evaluated relative
 * to the project directory.
 *
 * @param url The base URL of this repository.
 *
 * @return The added repository.
 *
 * @see [RepositoryHandler.ivy]
 * @deprecated Use ```ivy { url = ... }``` instead.
 */
@Deprecated("Use ivy { url = ... } instead.", replaceWith = ReplaceWith("ivy { url = ... }"))
fun RepositoryHandler.ivy(url: Any) =
    ivy { getUrl().set(convertToURI(url)) }

/**
 * Adds and configures an Ivy repository.
 *
 * The provided [url] value is evaluated as per [org.gradle.api.Project.uri]. This means, for example, you can pass in a `File` object, or a relative path to be evaluated relative
 * to the project directory.
 *
 * @param url The base URL of this repository.
 * @param action The action to use to configure the repository.
 *
 * @return The added repository.
 *
 * @see [RepositoryHandler.ivy]
 * @deprecated Use ```ivy { url = ... }``` instead.
 */
@Deprecated("Use ivy { url = ... } instead.", replaceWith = ReplaceWith("ivy { url = ... }"))
fun RepositoryHandler.ivy(url: Any, action: IvyArtifactRepository.() -> Unit) =
    ivy {
        getUrl().set(convertToURI(url))
        action()
    }

private fun convertToURI(url: Any?): URI {
    return when (url) {
        is URI -> url
        is String -> try {
            URI(url)
        } catch (e: URISyntaxException) {
            throw InvalidUserDataException("Invalid URI: $url", e)
        }
        is File -> url.toURI()
        is FileSystemLocation -> url.asFile.toURI()
        else -> throw InvalidUserDataException("Unsupported URI type: ${url?.javaClass}")
    }
}

/**
 * @since 9.0
 */
@Incubating
fun Property<URI>.assign(value: String) {
    val uri = try {
        URI(value)
    } catch (e: URISyntaxException) {
        throw InvalidUserDataException("Invalid URI: $value", e)
    }
    set(uri)
}

/**
 * @since 9.0
 */
@Incubating
fun Property<URI>.assign(value: File) {
    set(value.toURI())
}

/**
 * @since 9.0
 */
@Incubating
fun Property<URI>.assign(value: FileSystemLocation) {
    assign(value.asFile)
}

/**
 * @since 9.0
 */
@Incubating
fun Property<URI>.assign(value: Provider<String>) {
    set(
        value.map { url ->
            try {
                URI(url)
            } catch (e: URISyntaxException) {
                throw InvalidUserDataException("Invalid URI: $url", e)
            }
        }
    )
}

/**
 * @since 9.0
 */
@Incubating
@JvmName("assingProviderFile")
fun Property<URI>.assign(value: Provider<out File>) {
    set(value.map { it.toURI() })
}

/**
 * @since 9.0
 */
@Incubating
@JvmName("assingProviderFileSystemLocation")
fun Property<URI>.assign(value: Provider<out FileSystemLocation>) {
    assign(value.map { it.asFile })
}

