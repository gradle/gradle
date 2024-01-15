/*
 * Copyright 2015 the original author or authors.
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
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Issue

class VariantsDependencySubstitutionRulesIntegrationTest extends AbstractIntegrationSpec {
    def resolve = new ResolveTestFixture(buildFile, "conf").expectDefaultConfiguration("runtime")

    def setup() {
        settingsFile << "rootProject.name='depsub'\n"
        resolve.prepare()
        resolve.addDefaultVariantDerivationStrategy()
    }

    @Issue("https://github.com/gradle/gradle/issues/13204")
    def "can substitute a normal dependency with a platform dependency"() {

        buildFile << """

            configurations {
                conf {
                    resolutionStrategy.dependencySubstitution {
                        substitute module('org:lib') using $notation
                    }
                }
            }

            dependencies {
                conf 'org:lib:1.0'
            }
        """

        createDirs("platform")
        settingsFile << """
            include 'platform'
        """

        file("platform/build.gradle") << """
            plugins {
                id 'java-platform'
            }

        """

        when:
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                edge('org:lib:1.0', ':platform', 'depsub:platform:') {
                    variant(expectedVariant, [
                        'org.gradle.category': expectedCategory,
                        'org.gradle.usage': 'java-runtime'
                    ])
                    selectedByRule()
                    noArtifacts()
                }
            }
        }

        where:
        notation                                                                                                                                       | expectedCategory    | expectedVariant
        'platform(project(":platform"))'                                                                                                               | 'platform'          | 'runtimeElements'
        'variant(project(":platform")) { platform() }'                                                                                                 | 'platform'          | 'runtimeElements'
        'variant(project(":platform")) { attributes { attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.REGULAR_PLATFORM)) } }'  | 'platform'          | 'runtimeElements'

        'variant(project(":platform")) { enforcedPlatform() }'                                                                                         | 'enforced-platform' | 'enforcedRuntimeElements'
        'variant(project(":platform")) { attributes { attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.ENFORCED_PLATFORM)) } }' | 'enforced-platform' | 'enforcedRuntimeElements'

    }

    @Issue("https://github.com/gradle/gradle/issues/13204")
    def "can substitute a platform dependency with a regular dependency"() {
        mavenRepo.module("org", "lib", "1.0").publish()

        buildFile << """

            repositories {
               maven { url "${mavenRepo.uri}" }
            }

            configurations {
                conf {
                    resolutionStrategy.dependencySubstitution {
                        substitute $notation using module('org:lib:1.0')
                    }
                }
            }

            dependencies {
                conf platform('org:lib:1.0')
            }
        """

        when:
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                edge('org:lib:1.0', 'org:lib:1.0') {
                    variant('runtime', [
                        'org.gradle.category': 'library',
                        'org.gradle.libraryelements': 'jar',
                        'org.gradle.status': 'release',
                        'org.gradle.usage': 'java-runtime'
                    ])
                    selectedByRule()
                }
            }
        }

        where:
        notation << [
            'platform(module("org:lib:1.0"))',
            'variant(platform(module("org:lib:1.0"))) { }',
            'variant(module("org:lib:1.0")) { platform() }',
            'variant(module("org:lib:1.0")) { attributes { attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.REGULAR_PLATFORM)) } }',
        ]

    }

    def "can substitute a dependency without capabilities with a dependency with capabilities"() {
        mavenRepo.module("org", "lib", "1.0").publish()

        buildFile << """

            repositories {
               maven { url "${mavenRepo.uri}" }
            }

            configurations {
                conf {
                    resolutionStrategy.dependencySubstitution {
                        substitute module('org:lib:1.0') using variant(module('org:lib:1.0')) {
                            capabilities {
                                requireCapability 'org:lib-test-fixtures'
                            }
                        }
                    }
                }
            }

            dependencies {
                conf 'org:lib:1.0'
            }
        """

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause "Unable to find a variant of org:lib:1.0 providing the requested capability org:lib-test-fixtures:"
    }

    def "can substitute a project dependency without capabilities with a dependency with capabilities"() {
        mavenRepo.module("org", "lib", "1.0").publish()

        buildFile << """

            repositories {
               maven { url "${mavenRepo.uri}" }
            }

            configurations {
                conf {
                    resolutionStrategy.dependencySubstitution {
                        substitute project(':other') using variant(module('org:lib:1.0')) {
                            capabilities {
                                requireCapability 'org:lib-test-fixtures'
                            }
                        }
                    }
                }
            }

            dependencies {
                conf project(":other")
            }
        """

        createDirs("other")
        settingsFile << """
            include 'other'
        """

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause "Unable to find a variant of org:lib:1.0 providing the requested capability org:lib-test-fixtures:"
    }

    def "can substitute a dependency with capabilities with a dependency without capabilities"() {
        mavenRepo.module("org", "lib", "1.0").publish()

        buildFile << """

            repositories {
               maven { url "${mavenRepo.uri}" }
            }

            configurations {
                conf {
                    resolutionStrategy.dependencySubstitution {
                        substitute variant(module('org:lib:1.0')) {
                            capabilities {
                                requireCapability 'org:lib-test-fixtures'
                            }
                        } using module('org:lib:1.0')
                    }
                }
            }

            dependencies {
                conf('org:lib:1.0') {
                    capabilities {
                        requireCapability 'org:lib-test-fixtures'
                    }
                }
            }
        """

        when:
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                edge('org:lib:1.0', 'org:lib:1.0') {
                    variant('runtime', [
                        'org.gradle.category': 'library',
                        'org.gradle.libraryelements': 'jar',
                        'org.gradle.status': 'release',
                        'org.gradle.usage': 'java-runtime'
                    ])
                    selectedByRule()
                }
            }
        }
    }

    def "can substitute a dependency with capabilities with a project dependency without capabilities"() {
        buildFile << """

            repositories {
               maven { url "${mavenRepo.uri}" }
            }

            configurations {
                conf {
                    resolutionStrategy.dependencySubstitution {
                        substitute variant(module('org:lib:1.0')) {
                            capabilities {
                                requireCapability 'org:lib-test-fixtures'
                            }
                        } using project(':other')
                    }
                }
            }

            dependencies {
                conf('org:lib:1.0') {
                    capabilities {
                        requireCapability 'org:lib-test-fixtures'
                    }
                }
            }
        """

        createDirs("other")
        settingsFile << """
            include 'other'
        """
        file("other/build.gradle") << """
            plugins {
                id 'java-library'
            }
            group = 'org'
        """

        when:
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":depsub:") {
                edge('org:lib:1.0', ':other', 'org:other:') {
                    configuration = 'runtimeElements'
                    selectedByRule()
                }
            }
        }
    }
}
