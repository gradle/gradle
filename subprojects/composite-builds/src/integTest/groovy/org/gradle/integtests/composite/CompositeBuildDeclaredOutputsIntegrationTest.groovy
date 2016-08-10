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
import org.gradle.test.fixtures.maven.MavenFileRepository
/**
 * Tests for resolving dependency graph with substitution within a composite build.
 */
class CompositeBuildDeclaredOutputsIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildA
    BuildTestFile buildB
    MavenFileRepository mavenRepo
    ResolveTestFixture resolve
    def buildArgs = []

    def setup() {
        mavenRepo = new MavenFileRepository(file("maven-repo"))
        mavenRepo.module("org.test", "buildB", "1.0").publish()
        mavenRepo.module("org.test", "b2", "1.0").publish()

        buildA = multiProjectBuild("buildA", ['a1', 'a2']) {
            buildFile << """
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                allprojects {
                    apply plugin: 'java'
                    configurations { compile }
                }
"""
        }
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
    }

    def "will substitute only declared outputs when defined for included build"() {
        given:
        buildA.buildFile << """
            dependencies {
                compile "org.test:b1:1.0"
                compile "org.test:b2:1.0"
            }
"""
        buildA.settingsFile << """
            includeBuild('${buildB.toURI()}') {
                dependencySubstitution {
                    substitute module("org.test:b1:1.0") with project("buildB::b1")
                }
            }
"""

        expect:
        resolvedGraph {
            edge("org.test:b1:1.0", "project buildB::b1", "org.test:b1:2.0") {
                compositeSubstitute()
            }
            module("org.test:b2:1.0")
        }
    }

    def "can substitute arbitrary coordinates using declared outputs for included build"() {
        given:
        buildA.buildFile << """
            dependencies {
                compile "X:Y:4.0"
            }
"""
        buildA.settingsFile << """
            includeBuild('${buildB.toURI()}') {
                dependencySubstitution {
                    substitute module("X:Y") with project("buildB::b1")
                }
            }
"""

        expect:
        resolvedGraph {
            edge("X:Y:4.0", "project buildB::b1", "org.test:b1:2.0") {
                compositeSubstitute()
            }
        }
    }

    void resolvedGraph(@DelegatesTo(ResolveTestFixture.NodeBuilder) Closure closure) {
        resolve.prepare()
        execute(buildA, ":checkDeps", buildArgs)
        resolve.expectGraph {
            root(":", "org.test:buildA:1.0", closure)
        }
    }
}
