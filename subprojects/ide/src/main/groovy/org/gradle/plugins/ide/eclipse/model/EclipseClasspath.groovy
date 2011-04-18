/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.model

import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSet

/**
 * DSL-friendly model of the eclipse classpath needed for .classpath generation
 * <p>
 * Example of use with a blend of all possible properties.
 * Bear in mind that usually you don't have configure eclipse classpath directly because Gradle configures it for free!
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 * apply plugin: 'eclipse'
 *
 * configurations {
 *   provided
 *   someBoringConfig
 * }
 *
 * eclipse {
 *   classpath {
 *     //you can configure the sourceSets however Gradle simply uses current sourceSets
 *     //so it's probably best not to change it.
 *     //sourceSets =
 *
 *     //you can tweak the classpath of the eclipse project by adding extra configurations:
 *     plusConfigurations += configurations.provided
 *
 *     //you can also remove configurations from the classpath:
 *     minusConfigurations += configurations.someBoringConfig
 *
 *     //if you want to append extra containers:
 *     containers 'someFriendlyContainer', 'andYetAnotherContainer'
 *
 *     //customizing the classes output directory:
 *     classesOutputDir = file('build-eclipse')
 *   }
 * }
 * </pre>
 *
 * Author: Szczepan Faber, created at: 4/16/11
 */
class EclipseClasspath {

    /**
     * The source sets to be added to the classpath.
     */
    Iterable<SourceSet> sourceSets

    /**
     * The configurations which files are to be transformed into classpath entries.
     */
    Set<Configuration> plusConfigurations = new LinkedHashSet<Configuration>()

    /**
     * The configurations which files are to be excluded from the classpath entries.
     */
    Set<Configuration> minusConfigurations = new LinkedHashSet<Configuration>()

    /**
     * The path variables to be used for replacing absolute paths in classpath entries.
     * A map with String->File pairs.
     */
    Map<String, File> pathVariables = [:]

    /**
     * Adds path variables to be used for replacing absolute paths in classpath entries.
     *
     * @param pathVariables A map with String->File pairs.
     */
    void pathVariables(Map<String, File> pathVariables) {
        assert pathVariables != null
        this.pathVariables.putAll pathVariables
    }

   /**
     * Containers to be added to the classpath
     */
    Set<String> containers = new LinkedHashSet<String>()

   /**
     * Adds containers to the .classpath.
     *
     * @param containers the container names to be added to the .classpath.
     */
    void containers(String... containers) {
        assert containers != null
        this.containers.addAll(containers as List)
    }

    /**
     * The default output directory for eclipse generated files, eg classes.
     */
    File classesOutputDir
}
