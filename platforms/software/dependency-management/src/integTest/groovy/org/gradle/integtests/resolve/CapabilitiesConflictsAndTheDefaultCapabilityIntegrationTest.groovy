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

package org.gradle.integtests.resolve;

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionResult
import spock.lang.Ignore;

/**
 * Demonstrates how default capabilities are sometimes handled differently from explicit capabilities.
 */
class CapabilitiesConflictsAndTheDefaultCapabilityIntegrationTest extends AbstractIntegrationSpec {
    def "can NOT dep (by attributes) 2 variants from same project, sharing the same (default) capability"() {
        buildFile << """
            dependencies {
                myDeps(project(":producer-with-default-capability")) {
                    attributes {
                        attribute(color, "red")
                    }
                }
                myDeps(project(":producer-with-default-capability")) {
                    attributes {
                        attribute(color, "blue")
                    }
                }
            }
        """

        when:
        fails("doResolve")

        then:
        assertCapabilityConflictReported("example:producer-with-default-capability:1.0")
    }

    def "can NOT dep (by name) 2 variants from same project, sharing the same (default) capability"() {
        buildFile << """
            dependencies {
                myDeps project(path: ":producer-with-default-capability", configuration: "confRed")
                myDeps project(path: ":producer-with-default-capability", configuration: "confBlue")
            }
        """

        when:
        succeeds(":producer-with-default-capability:outgoingVariants")

        then:
        assertVariantHasCapability("confRed", "example:producer-with-default-capability:1.0")
        assertVariantHasCapability("confBlue", "example:producer-with-default-capability:1.0")

        when:
        fails("doResolve")

        then:
        assertCapabilityConflictReported("example:producer-with-default-capability:1.0")
    }

    def "can NOT dep (by name) 2 variants from same project, sharing the same (default) capability, when that capability uses an empty group"() {
        buildFile << """
            dependencies {
                myDeps project(path: ":producer-with-default-capability-with-empty-group", configuration: "confRed")
                myDeps project(path: ":producer-with-default-capability-with-empty-group", configuration: "confBlue")
            }
        """

        when:
        succeeds(":producer-with-default-capability-with-empty-group:outgoingVariants")

        then:
        assertVariantHasCapability("confRed", ":producer-with-default-capability-with-empty-group:1.0")
        assertVariantHasCapability("confBlue", ":producer-with-default-capability-with-empty-group:1.0")

        when:
        fails("doResolve")

        then:
        assertCapabilityConflictReported(":producer-with-default-capability-with-empty-group:1.0")
    }

    def "can NOT dep (by name + by attributes) 2 variants from same project, sharing the same (default) capability"() {
        buildFile << """
            dependencies {
                myDeps project(path: ":producer-with-default-capability", configuration: "confRed")
                myDeps(project(":producer-with-default-capability")) {
                    attributes {
                        attribute(color, "blue")
                    }
                }
            }
        """

        when:
        succeeds(":producer-with-default-capability:outgoingVariants")

        then:
        assertVariantHasCapability("confRed", "example:producer-with-default-capability:1.0")
        assertVariantHasCapability("confBlue", "example:producer-with-default-capability:1.0")

        when:
        fails("doResolve")

        then:
        assertCapabilityConflictReported("example:producer-with-default-capability:1.0")
    }

    def "can NOT dep (by attributes) 2 variants from same project, sharing the same (default) capability"() {
        buildFile << """
            dependencies {
                myDeps(project(":producer-with-default-capability")) {
                    attributes {
                        attribute(color, "red")
                    }
                }
                myDeps(project(":producer-with-default-capability")) {
                    attributes {
                        attribute(color, "blue")
                    }
                }
            }
        """

        when:
        succeeds(":producer-with-default-capability:outgoingVariants")

        then:
        assertVariantHasCapability("confRed", "example:producer-with-default-capability:1.0")
        assertVariantHasCapability("confBlue", "example:producer-with-default-capability:1.0")

        when:
        fails("doResolve")

        then:
        assertCapabilityConflictReported("example:producer-with-default-capability:1.0")
    }

    def "can NOT dep (by name) 2 variants from same project, sharing the same non-default explicit capability"() {
        buildFile << """
            dependencies {
                myDeps project(path: ":producer-with-explicit-capability-1", configuration: "confRed")
                myDeps project(path: ":producer-with-explicit-capability-1", configuration: "confBlue")
            }
        """

        when:
        succeeds(":producer-with-explicit-capability-1:outgoingVariants")

        then:
        assertVariantHasCapability("confRed", "example:my-custom-producer:1.0")
        assertVariantHasCapability("confBlue", "example:my-custom-producer:1.0")

        when:
        fails("doResolve")

        then:
        assertCapabilityConflictReported("example:my-custom-producer:1.0")
    }

