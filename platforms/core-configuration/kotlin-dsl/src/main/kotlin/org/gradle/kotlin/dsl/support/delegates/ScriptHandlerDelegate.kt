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
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.DependencyLockingHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.internal.deprecation.DeprecationLogger
import java.io.File
import java.net.URI


/**
 * Facilitates the implementation of the [ScriptHandler] interface by delegation via subclassing.
 */
@Deprecated("Will be removed in Gradle 9.0")
abstract class ScriptHandlerDelegate : ScriptHandler {

    init {
        @Suppress("DEPRECATION")
        DeprecationLogger.deprecateType(ScriptHandlerDelegate::class.java)
            .willBeRemovedInGradle9()
            .undocumented()
            .nagUser()
    }

    internal
    abstract val delegate: ScriptHandler

    override fun getSourceFile(): File? =
        delegate.sourceFile

    override fun getSourceURI(): URI? =
        delegate.sourceURI

    override fun getRepositories(): RepositoryHandler =
        delegate.repositories

    override fun repositories(configureClosure: Closure<Any>) =
        delegate.repositories(configureClosure)

    override fun getDependencies(): DependencyHandler =
        delegate.dependencies

    override fun dependencies(configureClosure: Closure<Any>) =
        delegate.dependencies(configureClosure)

    override fun getConfigurations(): ConfigurationContainer =
        delegate.configurations

    override fun configurations(configureClosure: Closure<Any>) =
        delegate.configurations(configureClosure)

    override fun dependencyLocking(configureClosure: Closure<Any>) =
        delegate.dependencyLocking(configureClosure)

    override fun getDependencyLocking(): DependencyLockingHandler =
        delegate.dependencyLocking

    override fun getClassLoader(): ClassLoader =
        delegate.classLoader
}
