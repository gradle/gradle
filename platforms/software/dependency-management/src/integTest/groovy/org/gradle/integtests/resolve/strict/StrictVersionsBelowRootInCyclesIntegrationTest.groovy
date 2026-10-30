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

package org.gradle.integtests.resolve.strict

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

/**
 * A strict version declared below the root applies to a cycle once every path to the cycle goes through its declarer.
 */
class StrictVersionsBelowRootInCyclesIntegrationTest extends AbstractIntegrationSpec {
    def resolve = new ResolveTestFixture(testDirectory)

    def "a strict version of a transitive dependency applies to a cycle once the other path to the cycle is evicted"() {
        given:
        def m1 = module("M", "1").publish()
        def m2 = module("M", "2").publish()
        def b2 = module("B", "2").publish()
        def c0 = module("C0", "1")
        def c1 = module("C1", "1")
        // C0 <-> C1 is a cycle, and C0 asks for M:2
        c0.dependsOn(m2).dependsOn(c1).publish()
        c1.dependsOn(c0).publish()
        // D leads into the cycle and strictly requires M:1
        module("D", "1").dependsOn(c0).dependsOn([strictly: "1"], m1).publish()
        // B:1 is a second path into the cycle, which bypasses D
        module("B", "1").dependsOn(c0).publish()
        // B is upgraded to 2, which has no edges, at the end of a path long enough for B:1 to leave the graph
        // after the cycle is closed
        def r2 = module("R2", "1").dependsOn(b2).publish()
        def r1 = module("R1", "1").dependsOn(r2).publish()
        module("R0", "1").dependsOn(r1).publish()

        settingsFile << """
            rootProject.name = "test"
        """
        buildFile << """
            ${mavenTestRepository()}
            configurations {
                conf
            }
            dependencies {
                conf("org:D:1")
                conf("org:R0:1")
                conf("org:B:1")
            }
            ${resolve.configureProject("conf")}
        """

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:D:1") {
                    module("org:C0:1") {
                        edge("org:M:2", "org:M:1") {
                            byAncestor()
                        }
                        module("org:C1:1") {
                            module("org:C0:1")
                        }
                    }
                    edge("org:M:{strictly 1}", "org:M:1")
                }
                module("org:R0:1") {
                    module("org:R1:1") {
                        module("org:R2:1") {
                            module("org:B:2")
                        }
                    }
                }
                edge("org:B:1", "org:B:2") {
                    byConflictResolution("between versions 2 and 1")
                }
            }
        }
    }

    private def module(String name, String version) {
        return mavenRepo.module("org", name, version).withModuleMetadata()
    }
}
