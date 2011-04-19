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

/**
 * Dsl-friendly model of the eclipse wtp information
 * <p>
 * Example of use with a blend of all possible properties.
 * Bear in mind that usually you don't have configure them directly because Gradle configures it for free!
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 * apply plugin: 'war'
 * apply plugin: 'eclipse'
 *
 * configurations {
 *   someInterestingConfiguration
 *   anotherConfiguration
 * }
 *
 * eclipse {
 *
 *   //if you want parts of paths in resulting file(s) to be replaced by variables (files):
 *   pathVariables 'GRADLE_HOME': file('/best/software/gradle'), 'TOMCAT_HOME': file('../tomcat')
 *
 *   wtp {
 *     //you can configure the deployName:
 *     deployName = 'killerApp'
 *
 *     //you can alter the wb-resource elements. sourceDirs is a ConvenienceProperty.
 *     sourceDirs += file('someExtraFolder')
 *
 *     //you can alter the files are to be transformed into dependent-module elements:
 *     plusConfigurations += configurations.someInterestingConfiguration
 *
 *     //or whose files are to be excluded from dependent-module elements:
 *     minusConfigurations += configurations.anotherConfiguration
 *
 *     //you can add a wb-resource element; mandatory keys: 'sourcePath', 'deployPath':
 *     resource sourcePath: 'extra/resource' deployPath: 'deployment/resource'
 *   }
 * }
 *
 * </pre>
 *
 * Author: Szczepan Faber, created at: 4/19/11
 */
class EclipseWtp {

    /**
     * The source directories to be transformed into wb-resource elements.
     * <p>
     * Warning, this property is a {@link org.gradle.api.dsl.ConvenienceProperty}
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    Set<File> sourceDirs

    /**
     * The configurations whose files are to be transformed into dependent-module elements.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    Set<Configuration> plusConfigurations

    /**
     * The configurations whose files are to be excluded from dependent-module elements.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    Set<Configuration> minusConfigurations

    /**
     * The deploy name to be used.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    String deployName

    /**
     * Additional wb-resource elements.
     * <p>
     * Warning, this property is a {@link org.gradle.api.dsl.ConvenienceProperty}
     * <p>
     * For examples see docs for {@link EclipseWtp}
     */
    List<WbResource> resources = []

    /**
     * Adds a wb-resource.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     *
     * @param args A map that must contain a deployPath and sourcePath key with corresponding values.
     */
    void resource(Map<String, String> args) {
        //TODO SF validation
        resources.add(new WbResource(args.deployPath, args.sourcePath))
    }

    //********

    /**
     * The variables to be used for replacing absolute path in dependent-module elements.
     * <p>
     * For examples see docs for {@link EclipseModel}
     */
    Map<String, File> pathVariables = [:]
}