    def "can NOT dep (by attributes) 2 variants from same project, sharing the same explicit non-default capability"() {
        buildFile << """
            dependencies {
                myDeps(project(":producer-with-explicit-capability-1")) {
                    attributes {
                        attribute(color, "red")
                    }
                    capabilities {
                        requireCapability("example:my-custom-producer:1.0") // must explicitly request the capability, as the request for implicit capability of this project will fail
                    }
                }
                myDeps(project(":producer-with-explicit-capability-1")) {
                    attributes {
                        attribute(color, "blue")
                    }
                    capabilities {
                        requireCapability("example:my-custom-producer:1.0") // must explicitly request the capability, as the request for implicit capability of this project will fail
                    }
                }
            }
        """

        when:
        fails("doResolve")

        then:
        assertCapabilityConflictReported("example:my-custom-producer:1.0")
    }

    def "can NOT dep (by name) 2 variants from different projects, sharing the same explicit non-default capability"() {
        buildFile << """
            dependencies {
                myDeps project(path: ":producer-with-explicit-capability-1", configuration: "confRed")
                myDeps project(path: ":producer-with-explicit-capability-2", configuration: "confBlue")
            }
        """

        when:
        fails("doResolve")

        then:
        assertCapabilityConflictReported("example:my-custom-producer:1.0")
    }

    def "can NOT dep (by attributes) 2 variants from different projects, sharing the same explicit non-default capability"() {
        buildFile << """
            dependencies {
                myDeps(project(":producer-with-explicit-capability-1")) {
                    attributes {
                        attribute(color, "red")
                    }
                    capabilities {
                        requireCapability("example:my-custom-producer:1.0") // must explicitly request the capability, as the request for implicit capability of this project will fail
                    }
                }
                myDeps(project(":producer-with-explicit-capability-2")) {
                    attributes {
                        attribute(color, "blue")
                    }
                    capabilities {
                        requireCapability("example:my-custom-producer:1.0") // must explicitly request the capability, as the request for implicit capability of this project will fail
                    }
                }
            }
        """

        when:
        fails("doResolve")

        then:
        assertCapabilityConflictReported("example:my-custom-producer:1.0")
    }

    def "can NOT dep (by name) 2 variants from different projects, sharing the same capability, which happens to be the default capability on only one of them"() {
        buildFile << """
            dependencies {
                myDeps project(path: ":producer-with-default-capability", configuration: "confRed")
                myDeps project(path: ":producer-with-explicit-capability-equal-to-default", configuration: "confBlue")
            }
        """

        when:
        fails("doResolve")

        then:
        assertCapabilityConflictReported("example:producer-with-default-capability:1.0")
    }

    def "can NOT dep (by name + by attributes) 2 variants from different projects, sharing the same capability, which happens to be the default capability on only one of them"() {
        buildFile << """
            dependencies {
                myDeps(project(path: ":producer-with-default-capability", configuration: "confRed"))
                myDeps(project(":producer-with-explicit-capability-equal-to-default")) {
                    attributes {
                        attribute(color, "blue")
                    }
                    capabilities {
                        requireCapability("example:producer-with-default-capability:1.0") // must explicitly request the capability, as the request for implicit capability of this project will fail
                    }
                }
            }
        """

        when:
        fails("doResolve")

        then:
        assertCapabilityConflictReported("example:producer-with-default-capability:1.0")
    }

    def "can NOT dep (by attributes) 2 variants from different projects, sharing the same explicit non-default capability, which happens to be the default capability on only one of them"() {
        buildFile << """
            dependencies {
                myDeps(project(":producer-with-default-capability")) {
                    attributes {
                        attribute(color, "red")
                    }
                }
                myDeps(project(":producer-with-explicit-capability-equal-to-default")) {
                    attributes {
                        attribute(color, "blue")
                    }
                    capabilities {
                        requireCapability("example:producer-with-default-capability:1.0")
                    }
                }
            }
        """

        when:
        fails("doResolve")

        then:
        assertCapabilityConflictReported("example:producer-with-default-capability:1.0")
    }

    @Ignore("Will fix this issue separately")
    def "when dep (by attributes) project with explicit capability different from project name without requiring a capability - THIS ERROR MESSAGE MAKES NO SENSE"() {
        buildFile << """
            dependencies {
                myDeps(project(":producer-with-explicit-capability-1")) {
                    attributes {
                        attribute(color, "red")
                    }
                }
            }
        """

        when:
        succeeds(":producer-with-explicit-capability-1:outgoingVariants")

        then:
        assertVariantHasCapability("confRed", "example:my-custom-producer:1.0")

        when:
        fails("doResolve")

        then:
        assert false, 'This error message makes no sense - no capability was explicitly requested, the output SHOULD say something like: "requesting the implicit capability: example:producer-with-explicit-capability-1:1.0" and SUGGEST explicitly requiring a capability on the consumer'
    }

