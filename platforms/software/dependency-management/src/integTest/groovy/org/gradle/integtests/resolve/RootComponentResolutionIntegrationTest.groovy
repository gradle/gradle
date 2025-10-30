/*
 * Copyright 2023 the original author or authors.
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

/**
 * Tests interactions a Configuration's root component during resolution.
 * This includes verifying component identity in resolution results, and
 * ensuring the configuration can select other variants in the same component.
 */
class RootComponentResolutionIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << "rootProject.name = 'root'"
    }

    def "resolvable configuration does not contribute artifacts"() {
        mavenRepo.module("org", "foo").publish()
        buildFile << """
            configurations {
                conf {
                    outgoing {
                        artifact file("foo.txt")
                    }
                }
            }

            // To ensure we do not run into short-circuit resolver
            repositories { maven { url = "${mavenRepo.uri}" } }
            dependencies {
                conf "org:foo:1.0"
            }

            task resolve {
                def files = configurations.conf.incoming.files
                doLast {
                    assert files.files*.name == ["foo-1.0.jar"]
                }
            }
        """

        expect:
        succeeds("resolve")
    }

    def "resolvable configuration does not contribute artifacts with variant reselection"() {
        mavenRepo.module("org", "foo").artifact(classifier: "sources").publish()
        buildFile << """
            plugins {
                id("jvm-ecosystem")
            }
            configurations {
                conf {
                    outgoing {
                        artifact file("foo.txt")
                    }
                }
                secondary {
                    outgoing {
                        artifact file("bar.txt")
                    }
                    attributes {
                        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, DocsType.SOURCES))
                    }
                }
            }

            // To ensure we do not run into short-circuit resolver
            repositories { maven { url = "${mavenRepo.uri}" } }
            dependencies {
                conf "org:foo:1.0"
            }

            task resolve {
                def files = configurations.conf.incoming.artifactView {
                    withVariantReselection()
                    attributes {
                        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, DocsType.SOURCES))
                    }
                }.files
                doLast {
                    assert files*.name == ["foo-1.0-sources.jar"]
                }
            }
        """

        expect:
        succeeds("resolve")
    }

    def "configuration can resolve itself"() {
        buildFile << """
            configurations {
                conf {
                    outgoing {
                        artifact file("foo.txt")
                    }
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "foo"))
                    }
                }
            }

            dependencies {
                conf project
            }

            task resolve {
                def files = configurations.conf.incoming.files
                doLast {
                    assert files.files*.name == ["foo.txt"]
                }
            }
        """

        expect:
        succeeds("resolve")
    }

    def "resolvable configuration and consumable configuration from same project live in different component"() {
        buildFile << """
            configurations {
                conf {
                    canBeConsumed = false
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "foo"))
                    }
                }
                other {
                    outgoing {
                        artifact file("foo.txt")
                    }
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "foo"))
                    }
                }
            }

            dependencies {
                conf project
            }

            task resolve {
                def result = configurations.conf.incoming.resolutionResult
                def rootComponentProvider = result.rootComponent
                def rootVariantProvider = result.rootVariant

                doLast {
                    def rootComponent = rootComponentProvider.get()
                    def rootVariant = rootVariantProvider.get()

                    assert rootComponent.id instanceof RootComponentIdentifier
                    assert rootComponent.variants.size() == 1

                    def other = rootComponent.getDependenciesForVariant(rootVariant)
                    assert other.size() == 1

                    def otherDependency = other.first()
                    def otherComponent = otherDependency.selected

                    assert otherComponent.id instanceof ProjectComponentIdentifier
                    assert otherComponent.id != rootComponent.id
                }
            }
        """

        expect:
        succeeds("resolve")
    }
}
