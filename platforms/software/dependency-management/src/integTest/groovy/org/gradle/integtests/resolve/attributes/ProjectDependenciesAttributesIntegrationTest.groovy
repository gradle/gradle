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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.internal.component.ResolutionFailureHandler

class ProjectDependenciesAttributesIntegrationTest extends AbstractIntegrationSpec {

    ResolveTestFixture resolve = new ResolveTestFixture(buildFile, 'conf')

    def setup() {
        buildFile << """
            configurations {
               conf
            }
        """
        settingsFile << """
            rootProject.name = 'test'
        """
        resolve.prepare()
    }

    def "uses dependency attributes to select the right configuration on the target project (color=#color)"() {
        given:
        settingsFile << "include 'dep'"
        buildFile << """
            dependencies {
                conf(project(':dep')) {
                    attributes {
                        attribute(Attribute.of('color', String), '$color')
                    }
                }
            }
        """
        file("dep/build.gradle") << blueAndRedVariants()

        when:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                project(':dep', "test:dep:unspecified") {
                    variant "${color}Variant", [color: color]
                    noArtifacts()
                }
            }
        }

        where:
        color << ['blue', 'red']
    }

    def "fails with reasonable error message when no target variant can be found"() {
        given:
        settingsFile << "include 'dep'"
        buildFile << """
            dependencies {
                conf(project(':dep')) {
                    attributes {
                        attribute(Attribute.of('color', String), 'green')
                    }
                }
            }
        """
        file("dep/build.gradle") << blueAndRedVariants()

        file("gradle.properties").text = "${ResolutionFailureHandler.FULL_FAILURES_MESSAGE_PROPERTY}=true"

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause("""No matching variant of project :dep was found. The consumer was configured to find attribute 'color' with value 'green' but:
  - Variant 'blueVariant' capability test:dep:unspecified:
      - Incompatible because this component declares attribute 'color' with value 'blue' and the consumer needed attribute 'color' with value 'green'
  - Variant 'redVariant' capability test:dep:unspecified:
      - Incompatible because this component declares attribute 'color' with value 'red' and the consumer needed attribute 'color' with value 'green'""")
    }

    def "dependency attributes override configuration attributes"() {
        given:
        settingsFile << "include 'dep'"
        buildFile << """
            configurations {
                conf {
                    attributes {
                        attribute(Attribute.of('color', String), 'blue')
                    }
                }
            }
            dependencies {
                conf(project(':dep')) {
                    attributes {
                        attribute(Attribute.of('color', String), 'red')
                    }
                }
            }
        """
        file("dep/build.gradle") << blueAndRedVariants()

        when:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                project(':dep', "test:dep:unspecified") {
                    variant "redVariant", [color: 'red']
                    noArtifacts()
                }
            }
        }

    }

    private static String blueAndRedVariants() {
        """
            configurations {
                blueVariant {
                    canBeResolved = false
                    assert canBeConsumed
                    attributes {
                        attribute(Attribute.of('color', String), 'blue')
                    }
                }
                redVariant {
                    canBeResolved = false
                    assert canBeConsumed
                    attributes {
                        attribute(Attribute.of('color', String), 'red')
                    }
                }
            }
        """
    }
}
