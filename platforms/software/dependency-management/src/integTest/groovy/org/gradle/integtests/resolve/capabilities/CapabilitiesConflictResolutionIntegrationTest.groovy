/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.resolve.capabilities

import org.gradle.api.attributes.Category
import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
class CapabilitiesConflictResolutionIntegrationTest extends AbstractModuleDependencyResolveTest {

    def "reasonable error message when a user rule throws an exception (#rule)"() {
        given:
        repository {
            id('org:testA:1.0') {
                variant('runtime') {
                    capability('org', 'testA', '1.0')
                    capability('cap')
                }
            }
            id('org:testB:1.0') {
                variant('runtime') {
                    capability('org', 'testB', '1.0')
                    capability('cap')
                }
            }
        }

        buildFile << """
            dependencies {
                conf 'org:testA:1.0'
                conf 'org:testB:1.0'
            }

            // fix the conflict between modules providing the same capability using resolution rules
            configurations.all {
                resolutionStrategy {
                   capabilitiesResolution.withCapability('org.test:cap') {
                      $rule
                      because "we like testB better"
                   }
                }
            }
        """

        when:
        repositoryInteractions {
            id('org:testA:1.0') {
                expectGetMetadata()
            }
            id('org:testB:1.0') {
                expectGetMetadata()
            }
        }
        fails ":checkDeps"

        then:

        failure.assertHasCause(error)

        where:
        rule                               | error
        "throw new NullPointerException()" | "Capability resolution rule failed with an error" // error in user code
        "select('org:testD:1.0')"          | "org:testD:1.0 is not a valid candidate for conflict resolution on capability 'org.test:cap': candidates are ['org:testA:1.0' (runtime), 'org:testB:1.0' (runtime)]"// invalid candidate

    }

    def "can express preference for capabilities declared in published modules (#rule)"() {
        given:
        repository {
            id('org:testA:1.0') {
                variant('runtime') {
                    capability('org', 'testA', '1.0')
                    capability('cap')
                }
            }
            id('org:testB:1.0') {
                variant('runtime') {
                    capability('org', 'testB', '1.0')
                    capability('cap')
                }
            }
        }

        buildFile << """
            dependencies {
                conf 'org:testA:1.0'
                conf 'org:testB:1.0'
            }

            // fix the conflict between modules providing the same capability using resolution rules
            configurations.all {
                resolutionStrategy {
                   capabilitiesResolution.withCapability('org.test:cap') {
                      $rule
                      because "we like testB better"
                   }
                }
            }
        """

        when:
        repositoryInteractions {
            id('org:testA:1.0') {
                expectGetMetadata()
            }
            id('org:testB:1.0') {
                expectResolve()
            }
        }
        run ":checkDeps"

        then:

        resolve.expectGraph {
            root(":", ":test:") {
                edge('org:testA:1.0', 'org:testB:1.0')
                    .byConflictResolution("On capability org.test:cap we like testB better")
                module('org:testB:1.0')
            }
        }

        where:
        rule << [
            "select(candidates.find { it.id.module == 'testB'})",
            "select('org:testB:1.0')",
            "select('org:testB:1.1')", // we are lenient wrt to the version number
        ]
    }

    def "can express preference for a certain variant with capabilities declared in published modules"() {
        given:
        repository {
            id('org:testB:1.0') {
                variant('runtime') {
                    capability('org', 'testB', '1.0')
                }
                variant('runtimeAlt') {
                    capability('org', 'testB', '1.0')
                    capability('special')
                }
            }
        }

        buildFile << """
            dependencies {
                conf 'org:testB:1.0'
                conf('org:testB:1.0') {
                    capabilities {
                        requireCapability("org.test:special")
                    }
                }
            }

            // fix the conflict between variants of module providing the same capability using resolution rules
            configurations.all {
                resolutionStrategy {
                   capabilitiesResolution.withCapability('org:testB') {
                      select(candidates.find { it.variantName == 'runtimeAlt'})
                      because "we want runtimeAlt with 'special'"
                   }
                }
            }
        """

        when:
        repositoryInteractions {
            id('org:testB:1.0') {
                expectResolve()
            }
        }
        run ":checkDeps"

        then:

        resolve.expectGraph {
            root(":", ":test:") {
                module('org:testB:1.0:runtimeAlt').byConflictResolution("On capability org:testB we want runtimeAlt with 'special'")
                module('org:testB:1.0:runtimeAlt')
            }
        }
    }

