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
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

/**
 * Tests for resolving dependency graph with substitution within a composite build.
 */
class CompositeBuildMinimalConfigurationIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB
    BuildTestFile buildC
    ResolveTestFixture resolve
    def buildArgs = []

    def setup() {
        resolve = new ResolveTestFixture(buildA.buildFile).expectDefaultConfiguration("runtimeElements")
        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java-library'
                    version "2.0"
                }
"""
        }

        buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java-library'
"""
        }
    }

    def "does not configure build with declared substitutions that is not required for dependency substitution"() {
        given:
        dependency "org.test:buildB:1.0"

        includeBuild buildB
        includeBuild buildC, """
            substitute module("org.gradle:buildX") using project(":") // Not used
"""

        when:
        buildC.buildFile << """
            throw new RuntimeException('Configuration fails')
"""


        then:
        resolvedGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:2.0") {
                compositeSubstitute()
            }
        }
    }

    def "build with discovered substitutions that is not required for dependency substitution is configured only once"() {
        given:
        dependency "org.test:buildB:1.0"

        includeBuild buildB
        includeBuild buildC

        when:
        buildC.buildFile << """
            println 'Configured buildC'
"""


        then:
        resolvedGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:2.0") {
                compositeSubstitute()
            }
        }

        and:
        output.count('Configured buildC') == 1
    }

    def "configures included build only once when #action"() {
        given:
        dependency "org.test:buildB:1.0"
        dependency "org.test:buildC:1.0"

        includeBuild buildB
        includeBuild buildC, """
            substitute module("org.test:buildC") using project(":")
        """

        when:
        buildB.buildFile << """
            println 'Configured buildB'
        """
        buildC.buildFile << """
            println 'Configured buildC'
        """

        and:
        if (!buildArtifacts) {
            resolve.withoutBuildingArtifacts()
        }

        then:
        resolvedGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:2.0") {
                compositeSubstitute()
            }
            edge("org.test:buildC:1.0", ":buildC", "org.test:buildC:1.0") {
                compositeSubstitute()
            }
        }

        and:
        if (buildArtifacts) {
            executed(":buildB:jar", ":buildC:jar")
        }
        output.count('Configured buildB') == 1
        output.count('Configured buildC') == 1

        where:
        action      | buildArtifacts
        "resolving" | false
        "building"  | true
    }

    def "when configuration fails included build with #name substitutions is configured only once "() {
        given:
        dependency "org.test:buildB:1.0"

        if (name == "discovered") {
            includeBuild buildB
        } else {
            includeBuild buildB, """
                substitute module("org.test:buildB:") using project(":")
    """
        }

        and:
        buildB.buildFile << """
            println 'Configured buildB'
            throw new RuntimeException('Configuration failed for buildB')
"""

        when:
        fails(buildA, ":jar")

        then:
        output.count('Configured buildB') == 1

        and:
        failure.assertHasFileName("Build file '${buildB.buildFile}'")
        failure.assertHasDescription("A problem occurred evaluating project ':buildB'.")
        failure.assertHasCause("Configuration failed for buildB")

        where:
        name << ["discovered", "declared"]
    }

    def "configures included build only once when building multiple artifacts"() {
        given:
        dependency "org.test:buildB:1.0"
        dependency "org.test:b1:1.0"

        includeBuild buildB

        when:
        buildB.buildFile << """
            println 'Configured buildB'
"""

        then:
        resolvedGraph {
            edge("org.test:buildB:1.0", ":buildB", "org.test:buildB:2.0") {
                compositeSubstitute()
            }
            edge("org.test:b1:1.0", ":buildB:b1", "org.test:b1:2.0") {
                compositeSubstitute()
            }
        }

        and:
        executed(":buildB:jar", ":buildB:b1:jar")
        output.count('Configured buildB') == 1
    }

    def "configures included build only once when building multiple artifacts for a dependency of a referenced task"() {
        given:
        includeBuild buildB
        includeBuild buildC

        dependency buildC, "org.test:buildB:1.0"
        dependency buildC, "org.test:b1:1.0"

        when:
        buildA.buildFile << """
task run {
    dependsOn gradle.includedBuild('buildC').task(':jar')
}
"""
        buildB.buildFile << """
            println 'Configured buildB'
"""

        then:
        execute(buildA, ":run", buildArgs)

        and:
        output.count('Configured buildB') == 1
    }

    void resolvedGraph(@DelegatesTo(ResolveTestFixture.NodeBuilder) Closure closure) {
        resolve.prepare()
        execute(buildA, ":checkDeps", buildArgs)
        resolve.expectGraph {
            root(":", "org.test:buildA:1.0", closure)
        }
    }
}
