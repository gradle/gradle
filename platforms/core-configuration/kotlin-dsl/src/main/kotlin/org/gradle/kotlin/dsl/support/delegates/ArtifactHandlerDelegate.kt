/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl.support.delegates

import groovy.lang.Closure

import org.gradle.api.Action
import org.gradle.api.artifacts.ConfigurablePublishArtifact
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.dsl.ArtifactHandler


/**
 * Facilitates the implementation of the [ArtifactHandler] interface by delegation via subclassing.
 */
abstract class ArtifactHandlerDelegate : ArtifactHandler {

    internal
    abstract val delegate: ArtifactHandler

    override fun add(configurationName: String, artifactNotation: Any): PublishArtifact =
        delegate.add(configurationName, artifactNotation)

    override fun add(configurationName: String, artifactNotation: Any, configureClosure: Closure<Any>): PublishArtifact =
        delegate.add(configurationName, artifactNotation, configureClosure)

    override fun add(configurationName: String, artifactNotation: Any, configureAction: Action<in ConfigurablePublishArtifact>): PublishArtifact =
        delegate.add(configurationName, artifactNotation, configureAction)
}
