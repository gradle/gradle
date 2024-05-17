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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class AttributeContainerResolutionIntegrationTest extends AbstractIntegrationSpec {
    @Issue("https://github.com/gradle/gradle/issues/26298")
    def "lazy attributes provided to a Configuration do not fail resolution when they have side effects"() {
        buildFile << """
            configurations {
                conf {
                    attributes {
                        def otherAttribute = Attribute.of("zzz", Named)
                        attributeProvider(Attribute.of("aaa", Named), provider {
                            assert getAttribute(otherAttribute).name == "other"
                            objects.named(Named, "value")
                        })
                        attributeProvider(otherAttribute, provider {
                            objects.named(Named, "other")
                        })
                    }
                }
            }

            task resolve {
                inputs.files(configurations.conf)
            }
        """
        expect:
        // When this has failed in the past, building the task graph hits a NPE from the attribute container
        succeeds("resolve")
    }
}
