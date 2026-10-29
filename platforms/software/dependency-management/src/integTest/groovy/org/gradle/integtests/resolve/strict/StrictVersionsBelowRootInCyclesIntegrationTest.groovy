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

/**
 * A strict version declared below the root applies to a cycle once every path to the cycle goes through its declarer.
 */
class StrictVersionsBelowRootInCyclesIntegrationTest extends AbstractIntegrationSpec {

    def "a strict version of a transitive dependency applies to a cycle once the other path to the cycle is evicted"() {
        given:
        def m1 = module("M", "1").publish()
        def m2 = module("M", "2").publish()
        def b2 = module("B", "2").publish()
        def c0 = module("C0", "1")
        def c1 = module("C1", "1")
        // C0 <-> C1 is a cycle; C0 asks for M:2
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
        """

        when:
        succeeds("dependencies", "--configuration", "conf")

        then:
        outputContains("org:B:1 -> 2")
        outputContains("org:M:2 -> 1")
        outputDoesNotContain("FAILED")
    }

    private def module(String name, String version) {
        return mavenRepo.module("org", name, version).withModuleMetadata()
    }
}
