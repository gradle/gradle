/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.plugins.ide.idea.model

import org.gradle.api.artifacts.Configuration
import org.gradle.plugins.ide.idea.model.internal.IdeaDependenciesProvider

/**
 * Model for idea module.
 * <p>
 * Example of use with a blend of various properties.
 * Bear in mind that usually you don't have configure idea module directly because Gradle configures it for free!
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 * apply plugin: 'idea'
 *
 * //for the sake of the example lets have a 'provided' dependency configuration
 * configurations {
 *   provided
 *   provided.extendsFrom(compile)
 * }
 *
 * dependencies {
 *   //provided "some.interesting:dependency:1.0"
 * }
 *
 * idea {
 *   module {
 *     //if for some reason you want add an extra sourceDirs
 *     sourceDirs += file('some-custom-source-folder')
 *
 *     //if you don't like the name Gradle have chosen
 *     name = 'some-better-name'
 *
 *     //if you need to put provided dependencies on the classpath
 *     scopes.COMPILE.plus += configurations.provided
 *
 *     //if you love browsing javadocs
 *     downloadJavadoc = true
 *
 *     //and hate reading sources :)
 *     downloadSources = false
 *   }
 * }
 *
 * </pre>
 *
 * Author: Szczepan Faber, created at: 3/31/11
 */
class IdeaModule {

    org.gradle.api.Project project

   /**
     * Idea module name; controls the name of the *.iml file
     */
    String name

    /**
     * The directories containing the production sources.
     */
    Set<File> sourceDirs

    /**
     * The keys of this map are the Intellij scopes. Each key points to another map that has two keys, plus and minus.
     * The values of those keys are sets of  {@link org.gradle.api.artifacts.Configuration}  objects. The files of the
     * plus configurations are added minus the files from the minus configurations. See example below...
     * <p>
     * Example how to use scopes property to enable 'provided' dependencies in the output *.iml file:
     * <pre autoTested=''>
     * apply plugin: 'java'
     * apply plugin: 'idea'
     *
     * configurations {
     *   provided
     *   provided.extendsFrom(compile)
     * }
     *
     * dependencies {
     *   //provided "some.interesting:dependency:1.0"
     * }
     *
     * idea {
     *   module {
     *     scopes.COMPILE.plus += configurations.provided
     *   }
     * }
     * </pre>
     */
    Map<String, Map<String, Configuration>> scopes = [:]

    /**
     * Whether to download and add sources associated with the dependency jars.
     */
    boolean downloadSources = true

    /**
     * Whether to download and add javadoc associated with the dependency jars.
     */
    boolean downloadJavadoc = false

    protected Set<Path> getSourcePaths(PathFactory pathFactory) {
        getSourceDirs().findAll { it.exists() }.collect { pathFactory.path(it) }
    }

    protected Set getDependencies(PathFactory pathFactory) {
        new IdeaDependenciesProvider().provide(this, pathFactory);
    }
}
