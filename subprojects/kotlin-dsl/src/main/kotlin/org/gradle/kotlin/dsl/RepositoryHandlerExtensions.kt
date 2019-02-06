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
fun RepositoryHandler.maven(url: Any) =
    maven { it.setUrl(url) }


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
fun RepositoryHandler.maven(url: Any, action: MavenArtifactRepository.() -> Unit) =
    maven {
        it.setUrl(url)
        it.action()
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
fun RepositoryHandler.ivy(url: Any) =
    ivy { it.setUrl(url) }


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
fun RepositoryHandler.ivy(url: Any, action: IvyArtifactRepository.() -> Unit) =
    ivy {
        it.setUrl(url)
        it.action()
    }