    def "expressing a preference for a variant with capabilities declared in a published modules does not evict unrelated variants"() {
        given:
        repository {
            id('org:testB:1.0') {
                variant('runtime') {
                    capability('org', 'testB', '1.0')
                }
                variant('runtimeAlt') {
                    capability('org', 'testB', '1.0')
                    capability('special')
                }
                variant('runtimeOptional') {
                    capability('optional')
                }
            }
        }

        buildFile << """
            dependencies {
                conf 'org:testB:1.0'
                conf('org:testB:1.0') {
                    capabilities {
                        requireCapability("org.test:special")
                    }
                }
                conf('org:testB:1.0') {
                    capabilities {
                        requireCapability("org.test:optional")
                    }
                }
            }

            // fix the conflict between variants of module providing the same capability using resolution rules
            configurations.all {
                resolutionStrategy {
                   capabilitiesResolution.withCapability('org:testB') {
                      select(candidates.find { it.variantName == 'runtimeAlt'})
                      because "we want runtimeAlt with 'special'"
                   }
                }
            }
        """

        when:
        repositoryInteractions {
            id('org:testB:1.0') {
                expectResolve()
            }
        }
        run ":checkDeps"

        then:

        resolve.expectGraph {
            root(":", ":test:") {
                module('org:testB:1.0:runtimeAlt').byConflictResolution("On capability org:testB we want runtimeAlt with 'special'")
                module('org:testB:1.0:runtimeAlt')
                module('org:testB:1.0:runtimeOptional')
            }
        }
    }

    def "can select unrelated variant from component with variant that loses capability conflict"() {
        settingsFile << """
            include("producer1")
            include("producer2")
        """
        buildFile << """
            dependencies {
                conf(project(":producer2")) {
                    capabilities {
                        requireCapability("org:bar:1.0")
                    }
                }
                conf(project(":producer1")) {
                    capabilities {
                        requireCapability("org:foo:2.0")
                    }
                }
                conf(project(":producer2")) {
                    capabilities {
                        requireCapability("org:foo:1.0")
                    }
                }
            }

            configurations.conf {
                resolutionStrategy {
                    capabilitiesResolution {
                        withCapability("org:foo") {
                            selectHighestVersion()
                        }
                    }
                }
            }
        """

        file("producer1/build.gradle") << """
            configurations {
                consumable("foo") {
                    outgoing.capability("org:foo:2.0")
                    outgoing.artifact(file("producer1-foo-\${version}.jar"))
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, named(Category, Category.LIBRARY))
                    }
                }
            }
        """
        file("producer2/build.gradle") << """
            configurations {
                consumable("foo") {
                    outgoing.capability("org:foo:1.0")
                    outgoing.artifact(file("producer2-foo-\${version}.jar"))
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, named(Category, Category.LIBRARY))
                    }
                }
                consumable("bar") {
                    outgoing.capability("org:bar:1.0")
                    outgoing.artifact(file("producer2-bar-\${version}.jar"))
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, named(Category, Category.LIBRARY))
                    }
                }
            }
        """

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                project(":producer2", "test:producer2:unspecified") {
                    variant("bar", [(Category.CATEGORY_ATTRIBUTE.name): Category.LIBRARY])
                    artifact(name: "producer2-bar")
                }
                project(":producer1", "test:producer1:unspecified") {
                    variant("foo", [(Category.CATEGORY_ATTRIBUTE.name): Category.LIBRARY])
                    artifact(name: "producer1-foo")
                    byReason("conflict resolution: latest version of capability org:foo")
                }
                edge("project :producer2", "project :producer1", "test:producer1:unspecified")
            }
        }
    }

}
