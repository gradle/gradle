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

package org.gradle.integtests.resolve.bundling

import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import org.gradle.internal.component.ResolutionFailureHandler

@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
class JavaBundlingResolveIntegrationTest extends AbstractModuleDependencyResolveTest {

    def setup() {
        buildFile << """
            configurations {
                conf {
                    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
                }
            }
            apply plugin: 'java-base'
        """
    }

    def "defaults to the external dependencies variant (#bundling)"() {
        given:
        repository {
            'org:transitive:1.0'()
            'org:producer:1.0' {
                variant('api') {
                    dependsOn('org:transitive:1.0')
                    attribute Bundling.BUNDLING_ATTRIBUTE.name, 'external'
                }
                variant('runtime') {
                    dependsOn('org:transitive:1.0')
                    attribute Bundling.BUNDLING_ATTRIBUTE.name, 'external'
                }
                variant('fatApi') {
                    attribute Usage.USAGE_ATTRIBUTE.name, 'java-api'
                    attribute LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE.name, 'jar'
                    attribute Bundling.BUNDLING_ATTRIBUTE.name, bundling
                }
                variant('fatRuntime') {
                    attribute Usage.USAGE_ATTRIBUTE.name, 'java-runtime'
                    attribute LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE.name, 'jar'
                    attribute Bundling.BUNDLING_ATTRIBUTE.name, bundling
                }
            }
        }

        buildFile << """
            dependencies {
                conf("org:producer:1.0")
            }
        """

        when:
        repositoryInteractions {
            'org:producer:1.0' {
                expectResolve()
            }
            'org:transitive:1.0' {
                expectResolve()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:producer:1.0') {
                    variant('api', [
                            'org.gradle.dependency.bundling': 'external',
                            'org.gradle.status': JavaBundlingResolveIntegrationTest.defaultStatus(),
                            'org.gradle.usage': 'java-api',
                            'org.gradle.libraryelements': 'jar',
                            'org.gradle.category': 'library'
                    ])
                    module('org:transitive:1.0')
                }
            }
        }

        where:
        bundling << [Bundling.EMBEDDED, Bundling.SHADOWED]
    }

    def "selects the appropriate variant (producer=#bundling, requested=#requested, selected=#selected)"() {
        given:
        repository {
            'org:transitive:1.0'()
            'org:producer:1.0' {
                variant('api') {
                    dependsOn('org:transitive:1.0')
                    attribute Bundling.BUNDLING_ATTRIBUTE.name, 'external'
                }
                variant('runtime') {
                    dependsOn('org:transitive:1.0')
                    attribute Bundling.BUNDLING_ATTRIBUTE.name, 'external'
                }
                variant('fatApi') {
                    attribute Usage.USAGE_ATTRIBUTE.name, 'java-api'
                    attribute LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE.name, 'jar'
                    attribute Bundling.BUNDLING_ATTRIBUTE.name, bundling
                }
                variant('fatRuntime') {
                    attribute Usage.USAGE_ATTRIBUTE.name, 'java-runtime'
                    attribute LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE.name, 'jar'
                    attribute Bundling.BUNDLING_ATTRIBUTE.name, bundling
                }
            }
        }

        buildFile << """
            dependencies {
                conf("org:producer:1.0") {
                    attributes {
                        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling, '$requested'))
                    }
                }
            }
        """

        file("gradle.properties").text = "${ResolutionFailureHandler.FULL_FAILURES_MESSAGE_PROPERTY}=true"

        when:
        boolean shouldFail = selected == null
        repositoryInteractions {
            'org:producer:1.0' {
                if (shouldFail) {
                    expectGetMetadata()
                } else {
                    expectResolve()
                }
            }
        }
        if (shouldFail) {
            fails ':checkDeps'
        } else {
            run ':checkDeps'
        }

        then:
        if (shouldFail) {
            failure.assertHasCause("No matching variant of org:producer:1.0 was found. The consumer was configured to find a component for use during compile-time, and its dependencies repackaged (shadow jar) but:")
        } else {
            resolve.expectGraph {
                root(":", ":test:") {
                    module('org:producer:1.0') {
                        variant('fatApi', [
                                'org.gradle.dependency.bundling': selected,
                                'org.gradle.status': JavaBundlingResolveIntegrationTest.defaultStatus(),
                                'org.gradle.usage': 'java-api',
                                'org.gradle.libraryelements': 'jar'
                        ])
                    }
                }
            }
        }

        where:
        bundling           | requested         | selected
        Bundling.EMBEDDED | Bundling.EMBEDDED | Bundling.EMBEDDED
        Bundling.EMBEDDED | Bundling.SHADOWED | null
        Bundling.SHADOWED | Bundling.EMBEDDED | Bundling.SHADOWED
        Bundling.SHADOWED | Bundling.SHADOWED | Bundling.SHADOWED
    }

    static Closure<String> defaultStatus() {
        { -> GradleMetadataResolveRunner.useIvy() ? 'integration' : 'release' }
    }

}
