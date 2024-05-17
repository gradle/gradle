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

package org.gradle.integtests.resolve.attributes

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import spock.lang.Unroll

@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
class ComponentAttributesDynamicVersionIntegrationTest extends AbstractModuleDependencyResolveTest {

    @Unroll("#outcome if component-level attribute is #requested")
    def "component attributes are used to reject fixed version"() {
        given:
        repository {
            'org.test:module:1.0' {
                attribute('quality', 'qa')
            }
        }
        buildFile << """
            def quality = Attribute.of("quality", String)
            configurations {
                conf.attributes.attribute(quality, '$requested')
            }

            dependencies {
                attributesSchema {
                    attribute(quality)
                }
                conf 'org.test:module:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org.test:module:1.0' {
                expectGetMetadata()
                if (requested == 'qa') {
                    expectGetArtifact()
                }
            }
        }

        then:
        if (requested == 'qa') {
            run ':checkDeps'
            resolve.expectGraph {
                root(":", ":test:") {
                    module('org.test:module:1.0')
                }
            }
        } else {
            fails ':checkDeps'
            failure.assertHasCause("No matching variant of org.test:module:1.0 was found. The consumer was configured to find attribute 'quality' with value 'canary' but:")
            failure.assertThatCause(containsNormalizedString("Incompatible because this component declares attribute 'quality' with value 'qa' and the consumer needed attribute 'quality' with value '$requested'"))
        }

        where:
        requested | outcome
        'qa'      | 'succeeds'
        'canary'  | 'fails'
    }

    @Unroll("selects the first version which matches the component-level attributes (requested=#requested)")
    def "selects the first version which matches the component-level attributes"() {
        given:
        repository {
            'org.test:module:1.3' {
                attribute('quality', 'rc')
            }
            'org.test:module:1.2' {
                attribute('quality', 'rc')
            }
            'org.test:module:1.1' {
                attribute('quality', 'qa')
            }
            'org.test:module:1.0' {
                attribute('quality', 'beta')
            }
        }
        buildFile << """
            def quality = Attribute.of("quality", String)
            configurations {
                conf.attributes.attribute(quality, 'qa')
            }

            dependencies {
                attributesSchema {
                    attribute(quality)
                }
                conf 'org.test:module:$requested'
            }
        """

        when:
        repositoryInteractions {
            'org.test:module' {
                expectVersionListing()
            }
            'org.test:module:1.3' {
                expectGetMetadata()
            }
            'org.test:module:1.2' {
                expectGetMetadata()
            }
            'org.test:module:1.1' {
                expectResolve()
            }
        }

        then:

        run ':checkDeps'
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:module:${requested}", 'org.test:module:1.1') {
                    byReason("rejection: version 1.3:   - Attribute 'quality' didn't match. Requested 'qa', was: 'rc'")
                    byReason("rejection: version 1.2:   - Attribute 'quality' didn't match. Requested 'qa', was: 'rc'")
                }
            }
        }

        where:
        requested << ["[1.0,)", latestNotation(), "1.+", "1+", "+"]
    }

    @Unroll("selects the first version which matches the component-level attributes (requested=#requested) using dependency attributes")
    def "selects the first version which matches the component-level attributes using dependency attributes"() {
        given:
        repository {
            'org.test:module:1.3' {
                attribute('quality', 'rc')
            }
            'org.test:module:1.2' {
                attribute('quality', 'rc')
            }
            'org.test:module:1.1' {
                attribute('quality', 'qa')
            }
            'org.test:module:1.0' {
                attribute('quality', 'beta')
            }
        }
        buildFile << """
            def quality = Attribute.of("quality", String)

            configurations {
                // This test also makes sure that configuration-level attributes are overwritten
                // by dependency-level attributes. This should really belong to a different test
                // but since integration tests are pretty slow we do both in one go, knowing that
                // configuration-level already has its own test
                conf.attributes.attribute(quality, 'boo')
            }

            dependencies {
                attributesSchema {
                    attribute(quality)
                }
                conf('org.test:module:$requested') {
                    attributes {
                        attribute(quality, 'qa')
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'org.test:module' {
                expectVersionListing()
            }
            'org.test:module:1.3' {
                expectGetMetadata()
            }
            'org.test:module:1.2' {
                expectGetMetadata()
            }
            'org.test:module:1.1' {
                expectResolve()
            }
        }

        then:

        run ':checkDeps'
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:module:${requested}", 'org.test:module:1.1') {
                    byReason("rejection: version 1.3:   - Attribute 'quality' didn't match. Requested 'qa', was: 'rc'")
                    byReason("rejection: version 1.2:   - Attribute 'quality' didn't match. Requested 'qa', was: 'rc'")
                }
            }
        }

        where:
        requested << ["[1.0,)", latestNotation(), "1.+", "1+", "+"]
    }

    def "reasonable error message whenever a dynamic version doesn't match any version because of single attribute mismatch"() {
        given:
        repository {
            'org:test:1.0' {
                attribute('color', 'red')
            }
            'org:test:1.1' {
                attribute('color', 'blue')
            }
        }

        buildFile << """
            def color = Attribute.of("color", String)

            configurations {
                conf.attributes.attribute(color, 'green')
            }

            dependencies {
                attributesSchema {
                    attribute(color)
                }
                conf 'org:test:[1.0,)'
            }
        """

        when:
        repositoryInteractions {
            'org:test' {
                expectVersionListing()
                '1.1' {
                    expectGetMetadata()
                }
                '1.0' {
                    expectGetMetadata()
                }
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("""Could not find any version that matches org:test:[1.0,).
Versions rejected by attribute matching:
  - 1.1:
      - Attribute 'color' didn't match. Requested 'green', was: 'blue'
  - 1.0:
      - Attribute 'color' didn't match. Requested 'green', was: 'red'
""")
    }

    def "reasonable error message whenever a dynamic version doesn't match any version because of multiple attributes"() {
        given:
        repository {
            'org:test:1.0' {
                attribute('color', 'red')
                attribute('shape', 'square')
            }
            'org:test:1.1' {
                attribute('color', 'blue')
                attribute('shape', 'circle')
            }
        }

        buildFile << """
            def color = Attribute.of("color", String)
            def shape = Attribute.of("shape", String)

            configurations {
                conf.attributes.attribute(color, 'green')
                conf.attributes.attribute(shape, 'circle')
            }

            dependencies {
                attributesSchema {
                    attribute(color)
                    attribute(shape)
                }
                conf 'org:test:[1.0,)'
            }
        """

        when:
        repositoryInteractions {
            'org:test' {
                expectVersionListing()
                '1.1' {
                    expectGetMetadata()
                }
                '1.0' {
                    expectGetMetadata()
                }
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("""Could not find any version that matches org:test:[1.0,).
Versions rejected by attribute matching:
  - 1.1:
      - Attribute 'color' didn't match. Requested 'green', was: 'blue'
      - Attribute 'shape' matched. Requested 'circle', was: 'circle'
  - 1.0:
      - Attribute 'color' didn't match. Requested 'green', was: 'red'
      - Attribute 'shape' didn't match. Requested 'circle', was: 'square'""")
    }

    static Closure<String> latestNotation() {
        { -> GradleMetadataResolveRunner.useIvy() ? "latest.integration" : "latest.release" }
    }

}
