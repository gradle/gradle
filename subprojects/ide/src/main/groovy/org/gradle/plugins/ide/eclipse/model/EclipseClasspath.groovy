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

import org.gradle.api.tasks.SourceSet
import org.gradle.api.artifacts.Configuration

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

}
