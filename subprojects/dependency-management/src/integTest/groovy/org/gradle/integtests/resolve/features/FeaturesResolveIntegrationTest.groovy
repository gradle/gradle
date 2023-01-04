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

package org.gradle.integtests.resolve.features

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
class FeaturesResolveIntegrationTest extends AbstractModuleDependencyResolveTest {

    def "can select a variant providing a different capability"() {
        given:
        repository {
            'org:foo:1.0' {
                variant('api') {}
                variant('runtime') {}
                variant('feature1') {
                    capability('org', 'feature-1', '1.0')
                    attribute('org.gradle.usage', 'java-runtime')
                    attribute('org.gradle.libraryelements', 'jar')
                    attribute('org.gradle.category', 'library')
                    artifact('feat1')
                }
                variant('feature2') {
                    capability('org', 'feature-2', '1.0')
                    attribute('org.gradle.usage', 'java-runtime')
                    attribute('org.gradle.libraryelements', 'jar')
                    attribute('org.gradle.category', 'library')
                    artifact('feat2')
                }
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo:1.0')
                conf('org:foo:1.0') {
                    capabilities {
                        requireCapability('org:feature-1')
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectResolve()
                expectGetVariantArtifacts('feature1')
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:foo:1.0') {
                    variant('runtime', ['org.gradle.status': FeaturesResolveIntegrationTest.defaultStatus(), 'org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library'])
                    artifact()
                }
                module('org:foo:1.0') {
                    variant('feature1', ['org.gradle.status': FeaturesResolveIntegrationTest.defaultStatus(), 'org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library'])
                    artifact classifier: 'feat1'
                }
            }
        }
    }

    def "reasonable error message when no variant provides required capability"() {
        given:
        repository {
            'org:foo:1.0' {
                variant('api') {}
                variant('runtime') {}
                variant('feature1') {
                    capability('org', 'feature-1', '1.0')
                    attribute('org.gradle.usage', 'java-runtime')
                    artifact('feat1')
                }
                variant('feature2') {
                    capability('org', 'feature-2', '1.0')
                    attribute('org.gradle.usage', 'java-runtime')
                    artifact('feat2')
                }
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo:1.0')
                conf('org:foo:1.0') {
                    capabilities {
                        requireCapability('org:feature-3')
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectGetMetadata()
            }
        }
        fails 'checkDeps'

        then:
        failure.assertHasCause("""Unable to find a variant of org:foo:1.0 providing the requested capability org:feature-3:
   - Variant api provides org:foo:1.0
   - Variant runtime provides org:foo:1.0
   - Variant feature1 provides org:feature-1:1.0
   - Variant feature2 provides org:feature-2:1.0""")
    }

    def "can select a variant providing the required set of capabilities"() {
        given:
        repository {
            'org:foo:1.0' {
                variant('api') {}
                variant('runtime') {}
                variant('v1') {
                    capability('org', 'feature-1', '1.0')
                    capability('org', 'feature-2', '1.0')
                    attribute('org.gradle.usage', 'java-runtime')
                    attribute('org.gradle.libraryelements', 'jar')
                    attribute('org.gradle.category', 'library')
                    artifact('feat1')
                    artifact('feat2')
                }
                variant('v2') {
                    capability('org', 'feature-1', '1.0')
                    capability('org', 'feature-3', '1.0')
                    attribute('org.gradle.usage', 'java-runtime')
                    attribute('org.gradle.libraryelements', 'jar')
                    attribute('org.gradle.category', 'library')
                    artifact('feat1')
                    artifact('feat3')
                }
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo:1.0')
                conf('org:foo:1.0') {
                    capabilities {
                        requireCapabilities('org:feature-1', 'org:feature-3')
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectResolve()
                expectGetVariantArtifacts('v2')
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:foo:1.0') {
                    variant('runtime', ['org.gradle.status': FeaturesResolveIntegrationTest.defaultStatus(), 'org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library'])
                    artifact()
                }
                module('org:foo:1.0') {
                    variant('v2', ['org.gradle.status': FeaturesResolveIntegrationTest.defaultStatus(), 'org.gradle.usage': 'java-runtime', 'org.gradle.libraryelements': 'jar', 'org.gradle.category': 'library'])
                    artifact classifier: 'feat1'
                    artifact classifier: 'feat3'
                }
            }
        }
    }

    static Closure<String> defaultStatus() {
        { -> GradleMetadataResolveRunner.useIvy() ? 'integration' : 'release' }
    }
}
