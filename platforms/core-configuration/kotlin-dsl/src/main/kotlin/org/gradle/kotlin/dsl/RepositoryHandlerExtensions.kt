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

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.FileSystemLocation
import org.gradle.internal.deprecation.DeprecationLogger
import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.Path

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
 * @see [MavenArtifactRepository.setUrl]
 */
fun RepositoryHandler.maven(url: String) =
    maven {
        // for now, use the deprecated method to preserve current behavior (with file path resolving)
        // later, it can be changed to getUrl().set(URI.create(url))
        DeprecationLogger.whileDisabled {
            @Suppress("deprecation")
            setUrl(url)
        }
    }
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
 * @see [MavenArtifactRepository.setUrl]
 */
@Deprecated("Use the version that takes a URI instead", ReplaceWith("maven(uri(url))"))
fun RepositoryHandler.maven(url: CharSequence) =
    maven {
        @Suppress("deprecation")
        setUrl(url)
    }
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
 * @see [MavenArtifactRepository.setUrl]
 */
@Deprecated("Use the version that takes a URI instead", ReplaceWith("maven(uri(url))"))
fun RepositoryHandler.maven(url: File) =
    maven {
        @Suppress("deprecation")
        setUrl(url)
    }

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
 * @see [MavenArtifactRepository.setUrl]
 */
@Deprecated("Use the version that takes a URI instead", ReplaceWith("maven(uri(url))"))
fun RepositoryHandler.maven(url: FileSystemLocation) =
    maven {
        @Suppress("deprecation")
        setUrl(url)
    }

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
 * @see [MavenArtifactRepository.setUrl]
 */
@Deprecated("Use the version that takes a URI instead", ReplaceWith("maven(uri(url))"))
fun RepositoryHandler.maven(url: Path) =
    maven {
        @Suppress("deprecation")
        setUrl(url)
    }
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
 * @see [MavenArtifactRepository.setUrl]
 */
@Deprecated("Use the version that takes a URI instead", ReplaceWith("maven(uri(url))"))
fun RepositoryHandler.maven(url: URL) =
    maven {
        @Suppress("deprecation")
        setUrl(url)
    }

/**
 * Adds and configures a Maven repository.
 *
 * @param url the base URL of this repository. This URL is used to find both POMs and artifact files.
 * @return The added repository.
 *
 * @see [RepositoryHandler.maven]
 * @see [MavenArtifactRepository.setUrl]
 */
fun RepositoryHandler.maven(url: URI) =
    maven { getUrl().set(url) }

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
 */
fun RepositoryHandler.maven(url: String, action: MavenArtifactRepository.() -> Unit) =
    maven {
        // for now, use the deprecated method to preserve current behavior (with file path resolving)
        // later, it can be changed to getUrl().set(URI.create(url))
        DeprecationLogger.whileDisabled {
            @Suppress("deprecation")
            setUrl(url)
        }
        action()
    }
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
 */
@Deprecated("Use the version that takes a URI instead", ReplaceWith("maven(uri(url)){ ... }"))
fun RepositoryHandler.maven(url: CharSequence, action: MavenArtifactRepository.() -> Unit) =
    maven {
        @Suppress("deprecation")
        setUrl(url)
        action()
    }
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
 */
@Deprecated("Use the version that takes a URI instead", ReplaceWith("maven(uri(url)){ ... }"))
fun RepositoryHandler.maven(url: File, action: MavenArtifactRepository.() -> Unit) =
    maven {
        @Suppress("deprecation")
        setUrl(url)
        action()
    }
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
 */
@Deprecated("Use the version that takes a URI instead", ReplaceWith("maven(uri(url)){ ... }"))
fun RepositoryHandler.maven(url: FileSystemLocation, action: MavenArtifactRepository.() -> Unit) =
    maven {
        @Suppress("deprecation")
        setUrl(url)
        action()
    }
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
 */
@Deprecated("Use the version that takes a URI instead", ReplaceWith("maven(uri(url)){ ... }"))
fun RepositoryHandler.maven(url: Path, action: MavenArtifactRepository.() -> Unit) =
    maven {
        @Suppress("deprecation")
        setUrl(url)
        action()
    }
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
 */
@Deprecated("Use the version that takes a URI instead", ReplaceWith("maven(uri(url)){ ... }"))
fun RepositoryHandler.maven(url: URL, action: MavenArtifactRepository.() -> Unit) =
    maven {
        @Suppress("deprecation")
        setUrl(url)
        action()
    }
