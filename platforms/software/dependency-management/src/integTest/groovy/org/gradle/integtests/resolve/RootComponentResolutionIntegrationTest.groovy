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
            repositories { maven { url "${mavenRepo.uri}" } }
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
            repositories { maven { url "${mavenRepo.uri}" } }
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

        executer.expectDocumentedDeprecationWarning("While resolving configuration 'conf', it was also selected as a variant. Configurations should not act as both a resolution root and a variant simultaneously. Depending on the resolved configuration in this manner has been deprecated. This will fail with an error in Gradle 9.0. Be sure to mark configurations meant for resolution as canBeConsumed=false or use the 'resolvable(String)' configuration factory method to create them. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#depending_on_root_configuration")

        expect:
        succeeds("resolve")
    }

    def "configuration can resolve itself and reselect artifacts"() {
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
                other {
                    outgoing {
                        artifact file("bar.txt")
                    }
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "bar"))
                    }
                }
            }

            dependencies {
                conf project
            }

            task resolve {
                def files = configurations.conf.incoming.artifactView {
                    withVariantReselection()
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "bar"))
                    }
                }.files
                doLast {
                    assert files*.name == ["bar.txt"]
                }
            }
        """

        executer.expectDocumentedDeprecationWarning("While resolving configuration 'conf', it was also selected as a variant. Configurations should not act as both a resolution root and a variant simultaneously. Depending on the resolved configuration in this manner has been deprecated. This will fail with an error in Gradle 9.0. Be sure to mark configurations meant for resolution as canBeConsumed=false or use the 'resolvable(String)' configuration factory method to create them. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#depending_on_root_configuration")

        expect:
        succeeds("resolve")
    }

    def "resolvable configuration and consumable configuration from same project live in same resolved component"() {
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
                def root = result.root
                assert root.id.projectName == 'root'
                assert root.variants.size() == 2
                def conf = root.variants.find { it.displayName == 'conf' }
                def other = root.variants.find { it.displayName == 'other' }
                assert conf != null
                assert other != null
            }
        """

        expect:
        succeeds("resolve")
    }

    // This is not necessarily desired behavior, or important behavior at all.
    // The detached configuration is _not_ the project. It should not claim to be the project.
    // Ideally, this configuration would have an unspecified identity, similar to init, settings, and standalone scripts.
    def "project detached configurations are identified by the root project's identity"() {
        mavenRepo.module("org", "foo").publish()
        buildFile << """
            ${mavenTestRepository()}

            version = "1.0"
            group = "foo"

            task resolve {
                def rootComponent = configurations.detachedConfiguration(
                    dependencies.create("org:foo:1.0")
                ).incoming.resolutionResult.rootComponent
                doLast {
                    def root = rootComponent.get()
                    assert root.moduleVersion.group == "foo"
                    assert root.moduleVersion.name == "root-detachedConfiguration1"
                    assert root.moduleVersion.version == "1.0"
                    assert root.id instanceof ModuleComponentIdentifier
                    assert root.id.group == "foo"
                    assert root.id.module == "root-detachedConfiguration1"
                    assert root.id.version == "1.0"
                }
            }
        """

        expect:
        succeeds("resolve")
    }
}
