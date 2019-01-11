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

package org.gradle.integtests.resolve.attributes

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import spock.lang.Unroll

class MultipleVariantSelectionIntegrationTest extends AbstractModuleDependencyResolveTest {

    def setup() {
        buildFile << """
            def CUSTOM_ATTRIBUTE = Attribute.of('custom', String)
            dependencies.attributesSchema.attribute(CUSTOM_ATTRIBUTE)
        """
    }

    @RequiredFeatures(
            @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    )
    void "can select distinct variants of the same component by using different attributes"() {
        given:
        repository {
            'org:test:1.0' {
                variant('api') {
                    attribute('custom', 'c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                }
            }
        }

        buildFile << """
            dependencies {
                conf('org:test:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, 'c1')
                    }
                }
                conf('org:test:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, 'c2')
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectResolve()
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:test:1.0') {
                    variant('api', ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-api', custom: 'c1'])
                }
                module('org:test:1.0') {
                    variant('runtime', ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-runtime', custom: 'c2'])
                }
            }
        }
    }

    @RequiredFeatures(
            @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    )
    @Unroll("can select distinct variants of the same component by using different attributes with capabilities (conflict=#conflict)")
    void "can select distinct variants of the same component by using different attributes with capabilities"() {
        given:
        repository {
            'org:test:1.0' {
                variant('api') {
                    attribute('custom', 'c1')
                    capability('org.test', 'cap', '1.0')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                    capability('org.test', 'cap', conflict?'1.0':'1.1')
                }
            }
        }

        buildFile << """
            dependencies {
                conf('org:test:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, 'c1')
                    }
                }
                conf('org:test:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, 'c2')
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectGetMetadata()
                if (!conflict) {
                    expectGetArtifact()
                }
            }
        }
        if (conflict) {
            fails 'checkDeps'
        } else {
            succeeds 'checkDeps'
        }

        then:
        if (conflict) {
            failure.assertHasCause("Cannot choose between org:test:1.0 variant api and org:test:1.0 variant runtime because they provide the same capability: org.test:cap:1.0")
        } else {
            resolve.expectGraph {
                root(":", ":test:") {
                    edge('org:test:1.0', 'org:test:1.0') {
                        variant('runtime', ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-runtime', custom: 'c2'])
                    }
                    module('org:test:1.0') {
                        variant('runtime', ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-runtime', custom: 'c2'])
                    }
                }
            }
        }

        where:
        conflict << [true, false]
    }

    @RequiredFeatures(
            @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    )
    def "selects 2 variants of the same component when transitive dependency"() {
        given:
        repository {
            'org:foo:1.0' {
                variant('api') {
                    attribute('custom', 'c1')
                    artifact('c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                    artifact('c2')
                }
            }
            'org:foo:1.1' {
                variant('api') {
                    attribute('custom', 'c1')
                    artifact('c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                    artifact('c2')
                }
            }
            'org:bar:1.0' {
                variant('api') {
                    dependsOn('org:foo:1.1') {
                        attributes.custom = 'c1'
                    }
                }
                variant('runtime') {
                    dependsOn('org:foo:1.1') {
                        attributes.custom = 'c1'
                    }
                }
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo:1.0') {
                    attributes {
                        attribute(CUSTOM_ATTRIBUTE, 'c2')
                    }
                }
                conf('org:bar:1.0')
            }
        """

        when:
        repositoryInteractions {
            'org:bar:1.0' {
                expectResolve()
            }
            'org:foo:1.0' {
                expectGetMetadata()
            }
            'org:foo:1.1' {
                expectGetMetadata()
                expectGetVariantArtifacts('api')
                expectGetVariantArtifacts('runtime')
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org:foo:1.0', 'org:foo:1.1') {
                    byConflictResolution('between versions 1.0 and 1.1')
                    // the following assertion is true but limitations to the test fixtures make it hard to check
                    //variant('api',[custom:'c1', 'org.gradle.status':'integration', 'org.gradle.usage':'java-api'])
                    variant('runtime', [custom: 'c2', 'org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-runtime'])
                    artifact group: 'org', module: 'foo', version: '1.0', classifier: 'c1'
                    artifact group: 'org', module: 'foo', version: '1.0', classifier: 'c2'
                }
                module('org:bar:1.0') {
                    module('org:foo:1.1')
                }
            }
        }

    }

    @RequiredFeatures(
            @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    )
    def "selects a single variant of the same component when asking for a consumer specific attribute"() {
        given:
        repository {
            'org:foo:1.0' {
                variant('api') {
                    attribute('custom', 'c1')
                    artifact('c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                    artifact('c2')
                }
            }
            'org:foo:1.1' {
                variant('api') {
                    attribute('custom', 'c1')
                    artifact('c1')
                }
                variant('runtime') {
                    attribute('custom', 'c2')
                    artifact('c2')
                }
            }
            'org:bar:1.0' {
                variant('api') {
                    dependsOn('org:foo:1.1') {
                        attributes.custom = 'c2'
                    }
                }
                variant('runtime') {
                    dependsOn('org:foo:1.1') {
                        attributes.custom = 'c2'
                    }
                }
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo:1.0') {
                    attributes {
                        attribute(Attribute.of("other", String), 'unused')
                    }
                }
                conf('org:bar:1.0')
            }
        """

        when:
        repositoryInteractions {
            'org:bar:1.0' {
                expectResolve()
            }
            'org:foo:1.0' {
                expectGetMetadata()
            }
            'org:foo:1.1' {
                expectGetMetadata()
                expectGetVariantArtifacts('runtime')
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org:foo:1.0', 'org:foo:1.1') {
                    byConflictResolution('between versions 1.0 and 1.1')
                    variant('runtime', [custom: 'c2', 'org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-runtime'])
                    artifact group: 'org', module: 'foo', version: '1.0', classifier: 'c2'
                }
                module('org:bar:1.0') {
                    module('org:foo:1.1')
                }
            }
        }
    }

    @RequiredFeatures(
            @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    )
    def "can select both main variant and test fixtures of a single component"() {
        given:
        repository {
            'org:foo:1.0' {
                variant('api') {}
                variant('runtime') {}
                variant('test-fixtures') {
                    attribute('test-fixtures', 'true')
                    artifact('test-fixtures')
                }
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo:1.0')
                conf('org:foo:1.0') {
                    attributes {
                        // This setup is for tests only. Do not assume this is the right
                        // way to define test fixtures
                        attribute(Attribute.of("test-fixtures", String), 'true')
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectResolve()
                expectGetVariantArtifacts('test-fixtures')
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:foo:1.0') {
                    variant('runtime', ['org.gradle.status': defaultStatus(), 'org.gradle.usage': 'java-runtime'])
                    artifact group: 'org', module: 'foo', version: '1.0'
                }
                module('org:foo:1.0') {
                    variant('test-fixtures', ['org.gradle.status': defaultStatus(), 'test-fixtures': 'true'])
                    artifact group: 'org', module: 'foo', version: '1.0', classifier: 'test-fixtures'
                }
            }
        }
    }

    static Closure<String> defaultStatus() {
        { -> GradleMetadataResolveRunner.useIvy() ? 'integration' : 'release' }
    }
}
