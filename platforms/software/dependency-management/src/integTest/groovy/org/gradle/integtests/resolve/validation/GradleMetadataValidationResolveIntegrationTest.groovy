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

package org.gradle.integtests.resolve.validation

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import org.gradle.test.fixtures.gradle.GradleFileModuleAdapter
import spock.lang.Issue

@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
class GradleMetadataValidationResolveIntegrationTest extends AbstractModuleDependencyResolveTest {

    def "can resolve if component gav information is missing"() {
        GradleFileModuleAdapter.printComponentGAV = false
        buildFile << """
            dependencies {
                conf 'org.test:projectA:1.1'
            }
        """

        when:
        repository {
            'org.test:projectA:1.1'()
        }
        repositoryInteractions {
            'org.test:projectA:1.1' {
                expectResolve()
            }
        }

        then:
        succeeds ":checkDeps"
        resolve.expectGraph {
            root(":", ":test:") {
                module("org.test:projectA:1.1")
            }
        }

        cleanup:
        GradleFileModuleAdapter.printComponentGAV = true
    }

    def "fails with proper error if a mandatory attribute is not defined"() {
        buildFile << """
            dependencies {
                conf 'org.test:projectA:1.1'
            }
        """

        when:
        repository {
            'org.test:projectA:1.1' {
                variant("api") {
                    artifact("name", null, "name")
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA:1.1' {
                expectGetMetadata()
            }
        }

        then:
        fails ":checkDeps"
        failure.assertHasCause("missing 'url' at /variants[0]/files[0]")
    }

    @Issue("gradle/gradle#7888")
    def "fails with reasonable error message if Gradle Module Metadata doesn't declare any variant"() {
        buildFile << """
            dependencies {
                conf 'org.test:projectA:1.1'
            }
        """

        when:
        repository {
            'org.test:projectA:1.1' {
                withModule {
                    withoutDefaultVariants()
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA:1.1' {
                expectGetMetadata()
            }
        }

        then:
        fails ":checkDeps"
        failure.assertHasCause("Gradle Module Metadata for module org.test:projectA:1.1 is invalid because it doesn't declare any variant")
    }
}
