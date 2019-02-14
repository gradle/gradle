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

package org.gradle.integtests.resolve.packing

import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.DependencyPacking
import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import spock.lang.Ignore
import spock.lang.Unroll

@RequiredFeatures(
        [@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")]
)
class JavaPackingResolveIntegrationTest extends AbstractModuleDependencyResolveTest {

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

    @Ignore
    def "Spock workaround"() {
        expect:
        true
    }

    @Unroll
    def "defaults to the external dependencies variant (#packing)"() {
        given:
        repository {
            'org:transitive:1.0'()
            'org:producer:1.0' {
                variant('api') {
                    dependsOn('org:transitive:1.0')
                    attribute DependencyPacking.PACKING.name, 'external'
                }
                variant('runtime') {
                    dependsOn('org:transitive:1.0')
                    attribute DependencyPacking.PACKING.name, 'external'
                }
                variant('fatApi') {
                    attribute Usage.USAGE_ATTRIBUTE.name, 'java-api-jars'
                    attribute DependencyPacking.PACKING.name, packing
                }
                variant('fatRuntime') {
                    attribute Usage.USAGE_ATTRIBUTE.name, 'java-runtime-jars'
                    attribute DependencyPacking.PACKING.name, packing
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
                            'org.gradle.dependency.packing': 'external',
                            'org.gradle.status': defaultStatus(),
                            'org.gradle.usage': 'java-api-jars'
                    ])
                    module('org:transitive:1.0')
                }
            }
        }

        where:
        packing << [DependencyPacking.FATJAR, DependencyPacking.SHADOWED]
    }

    @Unroll
    def "selects the appropriate variant (producer=#packing, requested=#requested, selected=#selected)"() {
        given:
        repository {
            'org:transitive:1.0'()
            'org:producer:1.0' {
                variant('api') {
                    dependsOn('org:transitive:1.0')
                    attribute DependencyPacking.PACKING.name, 'external'
                }
                variant('runtime') {
                    dependsOn('org:transitive:1.0')
                    attribute DependencyPacking.PACKING.name, 'external'
                }
                variant('fatApi') {
                    attribute Usage.USAGE_ATTRIBUTE.name, 'java-api-jars'
                    attribute DependencyPacking.PACKING.name, packing
                }
                variant('fatRuntime') {
                    attribute Usage.USAGE_ATTRIBUTE.name, 'java-runtime-jars'
                    attribute DependencyPacking.PACKING.name, packing
                }
            }
        }

        buildFile << """
            dependencies {
                conf("org:producer:1.0") {
                    attributes {
                        attribute(DependencyPacking.PACKING, objects.named(DependencyPacking, '$requested'))
                    }
                }
            }
        """

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
            failure.assertHasCause("Unable to find a matching variant of org:producer:1.0")
        } else {
            resolve.expectGraph {
                root(":", ":test:") {
                    module('org:producer:1.0') {
                        variant('fatApi', [
                                'org.gradle.dependency.packing': selected,
                                'org.gradle.status': defaultStatus(),
                                'org.gradle.usage': 'java-api-jars'
                        ])
                    }
                }
            }
        }

        where:
        packing                    | requested                  | selected
        DependencyPacking.FATJAR   | DependencyPacking.FATJAR   | DependencyPacking.FATJAR
        DependencyPacking.FATJAR   | DependencyPacking.SHADOWED | null
        DependencyPacking.SHADOWED | DependencyPacking.FATJAR   | DependencyPacking.SHADOWED
        DependencyPacking.SHADOWED | DependencyPacking.SHADOWED | DependencyPacking.SHADOWED
    }

    static Closure<String> defaultStatus() {
        { -> GradleMetadataResolveRunner.useIvy() ? 'integration' : 'release' }
    }

}
