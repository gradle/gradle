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
import spock.lang.Ignore

/**
 * Tests for resolving dependency artifacts with substitution within a composite build.
 */
class IncludedBuildValidationIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB

    def setup() {
        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                }
"""
        }
    }

    def "reports failure when included build directory does not exist"() {
        when:
        def buildDir = new File("does-not-exist")
        includedBuilds << buildDir

        then:
        fails(buildA, "help")

        and:
        failure.assertHasDescription("Included build '${buildDir.absolutePath}' does not exist.")
    }

    def "reports failure when included build directory is not a directory"() {
        when:
        def buildDir = file("not-a-directory").createFile()
        includedBuilds << buildDir

        then:
        fails(buildA, "help")

        and:
        failure.assertHasDescription("Included build '${buildDir.absolutePath}' is not a directory.")
    }

    @Ignore
    def "reports failure when included build directory is not the root directory of build"() {
        when:
        includedBuilds << buildB.file('b1')

        then:
        fails(buildA, "help")

        and:
        failure.assertHasDescription("Included build 'b1' must have a settings file.")
    }

    def "reports failure for duplicate included build name"() {
        given:
        def buildC = singleProjectBuild("buildC")
        includeBuild(buildB)
        includeBuildAs(buildC, 'buildB')

        when:
        fails(buildA, "help")

        then:
        failure.assertHasDescription("Included build $buildC has build path :buildB which is the same as included build $buildB")
    }

    def "reports failure for included build name that conflicts with subproject name"() {
        given:
        createDirs("buildA", "buildA/buildB")
        buildA.settingsFile << """
            include 'buildB'
"""
        includedBuilds << buildB

        when:
        fails(buildA, "help")

        then:
        failure.assertHasDescription("Included build in ${buildB} has name 'buildB' which is the same as a project of the main build.")
    }

    def "included build name can be the same as root project name"() {
        given:
        buildB.settingsFile.text = "rootProject.name = 'buildA'"

        when:
        includedBuilds << buildB

        then:
        execute(buildA, "help")
    }

    def "allows to rename included build that conflicts with subproject name"() {
        given:
        createDirs("buildA", "buildA/buildB")
        buildA.settingsFile << """
            include 'buildB'
        """

        when:
        includeBuildAs(buildB, "buildB-build")

        then:
        execute(buildA, "help")
    }

}
