/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest

class LazyDownloadsIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def module = mavenHttpRepo.module("test", "test", "1.0").publish()
    def module2 = mavenHttpRepo.module("test", "test2", "1.0").publish()

    def setup() {
        settingsFile << "include 'child'"
        buildFile << """
            allprojects {
                repositories {
                    maven { url '$mavenHttpRepo.uri' }
                }
                configurations {
                    compile
                    create('default').extendsFrom compile
                }
            }

            dependencies {
                compile project(':child')
            }
            project(':child') {
                dependencies {
                    compile 'test:test:1.0'
                    compile 'test:test2:1.0'
                }
            }
"""
    }

    def "downloads only the metadata when dependency graph is queried"() {
        given:
        buildFile << """
            task graph {
                def root = configurations.compile.incoming.resolutionResult.rootComponent
                doLast {
                    root.get()
                }
            }
"""

        when:
        module.pom.expectGet()
        module2.pom.expectGet()

        then:
        succeeds("graph")
    }

    def "downloads only the metadata when resolved dependencies are queried"() {
        given:
        buildFile << """
            task artifacts {
                def result = configurations.compile.incoming.resolutionResult.rootComponent
                doLast {
                    println result.get().dependents
                }
            }
        """

        when:
        module.pom.expectGet()
        module2.pom.expectGet()

        then:
        succeeds("artifacts")
    }

    def "downloads only the metadata on failure to resolve the graph as files"() {
        given:
        buildFile << """
            task artifacts {
                def compile = configurations.compile
                doLast {
                    // cause resolution
                    compile.files*.name
                }
            }
"""

        when:
        module.pom.expectGetUnauthorized()
        module2.pom.expectGet()

        then:
        fails("artifacts")
        failure.assertResolutionFailure(":compile")
        failure.assertHasCause("Could not resolve test:test:1.0.")
    }

    def "downloads only the metadata on failure to resolve the graph as artifact collection"() {
        given:
        buildFile << """
            task artifacts {
                def result = configurations.compile.incoming.artifacts
                doLast {
                    // cause resolution
                    result*.id
                }
            }
"""

        when:
        module.pom.expectGetUnauthorized()
        module2.pom.expectGet()

        then:
        fails("artifacts")
        failure.assertResolutionFailure(":compile")
        failure.assertHasCause("Could not resolve test:test:1.0.")
    }
}
