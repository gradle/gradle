/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

/**
 * Tests the behavior of {@link org.gradle.api.artifacts.ConfigurationPublications#getVariants()}.
 */
class OutgoingVariantsMutationIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            def usage = Attribute.of('usage', String)


            configurations {
                dependencyScope("deps")
                resolvable("resolver") {
                    extendsFrom(deps)
                    attributes.attribute(usage, "primary")
                }
            }

            dependencies {
                deps(project)
            }
        """
    }

    def "cannot mutate outgoing variants after configuration is resolved"() {
        given:
        buildFile << """
            def elements = configurations.create("elements") {
                attributes.attribute(usage, "primary")
                outgoing {
                    variants {
                        classes {
                            attributes.attribute(usage, 'secondary')
                            artifact(file('classes'))
                        }
                    }
                }
            }

            elements.dependencies.addAllLater(provider {
                // Try to mutate attributes late.
                // We realize dependencies after we lock the configuration for mutation.
                elements.outgoing.variants.classes {
                    attributes.attribute(usage, 'tertiary')
                }
                []
            })

            task resolve {
                def files = configurations.resolver.incoming.files
                doLast {
                    println(files*.name)
                }
            }
        """

        when:
        fails("resolve")

        then:
        failure.assertHasCause "Cannot change attributes of configuration ':elements' variant classes after it has been locked for mutation"
    }

    def "cannot declare capabilities after configuration is observed"() {
        given:
        buildFile << """
            def elements = configurations.create("elements") {
                attributes.attribute(usage, "primary")
            }

            elements.dependencies.addAllLater(provider {
                // Try to add capabilities late.
                // We realize dependencies after we lock the configuration for mutation.
                elements.outgoing {
                    capability("foo")
                }
                []
            })

            task resolve {
                def files = configurations.resolver.incoming.files
                doLast {
                    println(files*.name)
                }
            }
        """

        when:
        fails("resolve")

        then:
        failure.assertHasCause("Cannot declare capability 'foo' on configuration ':elements' after the configuration was consumed as a variant.")
    }

    def "cannot add outgoing variants after configuration is observed"() {
        given:
        buildFile << """

            def elements = configurations.create("elements") {
                attributes.attribute(usage, "primary")
                outgoing.artifact(file("primary"))
            }

            if (${createVariantBefore}) {
                elements.outgoing.variants {
                    third {
                        attributes.attribute(usage, 'tertiary')
                        artifact file('third')
                    }
                }
            }

            elements.dependencies.addAllLater(provider {
                // Try to add a secondary variant late.
                // We realize dependencies after we lock the configuration for mutation.
                elements.outgoing.variants {
                    classes {
                        attributes.attribute(usage, 'secondary')
                        artifact file('classes')
                    }
                }
                []
            })

            task resolve {
                def files = configurations.resolver.incoming.artifactView {
                    attributes.attribute(usage, "secondary")
                }.files
                doLast {
                    println(files*.name)
                }
            }
        """

        when:
        fails("resolve")

        then:
        failure.assertHasCause("Cannot add secondary artifact set to configuration ':elements' after the configuration was consumed as a variant.")

        where:
        createVariantBefore << [true, false]
    }

    @Issue("https://github.com/gradle/gradle/issues/30367")
    def "secondary variants can be registered lazily"() {
        buildFile << """
            def type = Attribute.of("color", String)

            file("file1.txt").text = "file1"
            file("file2.txt").text = "file2"

            configurations {
                consumable("outgoing") {
                    attributes.attribute(type, "primary")
                    outgoing.artifact(file("file1.txt"))
                    outgoing.variants.register("my-secondary-variant") {
                        attributes.attribute(type, "secondary")
                        artifact(file("file2.txt"))
                    }
                }
            }

            tasks.register("resolve") {
                def files = configurations.resolver.incoming.artifactView {
                    attributes.attribute(type, "secondary")
                }.files
                inputs.files(files)
                doFirst {
                    assert files*.name == ["file2.txt"]
                }
            }
        """

        expect:
        succeeds("resolve")
    }
}
