/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.invocation

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class GradleLifecycleAllprojectsIntegrationTest extends AbstractIntegrationSpec {

    def 'lifecycle.allprojects is executed only once for a project'() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            gradle.lifecycle.allprojects { project ->
                println "lifecycle.allprojects for \${project.name}"
            }
            include(":a")
        """

        file("a/build.gradle") << "println 'a'"

        buildFile << """
            allprojects { project ->
                println "allprojects for \${project.name}"
            }
            subprojects { project ->
                println "subprojects for \${project.name}"
            }
        """

        when:
        run "help", "-q"

        then:
        outputContains """
lifecycle.allprojects for root
allprojects for root
lifecycle.allprojects for a
allprojects for a
subprojects for a
a
"""
    }

    def 'lifecycle.allprojects is executed before gradle.lifecycle.beforeProject'() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            gradle.lifecycle.beforeProject { project ->
                println "lifecycle.beforeProject for \${project.name}"
            }
            gradle.lifecycle.allprojects { project ->
                println "lifecycle.allprojects for \${project.name}"
            }
            include(":a")
        """
        file("a/build.gradle") << ""

        when:
        run "help", "-q"

        then:
        outputContains """
lifecycle.allprojects for root
lifecycle.beforeProject for root
lifecycle.allprojects for a
lifecycle.beforeProject for a
"""
    }

    def 'lifecycle.allprojects is executed before project.#api'() {
        given:
        settingsFile << """
            rootProject.name = 'root'

            gradle.lifecycle.allprojects { project ->
                project.ext {
                    foo = "bar"
                }
            }
            include(":a")
            include(":b")
        """

        file("a/build.gradle") << ""
        file("b/build.gradle") << ""

        buildFile << """
            $invocation { project ->
                println "foo = \${project.foo} for \${project.name}"
            }
        """

        when:
        run "help", "-q"

        then:
        outputContains expectedOutput

        where:
        api              | invocation                 | expectedOutput
        "allprojects"    | "allprojects"              | allprojectsExpectedOutput
        "subprojects"    | "subprojects"              | subprojectsExpectedOutput
        "project"        | "project(':a')"            | projectExpectedOutput
        "getAllprojects" | "getAllprojects().forEach" | getAllprojectsExpectedOutput
        "getSubprojects" | "getSubprojects().forEach" | getSubprojectsExpectedOutput
    }

    private static def allprojectsExpectedOutput = """
foo = bar for root
foo = bar for a
foo = bar for b
"""
    private static def subprojectsExpectedOutput = """
foo = bar for a
foo = bar for b
"""
    private static def projectExpectedOutput = """
foo = bar for a
"""
    private static def getAllprojectsExpectedOutput = """
foo = bar for root
foo = bar for a
foo = bar for b
"""
    private static def getSubprojectsExpectedOutput = """
foo = bar for a
foo = bar for b
"""

    def 'lifecycle.allprojects is executed eagerly only if a mutable state of a project touched by using project.#api'() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            gradle.lifecycle.allprojects { project ->
                println "lifecycle.allprojects for \${project.name}"
                project.ext {
                    foo = "bar"
                }
            }
            include(":a")
            include(":b")
        """

        file("a/build.gradle") << "println 'a'"
        file("b/build.gradle") << """
            println 'b'
            println "foo = \${foo} for b"
        """

        buildFile << """
            println 'root'
            $invocation { project ->
                if (project.getName() == 'a') {
                    println "Mutable state access for 'a': foo = \${project.foo}"
                } else {
                    println "Immutable state access for \${project.getName()}"
                }
            }
            println "$api is executed"
        """

        when:
        run "help", "-q"

        then:
        outputContains expectedOutput

        where:
        api              | invocation                 | expectedOutput
        "allprojects"    | "allprojects"              | mutableStateAccessProjectAllprojectsExpectedOutput
        "subprojects"    | "subprojects"              | mutableStateAccessProjectSubprojectsExpectedOutput
        "getAllprojects" | "getAllprojects().forEach" | mutableStateAccessProjectGetAllprojectsExpectedOutput
        "getSubprojects" | "getSubprojects().forEach" | mutableStateAccessProjectGetSubprojectsExpectedOutput
    }

    private static def mutableStateAccessProjectAllprojectsExpectedOutput = """
lifecycle.allprojects for root
root
Immutable state access for root
lifecycle.allprojects for a
Mutable state access for 'a': foo = bar
Immutable state access for b
allprojects is executed
a
lifecycle.allprojects for b
b
foo = bar for b
"""
    private static def mutableStateAccessProjectSubprojectsExpectedOutput = """
lifecycle.allprojects for root
root
lifecycle.allprojects for a
Mutable state access for 'a': foo = bar
Immutable state access for b
subprojects is executed
a
lifecycle.allprojects for b
b
foo = bar for b
"""
    private static def mutableStateAccessProjectGetAllprojectsExpectedOutput = """
lifecycle.allprojects for root
root
Immutable state access for root
lifecycle.allprojects for a
Mutable state access for 'a': foo = bar
Immutable state access for b
getAllprojects is executed
a
lifecycle.allprojects for b
b
foo = bar for b
"""
    private static def mutableStateAccessProjectGetSubprojectsExpectedOutput = """
lifecycle.allprojects for root
root
lifecycle.allprojects for a
Mutable state access for 'a': foo = bar
Immutable state access for b
getSubprojects is executed
a
lifecycle.allprojects for b
b
foo = bar for b
"""

    def 'lifecycle.allprojects is executed eagerly only if a mutable state of a project touched by using gradle.allprojects'() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            gradle.lifecycle.allprojects { project ->
                println "lifecycle.allprojects for \${project.getName()}"
                project.ext {
                    foo = "bar"
                }
            }
            gradle.allprojects { project ->
                if (project.getName() == 'a') {
                    println "Mutable state access for 'a': foo = \${project.foo}"
                } else {
                    println "Immutable state access for \${project.getName()}"
                }
            }
            include(":a")
        """

        file("a/build.gradle") << "println 'a'"

        buildFile << """
            println 'root'
        """

        when:
        run "help", "-q"

        then:
        outputContains """
Immutable state access for root
lifecycle.allprojects for a
Mutable state access for 'a': foo = bar
lifecycle.allprojects for root
root
a
"""
    }

//    @ToBeImplemented
//    def 'lifecycle.allprojects is executed eagerly only if a mutable state of a project touched by using gradle.rootProject'() {
//        given:
//        settingsFile << """
//            rootProject.name = 'root'
//            gradle.lifecycle.allprojects { project ->
//                println "lifecycle.allprojects for \${project.getName()}"
//                project.ext {
//                    foo = "bar"
//                }
//            }
//            gradle.rootProject { project ->
//                "State access for root: \${project.$stateAccess}"
//            }
//        """
//
//        when:
//        run "help", "-q"
//
//        then:
//        outputContains ""
//        where:
//        stateAccess | expectedOutput
//        "foo"       | ""
//        "getName()" | ""
//    }
}
