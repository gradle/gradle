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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class CapabilitiesLocalComponentIntegrationTest extends AbstractIntegrationSpec {

    def "can detect conflict between local projects providing the same capability"() {
        given:
        settingsFile << """
            rootProject.name = 'test'
            include 'b'
        """
        buildFile << """
            apply plugin: 'java-library'

            configurations.api.outgoing {
                capability 'org:capability:1.0'
            }

            dependencies {
                api project(":b")
            }

        """
        file('b/build.gradle') << """
            apply plugin: 'java-library'

            configurations.api.outgoing {
                capability 'test:b:unspecified'
                capability group:'org', name:'capability', version:'1.0'
            }
        """

        when:
        fails 'compileJava'

        then:
        failure.assertHasCause("""Module 'test:b' has been rejected:
   Cannot select module with conflict on capability 'org:capability:1.0' also provided by [:test:unspecified(compileClasspath)]""")
    }

    def 'fails to resolve undeclared test fixture'() {
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation(testFixtures(project(':')))
            }

            task resolve {
                doLast {
                    println configurations.compileClasspath.incoming.files.files
                }
            }
"""

        when:
        succeeds 'dependencyInsight', '--configuration', 'compileClasspath', '--dependency', ':'

        then:
        outputContains("Could not resolve root project :.")
    }

    def "can lazily define and request capability"() {
        buildFile << """

            def value = "org:initial:1.0"

            configurations {
                consumable("conf") {
                    outgoing {
                        capability(project.provider(() -> value))
                    }
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, "foo"))
                }
                dependencyScope("deps")
                resolvable("res") {
                    extendsFrom(deps)
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, "foo"))
                }
            }

            dependencies {
                deps(project) {
                    capabilities {
                        requireCapability(project.provider(() -> value))
                    }
                }
            }

            value = "com:final:1.0"

            task resolve {
                def result = configurations.res.incoming.resolutionResult.rootComponent
                doLast {
                    def capabilities = result.get().dependencies.first().resolvedVariant.capabilities
                    assert capabilities.size() == 1
                    assert capabilities.first().group == "com"
                    assert capabilities.first().name == "final"
                }
            }
        """

        expect:
        succeeds("resolve")
    }

    def "error when multiple capability selectors do not match includes both selectors"() {
        settingsFile << """
            include("other")
        """
        file("other/build.gradle") << """
            plugins {
                id("java-library")
            }
        """
        buildFile << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation(project(":other")) {
                    capabilities {
                        requireCapability("org:capability:1.0")
                        requireFeature("foo")
                    }
                }
            }

            task resolve {
                def files = configurations.runtimeClasspath.incoming.files
                doLast {
                    println(files*.name)
                }
            }
        """

        when:
        fails("resolve")

        then:
        failure.assertHasCause("Unable to find a variant with the requested capabilities: [coordinates 'org:capability', feature 'foo']")
    }

    @Issue("https://github.com/gradle/gradle/issues/26377")
    def "ResolvedVariantResults reported by ResolutionResult and ArtifactCollection have same capabilities when they are added to configuration in hierarchy"() {
        settingsFile << "include 'producer'"
        file("producer/build.gradle") << """
            group="com.foo"

            task zip(type: Zip) {
                archiveFileName = "producer.zip"
                destinationDirectory = layout.buildDirectory
            }

            configurations {
                dependencyScope("api") {
                    outgoing {
                        capability("com.foo:producer:2.0")
                        capability("org.bar:dependency-scope-capability:1.0")
                    }
                }
                consumable("elements") {
                    extendsFrom(api)
                    outgoing.artifact(tasks.zip)
                    outgoing.capability("org.bar:consumable-capability:1.0")
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.LIBRARY))
                    }
                }
            }
        """

        buildFile << """
            configurations {
                dependencyScope("implementation")
                resolvable("classpath") {
                    extendsFrom(implementation)
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.LIBRARY))
                    }
                }
            }

            dependencies {
                implementation(project(":producer"))
            }

            task resolve {
                def conf = configurations.classpath
                def root = conf.incoming.resolutionResult.root
                def artifactVariants = conf.incoming.artifacts.artifacts.collect { it.variant }
                doLast {
                    def graphVariant = root.dependencies.find { it.selected.id.projectPath == ":producer" }.resolvedVariant
                    def artifactVariant = artifactVariants.find { it.owner.projectPath == ":producer" }
                    assert graphVariant.capabilities == artifactVariant.capabilities

                    def expected = ["com.foo:producer:2.0", "org.bar:dependency-scope-capability:1.0", "org.bar:consumable-capability:1.0"] as Set
                    assert graphVariant.capabilities.collect { "\${it.group}:\${it.name}:\${it.version}" } as Set == expected
                }
            }
        """

        expect:
        succeeds("resolve")
    }

    def "can request capability without version"() {
        settingsFile << """
            include("other")
        """
        file("other/build.gradle") << """
            configurations {
                consumable("apiElements") {
                    outgoing {
                        artifact(file("foo.txt"))
                        capability("org:capability:1.0")
                    }
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.LIBRARY))
                    }
                }
            }
        """
        buildFile << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation(project(":other")) {
                    capabilities {
                        requireCapability("org:capability")
                    }
                }
            }

            task resolve {
                def files = configurations.runtimeClasspath.incoming.files
                doLast {
                    assert files*.name == ["foo.txt"]
                }
            }
        """

        expect:
        succeeds("resolve")
    }
}
