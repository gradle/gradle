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

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.maven.MavenModule

/**
 * Tests for resolving dependency artifacts with substitution within a composite build.
 */
class CompositeBuildCommandLineArgsIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildA
    BuildTestFile buildB
    MavenModule publishedModuleB
    MavenFileRepository mavenRepo

    def setup() {
        mavenRepo = new MavenFileRepository(file("maven-repo"))
        publishedModuleB = mavenRepo.module("org.test", "buildB", "1.0").publish()

        buildA = singleProjectBuild("buildA") {
            buildFile << """
                apply plugin: 'java'
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }

                task resolve(type: Copy) {
                    from configurations.compile
                    into 'libs'
                }
"""
        }

        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                }
"""
        }
        builds = [buildA, buildB]
    }

    def "passes project properties to included build"() {
        given:
        dependency 'org.test:buildB:1.0'
        and:

        [buildA, buildB].each {
            it.buildFile << """
    if (project.getProperty("passedProperty") != "foo") {
        throw new RuntimeException("property not passed to build")
    }
"""
        }

        when:
        execute(buildA, ":resolve", ["-PpassedProperty=foo"])

        then:
        executed ":buildB:jar"
    }

    def "passes system property arguments to included build"() {
        given:
        dependency 'org.test:buildB:1.0'
        and:

        [buildA, buildB].each {
            it.buildFile << """
    if (System.properties['passedProperty'] != "foo") {
        throw new RuntimeException("property not passed to build")
    }
"""
        }

        when:
        execute(buildA, ":resolve", ["-DpassedProperty=foo"])

        then:
        executed ":buildB:jar"
    }

    def "can include same build multiple times using --include-build and settings.gradle"() {
        given:
        dependency 'org.test:buildB:1.0'

        // Include 'buildB' twice via settings.gradle
        buildA.settingsFile << """
includeBuild '${buildB.toURI()}'
includeBuild '${buildB.toURI()}'
"""
        // Include 'buildB' twice via command-line arg
        def args = ["--include-build", '../buildB', "--include-build", '../buildB']

        when:
        execute(buildA, ":resolve", args)

        then:
        executed ":buildB:jar"
    }

    @NotYetImplemented // This breaks because we are too aggressive in passing `StartParameter` to the participants
    def "does not pass settings-file or build-file arguments when building participant artifact"() {
        given:
        dependency 'org.test:buildB:1.0'

        buildA.buildFile.copyTo(buildA.file("build-copy.gradle"))
        buildA.settingsFile.copyTo(buildA.file("settings-copy.gradle"))

        when:
        execute(buildA, ":resolve", ["--build-file", "build-copy.gradle", "--settings-file", "settings-copy.gradle"])

        then:
        executed ":buildB:jar"
    }

    @NotYetImplemented // This breaks because we are too aggressive in passing `StartParameter` to the participants
    def "does not exclude tasks when building participant artifact"() {
        given:
        dependency 'org.test:buildB:1.0'

        when:
        execute(buildA, ":resolve", ["--exclude-task", "jar"])

        then:
        executed ":buildB:jar"
    }

    def dependency(String notation) {
        buildA.buildFile << """
            dependencies {
                compile '${notation}'
            }
"""
    }

}
