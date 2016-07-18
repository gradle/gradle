/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.script.lang.kotlin

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository

/**
 * @see RepositoryHandler
 */
class KotlinRepositoryHandler(val repositoryHandler: RepositoryHandler) : RepositoryHandler by repositoryHandler {

    /**
     * Adds and configures a Maven repository.
     */
    fun maven(configuration: MavenArtifactRepository.() -> Unit) =
        repositoryHandler.maven({ it.configuration() })

    /**
     * Adds and configures an Ivy repository.
     */
    fun ivy(configuration: IvyArtifactRepository.() -> Unit) =
        repositoryHandler.ivy({ it.configuration() })

    inline operator fun invoke(configuration: KotlinRepositoryHandler.() -> Unit) =
        configuration()
}
