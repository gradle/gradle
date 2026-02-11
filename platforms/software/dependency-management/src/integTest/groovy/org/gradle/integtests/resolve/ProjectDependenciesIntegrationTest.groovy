/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest
import spock.lang.Issue

@FluidDependenciesResolveTest
class ProjectDependenciesIntegrationTest extends AbstractDependencyResolutionTest {

    @Issue("GRADLE-2477") //this is a feature on its own but also covers one of the reported issues
    def "resolving project dependency triggers configuration of the target project"() {
        settingsFile << "include 'impl'"
        buildFile << """
            apply plugin: 'java'
            dependencies {
                implementation project(":impl")
            }
            repositories {
                //resolving project must declare the repo
                maven { url = '${mavenRepo.uri}' }
            }
            println "Resolved at configuration time: " + configurations.runtimeClasspath.files*.name
        """

        mavenRepo.module("org", "foo").publish()
        file("impl/build.gradle") << """
            apply plugin: 'java'
            dependencies {
                implementation "org:foo:1.0"
            }
        """

        when:
        succeeds()

        then:
        outputContains "Resolved at configuration time: [impl.jar, foo-1.0.jar]"
    }

    def "configuring project dependencies by map is validated for #description"() {
        given:
        settingsFile("include 'impl'")
        buildFile("""
            configurations {
                def deps = dependencyScope('deps')
                resolvable('conf') {
                    extendsFrom(deps)
                }
            }

            dependencies {
                deps($declaration)
            }
        """)
        buildFile("impl/build.gradle", """
            configurations.create('conf')
        """)

        when:
        if (expectedFailure) {
            runAndFail("dependencies")
        } else {
            succeeds("dependencies")
        }

        then:
        if (expectedFailure) {
            failureHasCause(expectedFailure)
        }

       where:
       description              || declaration                                                      || expectedFailure
       "extraKey"               || 'project(path: ":impl", configuration: ":conf", foo: "bar")'     || "Could not set unknown property 'foo' for "
       "missingConfiguration"   || 'project(path: ":impl")'                                         || null
       "missingPath"            || 'project(paths: ":impl", configuration: ":conf")'                || "Required keys [path] are missing from map"
    }

    @Issue("https://github.com/gradle/gradle/issues/34692")
    def "throws UnknownProjectException when creating project dependency from map with unknown project"() {
        buildFile("""
            configurations.dependencyScope("deps")
            dependencies {
                try {
                    deps(project(path: "unknown"))
                } catch (Exception e) {
                    assert e instanceof UnknownProjectException
                    throw e
                }
            }
        """)

        when:
        fails("help")

        then:
        failure.assertHasCause("Project with path ':unknown' could not be found.")
    }

    def "can add constraint on root project"() {
        given:
        mavenRepo.module("org", "foo").publish()
        buildFile << """
            configurations {
                dependencyScope("deps")
                resolvable("res")  {
                    extendsFrom(deps)
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, named(Category, "foo"))
                    }
                }
                consumable("cons") {
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, named(Category, "foo"))
                    }
                }
            }

            ${mavenTestRepository()}

            dependencies {
                deps "org:foo:1.0"
                deps project(":")
                constraints {
                    deps project(":")
                }
            }

            task resolve {
                def files = configurations.res
                doLast {
                    assert files*.name == ["foo-1.0.jar"]
                }
            }
        """

        expect:
        succeeds("resolve")
    }
}
