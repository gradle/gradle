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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.util.Matchers
import spock.lang.Ignore

/**
 * Tests for plugin development scenarios within a composite build.
 */
class CompositeBuildPluginDevelopmentIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile pluginBuild
    BuildTestFile pluginDependencyA

    def setup() {
        pluginDependencyA = singleProjectBuild("pluginDependencyA") {
            buildFile << """
                apply plugin: 'java'
                version "2.0"
"""
        }

        pluginBuild = pluginProjectBuild("pluginBuild")
    }

    def "can co-develop plugin and consumer with plugin as included build"() {
        given:
        applyPlugin(buildA)

        includeBuild pluginBuild

        when:
        execute(buildA, "tasks")

        then:
        outputContains("taskFromPluginBuild")
    }

    def "can co-develop plugin and consumer with both plugin and consumer as included builds"() {
        given:
        applyPlugin(pluginDependencyA)

        buildA.buildFile << """
            dependencies {
                compile "org.test:pluginDependencyA:1.0"
            }
"""

        includeBuild pluginDependencyA, """
            substitute module("org.test:pluginDependencyA") with project(":")
"""
        includeBuild pluginBuild

        when:
        execute(buildA, "assemble")

        then:
        executed ":pluginBuild:jar", ":pluginDependencyA:jar", ":jar"
    }

    def "can co-develop plugin and consumer where plugin uses previous version of itself to build"() {
        given:
        // Ensure that 'plugin' is published with older version
        mavenRepo.module("org.test", "pluginBuild", "0.1").publish()

        pluginBuild.buildFile << """
            buildscript {
                repositories {
                    repositories {
                        maven { url "${mavenRepo.uri}" }
                    }
                }
                dependencies {
                    classpath 'org.test:pluginBuild:0.1'
                }
            }
"""

        applyPlugin(buildA)

        includeBuild pluginBuild, """
            // Only substitute version 1.0 with project dependency. This allows this project to build with the published dependency.
            substitute module("org.test:pluginBuild:1.0") with project(":")
"""

        when:
        execute(buildA, "tasks")

        then:
        outputContains("taskFromPluginBuild")
    }

    // TODO:DAZ Fix this: https://builds.gradle.org/viewLog.html?buildId=4295932&buildTypeId=Gradle_Check_NoDaemon_Java8_Oracle_Linux_compositeBuilds
    @Ignore("Cycle check is not parallel safe: test may hang or produce StackOverflowError")
    def "detects dependency cycle between included builds required for buildscript classpath"() {
        given:
        def pluginDependencyB = singleProjectBuild("pluginDependencyB") {
            buildFile << """
                apply plugin: 'java'
                version "2.0"
"""
        }

        dependency pluginBuild, "org.test:pluginDependencyA:1.0"
        dependency pluginDependencyA, "org.test:pluginDependencyB:1.0"
        dependency pluginDependencyB, "org.test:pluginDependencyA:1.0"

        applyPlugin(buildA)

        includeBuild pluginBuild
        includeBuild pluginDependencyA
        includeBuild pluginDependencyB

        when:
        fails(buildA, "tasks")

        then:
        failure
            .assertHasDescription("Could not determine the dependencies of task")
            .assertHasCause("Included build dependency cycle:")
            .assertThatCause(Matchers.containsText("build 'pluginDependencyA' -> build 'pluginDependencyB'"))
            .assertThatCause(Matchers.containsText("build 'pluginDependencyB' -> build 'pluginDependencyA'"))
    }
}
