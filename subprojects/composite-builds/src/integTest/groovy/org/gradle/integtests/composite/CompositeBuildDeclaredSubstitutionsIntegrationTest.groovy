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
class CompositeBuildDeclaredSubstitutionsIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB
    BuildTestFile buildC
    ResolveTestFixture resolve
    def buildArgs = []

    def setup() {
        mavenRepo.module("org.test", "buildB", "1.0").publish()
        mavenRepo.module("org.test", "b2", "1.0").publish()

        resolve = new ResolveTestFixture(buildA.buildFile)

        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    version "2.0"

                    repositories {
                        maven { url "${mavenRepo.uri}" }
                    }
                }
"""
        }

        buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
"""
        }
    }

    def "will only make declared substitutions when defined for included build"() {
        given:
        dependency "org.test:buildB:1.0"
        dependency "org.test:b1:1.0"
        dependency "org.test:b2:1.0"

        includeBuild buildB, """
            substitute module("org.test:buildB") with project(":")
            substitute module("org.test:b1:1.0") with project(":b1")
"""

        expect:
        resolvedGraph {
            edge("org.test:buildB:1.0", "project :buildB:", "org.test:buildB:2.0") {
                compositeSubstitute()
            }
            edge("org.test:b1:1.0", "project :buildB:b1", "org.test:b1:2.0") {
                compositeSubstitute()
            }
            module("org.test:b2:1.0")
        }
    }

    def "can combine included builds with declared and discovered substitutions"() {
        given:
        dependency "org.test:b1:1.0"
        dependency "org.test:XXX:1.0"

        includeBuild buildB
        includeBuild buildC, """
            substitute module("org.test:XXX") with project(":")
"""

        expect:
        resolvedGraph {
            edge("org.test:b1:1.0", "project :buildB:b1", "org.test:b1:2.0") {
                compositeSubstitute()
            }
            edge("org.test:XXX:1.0", "project :buildC:", "org.test:buildC:1.0") {
                compositeSubstitute()
            }
        }
    }

    def "can substitute arbitrary coordinates for included build"() {
        given:
        dependency "org.test:buildX:1.0"

        when:
        includeBuild buildB, """
            substitute module("org.test:buildX") with project(":b1")
"""

        then:
        resolvedGraph {
            edge("org.test:buildX:1.0", "project :buildB:b1", "org.test:b1:2.0") {
                compositeSubstitute()
            }
        }
    }

    def "resolves project substitution for build based on rootProject name"() {
        given:
        def buildB2 = rootDir.file("hierarchy", "buildB");
        buildB2.file('settings.gradle') << """
            rootProject.name = 'buildB2'
"""
        buildB2.file('build.gradle') << """
            apply plugin: 'java'
            group = 'org.test'
            version = '1.0'
"""

        dependency "org.gradle:buildX:1.0"

        when:
        // The project path ':' is resolved using the rootProject.name of buildB2
        includeBuild buildB2, """
            substitute module("org.gradle:buildX") with project(":")
"""

        then:
        resolvedGraph {
            edge("org.gradle:buildX:1.0", "project :buildB2:", "org.test:buildB2:1.0") {
                compositeSubstitute()
            }
        }
    }

    def "substitutes external dependency with project dependency from same participant build"() {
        given:
        dependency "org.test:buildB:1.0"
        dependency buildB, "org.test:b2:1.0"

        when:
        includeBuild buildB, """
            substitute module("org.test:buildB") with project(":")
            substitute module("org.test:b2:1.0") with project(":b2")
"""

        then:
        resolvedGraph {
            edge("org.test:buildB:1.0", "project :buildB:", "org.test:buildB:2.0") {
                compositeSubstitute()
                edge("org.test:b2:1.0", "project :buildB:b2", "org.test:b2:2.0") {
                    compositeSubstitute()
                }

            }
        }
    }

    void resolvedGraph(@DelegatesTo(ResolveTestFixture.NodeBuilder) Closure closure) {
        def resolve = new ResolveTestFixture(buildA.buildFile)
        resolve.prepare()
        execute(buildA, ":checkDeps", buildArgs)
        resolve.expectGraph {
            root(":", "org.test:buildA:1.0", closure)
        }
    }
}
