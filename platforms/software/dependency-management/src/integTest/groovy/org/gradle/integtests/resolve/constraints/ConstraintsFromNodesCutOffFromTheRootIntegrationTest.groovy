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

    private def module(String name, String version) {
        return mavenRepo.module("org", name, version).withModuleMetadata()
    }
}
