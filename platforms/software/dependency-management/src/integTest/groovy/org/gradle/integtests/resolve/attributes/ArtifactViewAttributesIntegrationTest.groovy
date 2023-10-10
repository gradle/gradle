/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Test that ArtifactView has a "live" view of the underlying configuration's attributes.
 */
class ArtifactViewAttributesIntegrationTest extends AbstractIntegrationSpec {
    def "artifact view has live view of underlying configuration attributes"() {
        buildFile << """
            def usage = Attribute.of('usage', String)
            def buildType = Attribute.of('buildType', String)
            def flavor = Attribute.of('flavor', String)
            configurations {
                conf {
                    attributes {
                        attribute(usage, 'usage-value')
                    }
                }
            }

            def view = configurations.conf.incoming.artifactView {
                attributes {
                    attribute(buildType, 'buildType-value')
                }
            }

            configurations {
                conf {
                    attributes {
                        attribute(flavor, 'flavor-value')
                    }
                }
            }

            def map = [:]
            view.attributes.keySet().each { key ->
                map[key.name] = view.attributes.getAttribute(key)
            }
            assert map.size() == 3
            assert map['usage'] == 'usage-value'
            assert map['buildType'] == 'buildType-value'
            assert map['flavor'] == 'flavor-value'
        """

        expect:
        succeeds 'help'
    }
}
