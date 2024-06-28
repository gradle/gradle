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

package org.gradle.integtests.experiments

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

/**
 * This test is to verify that secondary variants with no artifacts exclude the main
 * variant from resolution.
 */
@Issue("https://github.com/gradle/gradle/issues/29630")
class SecondaryVariantsBreakConsumableConfs extends AbstractIntegrationSpec {
    def "no secondary variants works fine for resolution"() {
        given:
        setupBuild()

        expect:
        succeeds("resolve")
    }

    def "no secondary variants works fine for dependencyInsight"() {
        given:
        setupBuild()

        expect:
        succeeds("dependencyInsight", "--configuration", "resolvableConfiguration", "--dependency", ":")
    }

    def "secondary variant with no artifacts breaks resolution - THIS SHOULD SUCCEED"() {
        given:
        setupBuild()

        expect:
        fails("resolve", "-PregisterSecondaryVariant=true")

        and:
        failure.assertHasDescription("Could not determine the dependencies of task ':resolve'.")
        failure.assertHasCause("Could not resolve all dependencies for configuration ':resolvableConfiguration'.")
        failure.assertHasErrorOutput("""> Could not resolve all dependencies for configuration ':resolvableConfiguration'.
   > No variants of root project : match the consumer attributes:
       - Configuration ':consumableConfiguration' variant myVariant declares attribute 'myAttribute1' with value 'value1':
           - Incompatible because this component declares attribute 'myAttribute2' with value 'otherValue2' and the consumer needed attribute 'myAttribute2' with value 'value2'""")
    }

    // Does this not failing indicate the report is unreliable?
    def "secondary variant with no artifacts works fine for dependencyInsight - SHOULD THIS FAIL?"() {
        given:
        setupBuild()

        expect:
        succeeds("dependencyInsight", "--configuration", "resolvableConfiguration", "--dependency", ":")
    }

    def "secondary variant with an artifact works fine for resolution"() {
        given:
        setupBuild()

        expect:
        succeeds("resolve", "-PregisterSecondaryVariant=true", "-PregisterArtifacts=true")
    }

    def "secondary variant with an artifact works fine for dependencyInsight"() {
        given:
        setupBuild()

        expect:
        succeeds("dependencyInsight", "--configuration", "resolvableConfiguration", "--dependency", ":", "-PregisterSecondaryVariant=true", "-PregisterArtifacts=true")
    }

    private TestFile setupBuild() {
        buildKotlinFile << """
            val myAttribute1 = Attribute.of("myAttribute1", String::class.java)
            val myAttribute2 = Attribute.of("myAttribute2", String::class.java)

            configurations.consumable("consumableConfiguration1") {
                attributes {
                    attribute(myAttribute1, "someOtherValue1")
                    attribute(myAttribute2, "value2")
                }
            }

            val consumableConfiguration = configurations.consumable("consumableConfiguration") {
                attributes {
                    attribute(myAttribute1, "value1")
                    attribute(myAttribute2, "value2")
                }

                if (providers.gradleProperty("registerSecondaryVariant").orNull == "true") {
                    outgoing.variants.create("myVariant") {
                        attributes {
                            attribute(myAttribute1, "value1")
                            attribute(myAttribute2, "otherValue2")
                        }
                    }
                }
            }

            if (providers.gradleProperty("registerArtifacts").orNull == "true") {
                artifacts {
                    add(consumableConfiguration.name, project.file("build.gradle.kts"))
                }
            }

            val dependencyScope = configurations.dependencyScope("dependencyScope")
            val resolvableConfiguration = configurations.resolvable("resolvableConfiguration") {
                extendsFrom(dependencyScope.get())
                attributes {
                    attribute(myAttribute1, "value1")
                    attribute(myAttribute2, "value2")
                }
            }

            dependencies {
                attributesSchema.attribute(myAttribute1)
                attributesSchema.attribute(myAttribute2)
                dependencyScope(project(":"))
            }

            tasks.register("resolve") {
                inputs.files(resolvableConfiguration)
                doFirst {
                    println(resolvableConfiguration.get().files)
                }
            }
        """
    }
}
