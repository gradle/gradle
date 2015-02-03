/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.plugins.scala

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin

import java.util.regex.Pattern

/**
 * Alternative mechanism to configure the scala runtime version, rather than deriving it from the compile configuration.
 * This crystallises existing conventions into a standard mechanism, providing the scala binary version for other dependencies.
 * It does not necessarily supersede the existing ScalaRuntime extension.
 *
 * <p>Example usage:
 *
 * <pre autoTested="">
 *     apply plugin: "scala"
 *
 *     repositories {*         mavenCentral()
 *}*
 *     scala {*         version = '2.11.5'
 *}*
 *     dependencies {*         compile "org.scalatest:scalatest_${scala.binaryVersion}:2.2.4"
 *}* </pre>
 */
class ScalaLibraryExtension {

    static String scalaVersionProperty = 'scalaVersion'
    static String scalaComponentsProperty = 'scalaComponents'
    private static Pattern scalaVersionPattern = ~/(\d+\.\d+)\.\d+/

    String version = ""
    Boolean publishVersion = true
    List<String> additionalComponents = []

    ScalaLibraryExtension(Project project) {
        if (project.hasProperty(scalaVersionProperty)) {
            version = project.property(scalaVersionProperty) as String
        }
        if (project.hasProperty(scalaComponentsProperty)) {
            additionalComponents = project.property(scalaComponentsProperty) as List<String>
        }
        project.afterEvaluate { p ->
            def scala = p.scala
            if (scala.version) {
                p.dependencies.add(JavaPlugin.COMPILE_CONFIGURATION_NAME, [group: 'org.scala-lang', name:'scala-library', version: scala.version])
                if (additionalComponents) {
                    scala.additionalComponents.each { component ->
                        p.dependencies.add(JavaPlugin.COMPILE_CONFIGURATION_NAME, [group: 'org.scala-lang', name: "scala-$component", version: scala.version])
                    }
                }
                if (publishVersion) {
                    p.archivesBaseName += "_${scala.binaryVersion}"
                }
            }
        }
    }

    def getBinaryVersion() {
        def match = (version =~ scalaVersionPattern)
        if (match.matches()) {
            match.group(1)
        } else {
            ""
        }
    }

}