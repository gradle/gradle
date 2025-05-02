/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.artifacts.ConfigurablePublishArtifact
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.dsl.ArtifactHandler

import org.gradle.kotlin.dsl.support.delegates.ArtifactHandlerDelegate


/**
 * Receiver for `artifacts` block providing convenient utilities for configuring artifacts.
 *
 * @see ArtifactHandler
 */
class ArtifactHandlerScope
private constructor(
    val artifacts: ArtifactHandler
) : ArtifactHandlerDelegate() {

    companion object {
        /**
         * Creates an [ArtifactHandlerScope] with the given [artifacts]
         * @param artifacts the underlying [ArtifactHandler]
         */
        fun of(artifacts: ArtifactHandler) =
            ArtifactHandlerScope(artifacts)
    }

    override val delegate: ArtifactHandler
        get() = artifacts

    /**
     * Adds an artifact to the given configuration.
     *
     * @param artifactNotation notation of the artifact to add.
     * @return The artifact.
     * @see [ArtifactHandler.add]
     */
    operator fun String.invoke(artifactNotation: Any): PublishArtifact =
        artifacts.add(this, artifactNotation)

    /**
     * Adds an artifact to the given configuration.
     *
     * @param artifactNotation notation of the artifact to add.
     * @param configureAction the action to execute to configure the artifact.
     * @return The artifact.
     * @see [ArtifactHandler.add]
     */
    operator fun String.invoke(artifactNotation: Any, configureAction: ConfigurablePublishArtifact.() -> Unit): PublishArtifact =
        artifacts.add(this, artifactNotation, configureAction)

    /**
     * Adds an artifact to the given configuration.
     *
     * @param artifactNotation notation of the artifact to add.
     * @return The artifact.
     * @see [ArtifactHandler.add]
     */
    operator fun Configuration.invoke(artifactNotation: Any): PublishArtifact =
        add(name, artifactNotation)


    /**
     * Adds an artifact to the given configuration.
     *
     * @param artifactNotation notation of the artifact to add.
     * @param configureAction the action to execute to configure the artifact.
     * @return The artifact.
     * @see [ArtifactHandler.add]
     */
    operator fun Configuration.invoke(artifactNotation: Any, configureAction: ConfigurablePublishArtifact.() -> Unit): PublishArtifact =
        add(name, artifactNotation, configureAction)


    /**
     * Configures the artifacts.
     */
    inline operator fun invoke(configuration: ArtifactHandlerScope.() -> Unit) =
        run(configuration)
}
