/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.resolve.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Tests for using artifact type and classifier notation for non-dependency methods.
 */
class ArtifactTypeAndClassifierNonDependencyNotationIntegrationTest extends AbstractIntegrationSpec {
    static def ALL_COMBINATIONS = [
        ["classifier", ":linux"],
        ["artifact type", "@jar"],
        ["classifier and artifact type", ":linux@jar"]
    ]

    def "using #notationName in constraints is deprecated"() {
        given:
        buildFile("""
        plugins {
            id 'java-library'
        }

        dependencies {
            constraints {
                implementation('foo:bar:1.0${notationSyntax}')
            }
        }

        for (def c : configurations.implementation.dependencyConstraints) {
            println "Constraint: " + c.group + ":" + c.name + ":" + c.version
        }
        """)
        executer.expectDocumentedDeprecationWarning(
            "Declaring an artifact type or classifier on a non-dependency. " +
                "This behavior has been deprecated. This will fail with an error in Gradle 10. " +
                "The artifact type and classifier can only be specified for external dependencies. " +
                "The value(s) you specified will be ignored. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#non_dependency_artifact_type_classifier"
        )

        when:
        succeeds("help")

        then:
        outputContains("Constraint: foo:bar:1.0")

        where:
        [notationName, notationSyntax] << ALL_COMBINATIONS
    }

    def "using #notationName in component metadata variant dependencies is deprecated"() {
        given:
        buildFile("""
        plugins {
            id 'java-library'
        }

        ${mavenCentralRepository()}

        dependencies {
            implementation 'com.google.guava:guava:28.1-jre'
            components {
                withModule('com.google.guava:guava') {
                    allVariants {
                        withDependencies {
                           add("foo:bar:1.0${notationSyntax}")
                        }
                    }
                }
            }
        }
        """)
        executer.expectDocumentedDeprecationWarning(
            "Declaring an artifact type or classifier on a non-dependency. " +
                "This behavior has been deprecated. This will fail with an error in Gradle 10. " +
                "The artifact type and classifier can only be specified for external dependencies. " +
                "The value(s) you specified will be ignored. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#non_dependency_artifact_type_classifier"
        )

        when:
        succeeds("dependencies")

        then:
        // We just need to see that the dependency was attempted to be resolved
        outputContains("foo:bar:1.0 FAILED")

        where:
        [notationName, notationSyntax] << ALL_COMBINATIONS
    }
}
