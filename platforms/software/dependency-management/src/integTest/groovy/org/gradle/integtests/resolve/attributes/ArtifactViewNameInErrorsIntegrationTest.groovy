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

package org.gradle.integtests.resolve.attributes

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Tests that resolution failures of a named {@link org.gradle.api.artifacts.ArtifactView ArtifactView}
 * are reported against the view, while unnamed views keep reporting against their configuration.
 */
class ArtifactViewNameInErrorsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """
            rootProject.name = "consumer"
            include "producer"
        """

        file("producer/build.gradle") << """
            def flavor = Attribute.of("flavor", String)

            configurations {
                consumable("producerVanilla") {
                    attributes.attribute(flavor, "vanilla")
                    outgoing.artifact(file("vanilla.jar"))
                }
                consumable("producerChocolate") {
                    attributes.attribute(flavor, "chocolate")
                    outgoing.artifact(file("chocolate.jar"))
                }
            }
        """

        buildFile << """
            def flavor = Attribute.of("flavor", String)
            def texture = Attribute.of("texture", String)

            configurations {
                dependencyScope("deps")
                resolvable("consumerConf") {
                    extendsFrom(deps)
                    attributes.attribute(flavor, "vanilla")
                }
            }

            dependencies {
                deps(project(":producer"))
            }
        """
    }

    def "named artifact view resolves successfully"() {
        buildFile << """
            tasks.register("verify") {
                def viewFiles = configurations.consumerConf.incoming.artifactView {
                    displayName = "chocolateView"
                    attributes.attribute(flavor, "chocolate")
                    withVariantReselection()
                }.files

                doLast {
                    println("files: " + viewFiles*.name)
                }
            }
        """

        when:
        succeeds("verify")

        then:
        outputContains("files: [chocolate.jar]")
    }

    def "unnamed artifact view files failure is reported against the configuration"() {
        buildFile << """
            tasks.register("verify") {
                def viewFiles = ${ambiguousArtifactView(null)}.files

                doLast {
                    println(viewFiles*.name)
                }
            }
        """

        expect:
        fails("verify")
        failure.assertHasCause("Could not resolve all files for configuration ':consumerConf'.")
    }

    def "named artifact view files failure is reported against the view"() {
        buildFile << """
            tasks.register("verify") {
                def viewFiles = ${ambiguousArtifactView("custom")}.files

                doLast {
                    println(viewFiles*.name)
                }
            }
        """

        expect:
        fails("verify")
        failure.assertHasCause("Could not resolve all files for artifact view 'custom' for configuration ':consumerConf'.")
        !errorOutput.contains("Could not resolve all files for configuration ':consumerConf'.")
    }

    def "named artifact view artifacts failure is reported against the view"() {
        buildFile << """
            tasks.register("verify") {
                def viewArtifacts = ${ambiguousArtifactView("custom")}.artifacts

                doLast {
                    viewArtifacts.artifacts.each { println(it.file.name) }
                }
            }
        """

        expect:
        fails("verify")
        failure.assertHasCause("Could not resolve all artifacts for artifact view 'custom' for configuration ':consumerConf'.")
    }

    /**
     * Both producer variants are compatible with a request that only sets an attribute neither declares,
     * so selecting the view's artifacts fails as ambiguous.
     */
    private static String ambiguousArtifactView(String name) {
        return """configurations.consumerConf.incoming.artifactView {
                    ${name != null ? "displayName = '$name'" : ""}
                    attributes.attribute(texture, "smooth")
                    withVariantReselection()
                }"""
    }
}
