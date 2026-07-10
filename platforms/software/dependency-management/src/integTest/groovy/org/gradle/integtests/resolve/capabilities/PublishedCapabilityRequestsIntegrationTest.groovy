/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
class PublishedCapabilityRequestsIntegrationTest extends AbstractModuleDependencyResolveTest {

    def "capability request without versions can be consumed"() {
        given:
        repository {
            'org:foo:1.0' {
                variant('runtimeAlt') {
                    attribute('custom', 'c1')
                    capability('alt')
                }
            }
            'org:bar:1.0' {
                dependsOn([group: 'org', artifact: 'foo', version: '1.0', requireCapability: 'org.test:alt'])
            }
        }

        buildFile << """
            dependencies {
                conf('org:bar:1.0')
            }
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectResolve()
            }
            'org:bar:1.0' {
                expectResolve()
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:bar:1.0') {
                    module('org:foo:1.0') {
                        variant('runtimeAlt', [custom: 'c1', 'org.gradle.status': PublishedCapabilityRequestsIntegrationTest.defaultStatus()])
                    }
                }
            }
        }
    }

    static Closure<String> defaultStatus() {
        { -> GradleMetadataResolveRunner.useIvy() ? 'integration' : 'release' }
    }

}
