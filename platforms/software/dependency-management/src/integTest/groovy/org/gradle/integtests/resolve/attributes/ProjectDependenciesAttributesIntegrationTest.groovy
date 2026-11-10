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
import spock.lang.Issue

class ProjectDependenciesAttributesIntegrationTest extends AbstractIntegrationSpec {

    ResolveTestFixture resolve = new ResolveTestFixture(testDirectory)

    def setup() {
        buildFile << """
            configurations {
                dependencyScope("conf")
                resolvable("res") {
                    extendsFrom(conf)
                }
            }

            ${resolve.configureProject("res")}
        """
        settingsFile << """
            rootProject.name = 'test'
        """
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
        failure.assertHasCause("""No matching variant of project ':dep' was found. The consumer was configured to find attribute 'color' with value 'green' but:
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

    @Issue("https://github.com/gradle/gradle/issues/29390")
    def "selects variants with different capabilities and different categories that were not requested"() {
        settingsFile << """
            include("producer")
        """

        file("producer/library.jar").text = "library"
        file("producer/bundle.zip").text = "bundle"
        file("producer/build.gradle") << """
            group = "org"

            configurations {
                consumable("libraryElements") {
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.LIBRARY))
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
                    }
                    outgoing {
                        capability("org:producer-dependencies:1.0")
                        artifact(file("library.jar"))
                    }
                }
                consumable("bundleElements") {
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "data"))
                    }
                    outgoing {
                        capability("org:producer-bundle:1.0")
                        artifact(file("bundle.zip"))
                    }
                }
            }
        """

        buildFile << """
            configurations.res.attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
                attribute(Attribute.of("distribution", String), "client")
            }

            dependencies {
                conf(project(":producer")) {
                    capabilities {
                        requireCapability("org:producer-dependencies")
                    }
                }
                conf(project(":producer")) {
                    capabilities {
                        requireCapability("org:producer-bundle")
                    }
                }
            }
        """

        when:
        run(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                project(":producer", "org:producer:unspecified") {
                    variant("libraryElements", ["org.gradle.category": "library", "org.gradle.usage": "java-api"])
                    artifact(name: "library", type: "jar", fileName: "library.jar")
                }
                project(":producer", "org:producer:unspecified") {
                    variant("bundleElements", ["org.gradle.category": "data"])
                    artifact(name: "bundle", type: "zip", fileName: "bundle.zip")
                }
            }
        }
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
