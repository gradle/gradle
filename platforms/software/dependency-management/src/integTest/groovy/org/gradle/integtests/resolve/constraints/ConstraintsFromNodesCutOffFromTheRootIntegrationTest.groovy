/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.resolve.constraints

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout

/**
 * A node cut off from the root, but kept in the graph by a cycle, may still be the provider of a pending
 * constraint. A module waiting for that constraint must still be selected.
 */
class ConstraintsFromNodesCutOffFromTheRootIntegrationTest extends AbstractIntegrationSpec {
    def resolve = new ResolveTestFixture(testDirectory)

    def "selects a module whose pending constraint comes from a node cut off from the root"() {
        given:
        def m4v1 = module("M4", "1").publish()
        def m4v3 = module("M4", "3").publish()
        def m0v2 = module("M0", "2").dependsOn(m4v1).publish()
        // M1:3 upgrades M0 to 2, and has a pending constraint on M4 until M0:2 depends on it
        def m1v3 = module("M1", "3").dependencyConstraint(m0v2).dependencyConstraint(m4v3).publish()
        // M3:1 <-> M2:2 is a cycle, only reached through M0:1, which the upgrade of M0 evicts
        def m3v1 = module("M3", "1")
        def m2v2 = module("M2", "2").dependsOn(m3v1).publish()
        m3v1.dependsOn(m2v2).dependsOn(m1v3).publish()
        module("M0", "1").dependsOn(m3v1).publish()

        settingsFile << """
            rootProject.name = "test"
            // Otherwise the nodes cut off from the root also deliver their artifacts
            enableFeaturePreview("ENHANCED_GRAPH_ORDERING")
        """
        buildFile << """
            ${mavenTestRepository()}
            configurations {
                conf
            }
            dependencies {
                conf("org:M0:1")
            }
            ${resolve.configureProject("conf")}
        """

        when:
        succeeds(":checkDeps")

        then:
        // M0:2 is only selected because of the constraint of M1:3, which is cut off from the root. The cycle keeps
        // M1:3 in the graph, but its constraint on M4 is never applied.
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:M0:1", "org:M0:2") {
                    byConstraint()
                    byConflictResolution("between versions 2 and 1")
                    module("org:M4:1")
                }
            }
        }
    }

    @IntegrationTestTimeout(60)
    def "resolution ends when a node with a deferred edge is cut off from the root"() {
        given:
        def m4v3 = module("M4", "3").publish()
        module("M1", "2").publish()
        module("M1", "3").publish()
        module("M3", "2").publish()
        // org:M5 is substituted by project :m5 below
        def m5v3 = module("M5", "3").publish()
        def m3v3 = module("M3", "3").dependsOn(m5v3).publish()
        def m4v2 = module("M4", "2").dependsOn(m3v3, strictly: "3", endorseStrictVersions: true).publish()
        module("M0", "1").dependsOn(m4v2).publish()

        settingsFile << """
            rootProject.name = "test"
            include("m5")
            // Otherwise the nodes cut off from the root also deliver their artifacts
            enableFeaturePreview("ENHANCED_GRAPH_ORDERING")
        """
        // M5 upgrades M4 to 3, which evicts M4:2, the only path to M3:3 and M5. Its constraints come first, as the
        // dependencies of a project are ordered by the name of their configuration.
        file("m5/build.gradle") << """
            ${mavenTestRepository()}
            group = "org"
            version = "3"
            def constraintsScope = configurations.dependencyScope("aConstraints")
            def dependenciesScope = configurations.dependencyScope("bDependencies")
            configurations.consumable("runtime") {
                extendsFrom(constraintsScope.get(), dependenciesScope.get())
                attributes.attribute(Attribute.of("custom", String), "value")
            }
            dependencies {
                constraints {
                    aConstraints("org:M4:3")
                    aConstraints("org:M1:3")
                }
                bDependencies("org:M1:2")
                bDependencies("org:M3:2")
            }
        """
        buildFile << """
            ${mavenTestRepository()}
            configurations {
                conf {
                    attributes.attribute(Attribute.of("custom", String), "value")
                    resolutionStrategy.dependencySubstitution {
                        substitute(module("org:M5")).using(project(":m5"))
                    }
                }
            }
            dependencies {
                conf("org:M0:1")
            }
            ${resolve.configureProject("conf")}
        """

        when:
        succeeds(":checkDeps")

        then:
        // M4:3 is only selected because of the constraint of M5, which is cut off from the root
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:M0:1") {
                    edge("org:M4:2", "org:M4:3") {
                        byConstraint()
                        byConflictResolution("between versions 3 and 2")
                    }
                }
            }
        }
    }

    private def module(String name, String version) {
        return mavenRepo.module("org", name, version).withModuleMetadata()
    }
}
