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
 * A strict version applies to every node that all paths from the root reach through its declarer, including
 * nodes in a cycle. Their requests for the module are ignored, which shows as the "By ancestor" selection reason.
 */
class StrictVersionsInCyclesIntegrationTest extends AbstractIntegrationSpec {
    def resolve = new ResolveTestFixture(testDirectory)

    def "a request in a cycle below a strict constraint of the root is ignored"() {
        given:
        def m0v1 = module("m0", "1")
        def m2v2 = module("m2", "2")
        def m3v1 = module("m3", "1")
        def m4v3 = module("m4", "3")
        def m5v2 = module("m5", "2")
        def m5v3 = module("m5", "3")
        // The cycle m4:3 -> m3:1 -> m0:1 -> m4:3 is only reached through m4:3, which the root strictly constrains.
        // m2:2 also reaches m0:1 for a moment, and leaves the graph once m5 is upgraded to 3.
        m0v1.dependsOn(m4v3).publish()
        m2v2.dependsOn(m5v3).dependsOn(m0v1).publish()
        m3v1.dependsOn(m0v1).publish()
        m4v3.dependsOn(m3v1).publish()
        m5v2.dependsOn(m2v2).publish()
        m5v3.publish()

        settingsFile << """
            rootProject.name = "test"
        """
        buildFile << """
            ${mavenTestRepository()}
            configurations {
                // Sorted before 'conf', so the strict constraint is listed before the dependencies
                aStrict
                conf {
                    extendsFrom(aStrict)
                }
            }
            dependencies {
                constraints {
                    aStrict("org:m4") {
                        version {
                            strictly("3")
                        }
                    }
                }
                conf("org:m4:3")
                conf("org:m5:2")
            }
            ${resolve.configureProject("conf")}
        """

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                constraint("org:m4:{strictly 3}", "org:m4:3")
                module("org:m4:3") {
                    byConstraint()
                    byAncestor()
                    module("org:m3:1") {
                        module("org:m0:1") {
                            module("org:m4:3")
                        }
                    }
                }
                edge("org:m5:2", "org:m5:3") {
                    byConflictResolution("between versions 3 and 2")
                }
            }
        }
    }

    private def module(String name, String version) {
        return mavenRepo.module("org", name, version).withModuleMetadata()
    }
}
