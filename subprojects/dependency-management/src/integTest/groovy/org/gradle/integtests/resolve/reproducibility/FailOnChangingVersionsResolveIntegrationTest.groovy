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

package org.gradle.integtests.resolve.reproducibility

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

class FailOnChangingVersionsResolveIntegrationTest extends AbstractModuleDependencyResolveTest {

    String getNotation() {
        "failOnChangingVersions"
    }

    def setup() {
        buildFile << """
            configurations.all {
                resolutionStrategy.$notation()
            }
        """
    }

    def "fails to resolve a direct changing dependency"() {
        buildFile << """
            dependencies {
                conf('org:test:1.0') {
                    changing = true
                }
            }
        """

        repository {
            'org:test:1.0'()
        }

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectGetMetadata()
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("Could not resolve org:test:1.0: Resolution strategy disallows usage of changing versions")
    }

    def "fails to resolve a transitive changing dependency"() {
        buildFile << """
            dependencies {
                conf('org:test:1.0')
                components {
                    withModule('org:testB') {
                        changing = true
                    }
                }
            }
        """

        repository {
            'org:test:1.0' {
                dependsOn 'org:testB:1.0'
            }
            'org:testB:1.0'()
        }

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectGetMetadata()
            }
            'org:testB:1.0' {
                expectGetMetadata()
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("Could not resolve org:testB:1.0: Resolution strategy disallows usage of changing versions")
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    def "can deny a direct snapshot dependency (unique = #unique)"() {
        buildFile << """
            dependencies {
                conf('org:test:1.0-SNAPSHOT')
            }
        """

        repository {
            'org:test:1.0-SNAPSHOT' {
                withModule {
                    if (!unique) {
                        withNonUniqueSnapshots()
                    }
                }
            }
        }

        when:
        repositoryInteractions {
            'org:test:1.0-SNAPSHOT' {
                allowAll()
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("Could not resolve org:test:1.0-SNAPSHOT: Resolution strategy disallows usage of changing versions")

        where:
        unique << [true, false]
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    def "can deny a transitive snapshot dependency (unique = #unique)"() {
        buildFile << """
            dependencies {
                conf 'org:test:1.0'
            }
        """

        repository {
            'org:test:1.0' {
                dependsOn 'org:testB:1.0-SNAPSHOT' // oh noes!
            }
            'org:testB:1.0-SNAPSHOT' {
                withModule {
                    if (!unique) {
                        withNonUniqueSnapshots()
                    }
                }
            }
        }

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectGetMetadata()
            }
            'org:testB:1.0-SNAPSHOT' {
                allowAll()
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("Could not resolve org:testB:1.0-SNAPSHOT: Resolution strategy disallows usage of changing versions")

        where:
        unique << [true, false]
    }
}