/**
 * Adds and configures a Maven repository.
 *
 * @param url The base URL of this repository. This URL is used to find both POMs and artifact files.
 * @param action The action to use to configure the repository.
 * @return The added repository.
 *
 * @see [RepositoryHandler.maven]
 * @see [MavenArtifactRepository.setUrl]
 */
fun RepositoryHandler.maven(url: URI, action: MavenArtifactRepository.() -> Unit) =
    maven {
        getUrl().set(url)
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
 */
fun RepositoryHandler.ivy(url: String) =
    ivy {
        // for now, use the deprecated method to preserve current behavior (with file path resolving)
        // later, it can be changed to getUrl().set(URI.create(url))
        DeprecationLogger.whileDisabled {
            @Suppress("deprecation")
            setUrl(url)
        }
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
 */
@Deprecated("Use the version that takes a URI instead", ReplaceWith("ivy(uri(url))"))
fun RepositoryHandler.ivy(url: CharSequence) =
    ivy {
        @Suppress("deprecation")
        setUrl(url)
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
 */
@Deprecated("Use the version that takes a URI instead", ReplaceWith("ivy(uri(url))"))
fun RepositoryHandler.ivy(url: File) =
    ivy {
        @Suppress("deprecation")
        setUrl(url)
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
 */
@Deprecated("Use the version that takes a URI instead", ReplaceWith("ivy(uri(url))"))
fun RepositoryHandler.ivy(url: FileSystemLocation) =
    ivy {
        @Suppress("deprecation")
        setUrl(url)
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
 */
@Deprecated("Use the version that takes a URI instead", ReplaceWith("ivy(uri(url))"))
fun RepositoryHandler.ivy(url: Path) =
    ivy {
        @Suppress("deprecation")
        setUrl(url)
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
 */
@Deprecated("Use the version that takes a URI instead", ReplaceWith("ivy(uri(url))"))
fun RepositoryHandler.ivy(url: URL) =
    ivy {
        @Suppress("deprecation")
        setUrl(url)
    }

/**
 * Adds and configures an Ivy repository.
 *
 * @param url The base URL of this repository.
 *
 * @return The added repository.
 *
 * @see [RepositoryHandler.ivy]
 */
fun RepositoryHandler.ivy(url: URI) =
    ivy { getUrl().set(url) }

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
 */
fun RepositoryHandler.ivy(url: String, action: IvyArtifactRepository.() -> Unit) =
    ivy {
        // for now, use the deprecated method to preserve current behavior (with file path resolving)
        // later, it can be changed to getUrl().set(URI.create(url))
        DeprecationLogger.whileDisabled {
            @Suppress("deprecation")
            setUrl(url)
        }
        action()
    }
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
 */
@Deprecated("Use the version that takes a URI instead", ReplaceWith("ivy(uri(url)){ ... }"))
fun RepositoryHandler.ivy(url: CharSequence, action: IvyArtifactRepository.() -> Unit) =
    ivy {
        @Suppress("deprecation")
        setUrl(url)
        action()
    }
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
 */
@Deprecated("Use the version that takes a URI instead", ReplaceWith("ivy(uri(url)){ ... }"))
fun RepositoryHandler.ivy(url: File, action: IvyArtifactRepository.() -> Unit) =
    ivy {
        @Suppress("deprecation")
        setUrl(url)
        action()
    }
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
 */
@Deprecated("Use the version that takes a URI instead", ReplaceWith("ivy(uri(url)){ ... }"))
fun RepositoryHandler.ivy(url: FileSystemLocation, action: IvyArtifactRepository.() -> Unit) =
    ivy {
        @Suppress("deprecation")
        setUrl(url)
        action()
    }
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
 */
@Deprecated("Use the version that takes a URI instead", ReplaceWith("ivy(uri(url)){ ... }"))
fun RepositoryHandler.ivy(url: Path, action: IvyArtifactRepository.() -> Unit) =
    ivy {
        @Suppress("deprecation")
        setUrl(url)
        action()
    }
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
 */
@Deprecated("Use the version that takes a URI instead", ReplaceWith("ivy(uri(url)){ ... }"))
fun RepositoryHandler.ivy(url: URL, action: IvyArtifactRepository.() -> Unit) =
    ivy {
        @Suppress("deprecation")
        setUrl(url)
        action()
    }
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
 */
fun RepositoryHandler.ivy(url: URI, action: IvyArtifactRepository.() -> Unit) =
    ivy {
        getUrl().set(url)
        action()
    }
