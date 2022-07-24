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

import org.gradle.api.Action
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler


/**
 * Facilitates the implementation of the [DependencyConstraintHandler] interface by delegation via subclassing.
 *
 * See [GradleDelegate] for why this is currently necessary.
 */
abstract class DependencyConstraintHandlerDelegate : DependencyConstraintHandler {

    internal
    abstract val delegate: DependencyConstraintHandler

    override fun add(configurationName: String, dependencyConstraintNotation: Any): DependencyConstraint =
        delegate.add(configurationName, dependencyConstraintNotation)

    override fun add(configurationName: String, dependencyNotation: Any, configureAction: Action<in DependencyConstraint>): DependencyConstraint =
        delegate.add(configurationName, dependencyNotation, configureAction)

    override fun create(dependencyConstraintNotation: Any): DependencyConstraint =
        delegate.create(dependencyConstraintNotation)

    override fun create(dependencyConstraintNotation: Any, configureAction: Action<in DependencyConstraint>): DependencyConstraint =
        delegate.create(dependencyConstraintNotation, configureAction)

    override fun enforcedPlatform(notation: Any): DependencyConstraint =
        delegate.enforcedPlatform(notation)

    override fun enforcedPlatform(notation: Any, configureAction: Action<in DependencyConstraint>): DependencyConstraint =
        delegate.enforcedPlatform(notation, configureAction)
}
