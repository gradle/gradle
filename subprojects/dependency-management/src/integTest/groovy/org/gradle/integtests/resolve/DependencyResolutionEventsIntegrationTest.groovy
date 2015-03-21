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
import spock.lang.Issue

class DependencyResolutionEventsIntegrationTest extends AbstractIntegrationSpec {

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

            task resolveIt(type: Copy, dependsOn: configurations.things) {
                from configurations.things
                into buildDir
            }
        """

        when:
        run "resolveIt"

        then:
        output.contains "accessed files"
    }

    def "listeners are called in parent->child order during resolution of child configuration"() {
        given:
        buildFile << """
            configurations {
                grandParent
                parent { extendsFrom grandParent }
                things { extendsFrom parent }
            }
            configurations.each { conf ->
                conf.incoming.beforeObserve { incoming ->
                    println "before observe \$conf"
                }
                conf.incoming.beforeResolve { incoming ->
                    println "before resolve \$conf"
                }
                conf.incoming.afterResolve { incoming ->
                    println "after resolve \$conf"
                }
            }
            configurations.things.resolve()
        """

        when: succeeds()
        then: output.contains(
"""before resolve configuration ':things'
before observe configuration ':grandParent'
before observe configuration ':parent'
before observe configuration ':things'
after resolve configuration ':things'""")
    }

    def "listeners are called on dependency project's configurations"() {
        given:
        settingsFile << 'include "api", "impl"'
        buildFile << """
            allprojects {
                apply plugin: "java"
                configurations.all { conf ->
                    conf.incoming.beforeObserve { incoming ->
                        println "before observe \$conf"
                    }
                    conf.incoming.beforeResolve { incoming ->
                        println "before resolve \$conf"
                    }
                    conf.incoming.afterResolve { incoming ->
                        println "after resolve \$conf"
                    }
                }
            }
            project(":impl") {
                dependencies {
                    compile project(":api")
                }
            }

            dependencies {
                compile project(":impl")
            }
            configurations.compile.resolve()
        """

        when: succeeds("dependencies", "--configuration", "compile")
        then: output.contains(
"""before resolve configuration ':compile'
before observe configuration ':compile'
before observe configuration ':impl:compile'
before observe configuration ':impl:runtime'
before observe configuration ':impl:default'
before observe configuration ':api:compile'
before observe configuration ':api:runtime'
before observe configuration ':api:default'
after resolve configuration ':compile'""")
    }

}
