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

class ProjectDependenciesAttributesIntegrationTest extends AbstractIntegrationSpec {

    ResolveTestFixture resolve = new ResolveTestFixture(buildFile, 'res')

    def setup() {
        buildFile << """
            configurations {
                dependencyScope("conf")
                resolvable("res") {
                    extendsFrom(conf)
                }
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

    def "Fails with reasonable error message when no target variant can be found"() {
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

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause("""No matching variant of project :dep was found. The consumer was configured to find attribute 'color' with value 'green' but:
  - Variant 'blueVariant':
      - Incompatible because this component declares attribute 'color' with value 'blue' and the consumer needed attribute 'color' with value 'green'
  - Variant 'redVariant':
      - Incompatible because this component declares attribute 'color' with value 'red' and the consumer needed attribute 'color' with value 'green'""")
    }

    def "dependency attributes override configuration attributes"() {
        given:
        settingsFile << "include 'dep'"
        buildFile << """
            configurations {
                res {
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

    def "multiple nodes in a target component may be selected when only the producing component has attribute rules marking their attributes compatible"() {
        settingsFile << """include 'producer'"""

        file("producer/build.gradle") << """
            class AllCompatibilityRule implements AttributeCompatibilityRule<Object> {
                @Override
                public void execute(CompatibilityCheckDetails<Object> details) {
                    details.compatible()
                }
            }

            dependencies {
                attributesSchema {
                    attribute(Attribute.of("color", String)) {
                        if (${applyRule}) {
                            compatibilityRules.add(AllCompatibilityRule)
                        }
                    }
                }
            }

            ${blueAndRedVariants()}

            configurations {
                blueVariant {
                    outgoing {
                        capability("org:special:1.0")
                    }
                }
            }
        """

        buildFile << """
            dependencies {
                conf(project(":producer"))
                conf(project(":producer")) {
                    capabilities {
                        requireCapability("org:special:1.0")
                    }
                }
            }
        """

        when:
        if (applyRule) {
            run ':checkDeps'
        } else {
            fails ':checkDeps'
        }

        then:
        if (applyRule) {
            resolve.expectGraph {
                root(":", ":test:") {
                    project(':producer', "test:producer:unspecified") {
                        variant "redVariant", [color: 'red']
                        noArtifacts()
                    }
                    project(':producer', "test:producer:unspecified") {
                        variant "blueVariant", [color: 'blue']
                        noArtifacts()
                    }
                }
            }
        } else {
            failure.assertHasCause("""Multiple incompatible variants of test:producer:unspecified were selected:
   - Variant blueVariant has attributes {color=blue}
   - Variant redVariant has attributes {color=red}""")
        }

        where:
        applyRule << [true, false]
    }

    private static String blueAndRedVariants() {
        """
            configurations {
                consumable("blueVariant") {
                    attributes {
                        attribute(Attribute.of('color', String), 'blue')
                    }
                }
                consumable("redVariant") {
                    attributes {
                        attribute(Attribute.of('color', String), 'red')
                    }
                }
            }
        """
    }
}
