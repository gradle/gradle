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

import org.gradle.api.artifacts.Dependency
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.initialization.dsl.ScriptHandler.CLASSPATH_CONFIGURATION

class KotlinScriptHandler(scriptHandler: ScriptHandler) : ScriptHandler by scriptHandler {

    /**
     * The dependencies of the script.
     */
    val dependencies = KotlinDependencyHandler(scriptHandler.dependencies)

    /**
     * The repositories which are used for retrieving dependencies for the script classpath.
     */
    val repositories = KotlinRepositoryHandler(scriptHandler.repositories)

    /**
     * Adds a dependency to the script classpath.
     */
    fun KotlinDependencyHandler.classpath(dependencyNotation: Any): Dependency =
        add(CLASSPATH_CONFIGURATION, dependencyNotation)
}