    def setup() {
        settingsFile << """
            rootProject.name = "example"

            include "producer-with-default-capability"
            include "producer-with-default-capability-with-empty-group"
            include "producer-with-explicit-capability-1"
            include "producer-with-explicit-capability-2"
            include "producer-with-explicit-capability-equal-to-default"
        """

        buildFile << """
            def color = Attribute.of("color", String)

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
        setupProducerWithDefaultCapabilityWithEmptyGroup()
        setupProducerWithExplicitCapability1()
        setupProducerWithExplicitCapability2()
        setupProducerWithExplicitCapabilityEqualToDefault()
    }

    private void setupProducerWithDefaultCapability() {
        file("producer-with-default-capability/red.jar").text = "red variant from producer-with-default-capability"
        file("producer-with-default-capability/blue.jar").text = "blue variant from producer-with-default-capability"

        file("producer-with-default-capability/build.gradle") << """
            version = "1.0"

            def color = Attribute.of("color", String)

            configurations {
                consumable("confRed") {
                    attributes {
                        attribute(color, "red")
                    }
                }
                consumable("confBlue") {
                    attributes {
                        attribute(color, "blue")
                    }
                }
            }

            artifacts {
                confRed file("red.jar")
                confBlue file("blue.jar")
            }
        """
    }

    private void setupProducerWithDefaultCapabilityWithEmptyGroup() {
        file("producer-with-default-capability-with-empty-group/red.jar").text = "red variant from producer-with-default-capability-with-empty-group"
        file("producer-with-default-capability-with-empty-group/blue.jar").text = "blue variant from producer-with-default-capability-with-empty-group"

        file("producer-with-default-capability-with-empty-group/build.gradle") << """
            version = "1.0"
            group = ""

            def color = Attribute.of("color", String)

            configurations {
                consumable("confRed") {
                    attributes {
                        attribute(color, "red")
                    }
                }
                consumable("confBlue") {
                    attributes {
                        attribute(color, "blue")
                    }
                }
            }

            artifacts {
                confRed file("red.jar")
                confBlue file("blue.jar")
            }
        """
    }

    private void setupProducerWithExplicitCapability1() {
        file("producer-with-explicit-capability-1/red.jar").text = "red variant from producer-with-explicit-capability-1"
        file("producer-with-explicit-capability-1/blue.jar").text = "blue variant from producer-with-explicit-capability-1"

        file("producer-with-explicit-capability-1/build.gradle") << """
            def color = Attribute.of("color", String)

            configurations {
                consumable("confRed")  {
                    attributes {
                        attribute(color, "red")
                    }
                    outgoing {
                        capability("example:my-custom-producer:1.0")
                    }
                }
                consumable("confBlue") {
                    attributes {
                        attribute(color, "blue")
                    }
                    outgoing {
                        capability("example:my-custom-producer:1.0")
                    }
                }
            }

            artifacts {
                confRed file("red.jar")
                confBlue file("blue.jar")
            }
        """
    }

    private void setupProducerWithExplicitCapability2() {
        file("producer-with-explicit-capability-2/red.jar").text = "red variant from producer-with-explicit-capability-2"
        file("producer-with-explicit-capability-2/blue.jar").text = "blue variant from producer-with-explicit-capability-2"

        file("producer-with-explicit-capability-2/build.gradle") << """
            def color = Attribute.of("color", String)

            configurations {
                consumable("confRed") {
                    attributes {
                        attribute(color, "red")
                    }
                    outgoing {
                        capability("example:my-custom-producer:1.0")
                    }
                }
                consumable("confBlue") {
                    attributes {
                        attribute(color, "blue")
                    }
                    outgoing {
                        capability("example:my-custom-producer:1.0")
                    }
                }
            }

            artifacts {
                confRed file("red.jar")
                confBlue file("blue.jar")
            }
        """
    }

    private void setupProducerWithExplicitCapabilityEqualToDefault() {
        file("producer-with-explicit-capability-equal-to-default/red.jar").text = "red variant from producer-with-explicit-capability-equal-to-default"
        file("producer-with-explicit-capability-equal-to-default/blue.jar").text = "blue variant from producer-with-explicit-capability-equal-to-default"

        file("producer-with-explicit-capability-equal-to-default/build.gradle") << """
            def color = Attribute.of("color", String)

            configurations {
                consumable("confRed") {
                    attributes {
                        attribute(color, "red")
                    }
                    outgoing {
                        capability("example:producer-with-default-capability:1.0")
                    }
                }
                consumable("confBlue") {
                    attributes {
                        attribute(color, "blue")
                    }
                    outgoing {
                        capability("example:producer-with-default-capability:1.0")
                    }
                }
            }

            artifacts {
                confRed file("red.jar")
                confBlue file("blue.jar")
            }
        """
    }

    private void assertCapabilityConflictReported(String capability) {
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
