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

package org.gradle.smoketests

import spock.lang.Ignore

class NebulaPluginsSmokeSpec extends AbstractSmokeSpec {
    def 'nebula recommender plugin'() {
        when:
        buildFile << """
            plugins {
              id "nebula.dependency-recommender" version "3.3.0"
            }

            apply plugin: 'java'

            repositories {
               jcenter()
            }

            dependencyRecommendations {
               mavenBom module: 'netflix:platform:latest.release'
            }

            dependencies {
               compile 'com.google.guava:guava' // no version, version is recommended
               compile 'commons-lang:commons-lang:2.6' // I know what I want, don't recommend
            }
            """

        then:
        runner().withArguments('build').build()
    }

    @Ignore("""org.codehaus.groovy.runtime.typehandling.GroovyCastException: Cannot cast object 'root project 'junit870421439514898935'' with class 'org.gradle.api.internal.project.DefaultProject_Decorated' to class 'org.gradle.api.internal.project.AbstractProject'
               at nebula.plugin.responsible.NebulaFacetPlugin.container(NebulaFacetPlugin.groovy:119)""")
    def 'nebula plugin plugin'() {
        when:
        buildFile << """
            plugins {
               id 'nebula.plugin-plugin' version '4.15.0'
            }
        """

        file("src/main/groovy/pkg/Thing.java") << """
            package pkg;

            import java.util.ArrayList;
            import java.util.List;

            public class Thing {
                   private List<String> firstOrderDepsWithoutVersions = new ArrayList<>();
            }
        """

        then:
        runner().withArguments('groovydoc', '-s').build()
    }

    @Ignore("No service of type StyledTextOutputFactory available in ProjectScopeServices")
    def 'nebula lint plugin jcenter'() {
        when:
        buildFile << """
            buildscript {
              repositories {
                jcenter()
              }
              dependencies {
                classpath "com.netflix.nebula:gradle-lint-plugin:0.30.4"
              }
            }

            apply plugin: "nebula.lint"
        """.stripIndent()

        then:
        runner().withArguments('buildEnvironment', 'lintGradle', '-s').build()
    }

    def 'nebula dependency lock plugin jcenter'() {
        when:
        buildFile << """
            buildscript {
              repositories {
                maven {
                  url "https://plugins.gradle.org/m2/"
                }
              }
              dependencies {
                classpath "com.netflix.nebula:gradle-dependency-lock-plugin:4.3.0"
              }
            }

            apply plugin: "nebula.dependency-lock"
        """.stripIndent()

        then:
        runner().withArguments('buildEnvironment', 'generateLock', '-s').build()
    }
}
