/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.integtests.experiments;

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionResult;

/**
 * Demonstrates how default capabilities are sometimes handled differently from explicit capabilities.
 */
class CapabilitiesConflictsAndTheDefaultCapability extends AbstractIntegrationSpec {
    def "can NOT dep by name two confs from same project, sharing the same (default) capability - THIS SHOULD FAIL"() {
        buildFile << """
            dependencies {
                myDeps project(path: ":producer-with-default-capability", configuration: "conf1")
                myDeps project(path: ":producer-with-default-capability", configuration: "conf2")
            }
        """

        when:
        succeeds("doResolve")

        then:
        assertResolved("file-a1.txt", "file-a2.txt")

        when:
        succeeds(":producer-with-default-capability:outgoingVariants")

        then:
        assertVariantHasCapability("conf1", "example:producer-with-default-capability:unspecified")
        assertVariantHasCapability("conf2", "example:producer-with-default-capability:unspecified")
    }

    def "can NOT dep by name two confs from same project, sharing the same capability"() {
        buildFile << """
            dependencies {
                myDeps project(path: ":producer-with-explicit-capability-1", configuration: "conf1")
                myDeps project(path: ":producer-with-explicit-capability-1", configuration: "conf2")
            }
        """

        when:
        fails("doResolve")

        then:
        assertCapabilityConflict("example:producer:1.0")
    }

    def "can NOT dep by name two confs from different projects, sharing the same capability"() {
        buildFile << """
            dependencies {
                myDeps project(path: ":producer-with-explicit-capability-1", configuration: "conf1")
                myDeps project(path: ":producer-with-explicit-capability-2", configuration: "conf1")
            }
        """

        when:
        fails("doResolve")

        then:
        assertCapabilityConflict("example:producer:1.0")
    }

    def "can NOT dep by name two confs from different projects, sharing the same capability, which happens to be the default capability on one of them"() {
        buildFile << """
            dependencies {
                myDeps project(path: ":producer-with-default-capability", configuration: "conf1")
                myDeps project(path: ":producer-with-explicit-capability-equal-to-default", configuration: "conf1")
            }
        """

        when:
        fails("doResolve")

        then:
        assertCapabilityConflict("example:producer-with-default-capability:unspecified")
    }

    def setup() {
        settingsFile << """
            rootProject.name = "example"

            include "producer-with-default-capability"
            include "producer-with-explicit-capability-1"
            include "producer-with-explicit-capability-2"
            include "producer-with-explicit-capability-equal-to-default"
        """

        buildFile << """
            configurations {
                dependencyScope("myDeps")
                resolvable("resolveMe") {
                    extendsFrom myDeps
                }
            }

            task doResolve(type: Sync) {
                from(configurations.resolveMe)
                into("build/resolved")
            }
        """

        setupProducerWithDefaultCapability()
        setupProducerWithExplicitCapability1()
        setupProducerWithExplicitCapability2()
        setupProducerWithExplicitCapabilityEqualToDefault()
    }

    private void setupProducerWithDefaultCapability() {
        file("producer-with-default-capability/file-a1.txt").text = "file 1 from producer-with-default-capability"
        file("producer-with-default-capability/file-a2.txt").text = "file 2 from producer-with-default-capability"

        file("producer-with-default-capability/build.gradle") << """
            def color = Attribute.of("color", String)

            configurations {
                consumable("conf1") {
                    attributes {
                        attribute(color, "red")
                    }
                }
                consumable("conf2") {
                    attributes {
                        attribute(color, "blue")
                    }
                }
            }

            artifacts {
                conf1 file("file-a1.txt")
                conf2 file("file-a2.txt")
            }
        """
    }

    private void setupProducerWithExplicitCapability1() {
        file("producer-with-explicit-capability/file-b1.txt").text = "file 1 from producer-with-explicit-capability-1"
        file("producer-with-explicit-capability/file-b2.txt").text = "file 2 from producer-with-explicit-capability-1"

        file("producer-with-explicit-capability-1/build.gradle") << """
            def color = Attribute.of("color", String)

            configurations {
                consumable("conf1")  {
                    attributes {
                        attribute(color, "red")
                    }
                    outgoing {
                        capability("example:producer:1.0")
                    }
                }
                consumable("conf2") {
                    attributes {
                        attribute(color, "blue")
                    }
                    outgoing {
                        capability("example:producer:1.0")
                    }
                }
            }

            artifacts {
                conf1 file("file-b1.txt")
                conf2 file("file-b2.txt")
            }
        """
    }

    private void setupProducerWithExplicitCapability2() {
        file("producer-with-explicit-capability/file-c1.txt").text = "file 1 from producer-with-explicit-capability-2"
        file("producer-with-explicit-capability/file-c2.txt").text = "file 2 from producer-with-explicit-capability-2"

        file("producer-with-explicit-capability-2/build.gradle") << """
            def color = Attribute.of("color", String)

            configurations {
                consumable("conf1") {
                    attributes {
                        attribute(color, "red")
                    }
                    outgoing {
                        capability("example:producer:1.0")
                    }
                }
                consumable("conf2") {
                    attributes {
                        attribute(color, "blue")
                    }
                    outgoing {
                        capability("example:producer:1.0")
                    }
                }
            }

            artifacts {
                conf1 file("file-c1.txt")
                conf2 file("file-c2.txt")
            }
        """
    }

    private void setupProducerWithExplicitCapabilityEqualToDefault() {
        file("producer-with-explicit-capability-equal-to-default/file-d1.txt").text = "file 1 from producer-with-explicit-capability-equal-to-default"
        file("producer-with-explicit-capability-equal-to-default/file-d2.txt").text = "file 2 from producer-with-explicit-capability-equal-to-default"

        file("producer-with-explicit-capability-equal-to-default/build.gradle") << """
            def color = Attribute.of("color", String)

            configurations {
                consumable("conf1") {
                    attributes {
                        attribute(color, "red")
                    }
                    outgoing {
                        capability("example:producer-with-default-capability:unspecified (default capability)")
                    }
                }
                consumable("conf2") {
                    attributes {
                        attribute(color, "blue")
                    }
                    outgoing {
                        capability("example:producer-with-default-capability:unspecified (default capability)")
                    }
                }
            }

            artifacts {
                conf1 file("file-d1.txt")
                conf2 file("file-d2.txt")
            }
        """
    }

    private void assertCapabilityConflict(String capability) {
        failureCauseContains("Cannot select module with conflict on capability '$capability'")
    }

    private void assertResolved(String... fileNames) {
        fileNames.each { fileName ->
            file("build/resolved/$fileName").assertExists()
        }
    }

    private ExecutionResult assertVariantHasCapability(String variantName, String capability) {
        result.assertOutputContains("""
--------------------------------------------------
Variant $variantName
--------------------------------------------------

Capabilities
    - $capability""")
    }
}
