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

import org.gradle.api.artifacts.dsl.DependencyHandler

/**
 * @see DependencyHandler
 */
class KotlinDependencyHandler(val dependencies: DependencyHandler) : DependencyHandler by dependencies {

    /**
     * Adds a dependency to the given configuration.
     *
     * @param dependencyNotation notation for the dependency to be added.
     * @return The dependency.
     * @see DependencyHandler.add
     */
    operator fun String.invoke(dependencyNotation: Any) =
        dependencies.add(this, dependencyNotation)

    inline operator fun invoke(configuration: KotlinDependencyHandler.() -> Unit) =
        configuration()
}
