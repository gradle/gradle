/*
 * Copyright 2011 the original author or authors.
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
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest
import spock.lang.Issue

@FluidDependenciesResolveTest
class DependencyResolutionEventsIntegrationTest extends AbstractIntegrationSpec {
    def "fires exactly one beforeResolve and afterResolve event when configuration is resolved"() {
        given:
        buildFile << """
            configurations {
                parent { }
                things.extendsFrom parent
                all {
                    incoming.beforeResolve { c -> println "before " + c.path }
                    incoming.afterResolve { c -> println "after " + c.path }
                }
            }
            dependencies {
                parent files("parent.txt")
                things files("thing.txt")
            }

            task resolveIt(type: Sync) {
                from configurations.things
                into buildDir
            }
        """

        when:
        run "resolveIt"

        then:
        output.count("before :things") == 1
        output.count("after :things") == 1
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2047")
    def "can access resolved files from afterResolve hook"() {
        given:
        file("thing.txt") << "stuff"
        buildFile << """
            configurations {
                things.incoming.afterResolve { incoming ->
                    incoming.files.files
                    println "accessed files"
                }
            }
            dependencies {
                things files("thing.txt")
            }

            task resolveIt(type: Sync) {
                from configurations.things
                into buildDir
            }
        """

        when:
        run "resolveIt"

        then:
        output.contains "accessed files"
    }

}
