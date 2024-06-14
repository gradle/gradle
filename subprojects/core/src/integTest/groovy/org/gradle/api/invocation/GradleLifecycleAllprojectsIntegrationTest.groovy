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

    def 'gradle.lifecycle.allprojects is applying before gradle.#api'() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            gradle.lifecycle.allprojects { project ->
                println "lifecycle.allprojects for \${project.name}"
            }
            gradle.$invocation { project ->
                println "$api for \${project.name}"
            }
            include(":a")
        """

        file("a/build.gradle") << "println ':a'"

        buildFile << """
            println 'Root'
        """

        when:
        run "help", "-q"

        then:
        outputContains expectedOutput
        where:
        api           | invocation    | expectedOutput
        "allprojects" | "allprojects" | gradleAllprojectsExpectedOutput
        "rootProject" | "rootProject" | gradleRootProjectExpectedOutput
    }

    private static def gradleAllprojectsExpectedOutput = """
lifecycle.allprojects for root
allprojects for root
lifecycle.allprojects for a
allprojects for a
Root
:a
"""
    private static def gradleRootProjectExpectedOutput = """
lifecycle.allprojects for root
rootProject for root
Root
"""

    def 'gradle.lifecycle.allprojects is applying only once'() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            gradle.lifecycle.allprojects { project ->
                println "lifecycle.allprojects for \${project.name}"
            }
            include(":a")
        """

        file("a/build.gradle") << "println ':a'"

        buildFile << """
            println 'Root'
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
Root
allprojects for root
lifecycle.allprojects for a
allprojects for a
subprojects for a
:a
"""
    }

    def 'gradle.lifecycle.allprojects is applying before gradle.lifecycle.beforeProject'() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            gradle.lifecycle.allprojects { project ->
                println "lifecycle.allprojects for \${project.name}"
            }
            gradle.lifecycle.beforeProject { project ->
                println "lifecycle.beforeProject for \${project.name}"
            }
            include(":a")
        """
        file("a/build.gradle") << "println ':a'"

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

    def 'gradle.lifecycle.allprojects is applying before project.#api'() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            gradle.lifecycle.allprojects { project ->
                println "lifecycle.allprojects for \${project.name}"
            }
            include(":a")
            include(":b")
        """

        file("a/build.gradle") << "println ':a'"
        file("b/build.gradle") << "println ':b'"

        buildFile << """
            println 'Root'
            $invocation { project ->
                println "$api for \${project.name}"
            }
        """

        when:
        run "help", "-q"

        then:
        outputContains expectedOutput

        where:
        api           | invocation      | expectedOutput
        "allprojects" | "allprojects"   | allprojectsExpectedOutput
        "subprojects" | "subprojects"   | subprojectsExpectedOutput
        "project"     | "project(':a')" | projectExpectedOutput
    }

    private static def allprojectsExpectedOutput = """
lifecycle.allprojects for root
Root
allprojects for root
lifecycle.allprojects for a
allprojects for a
lifecycle.allprojects for b
allprojects for b
:a
:b
"""
    private static def subprojectsExpectedOutput = """
lifecycle.allprojects for root
Root
lifecycle.allprojects for a
subprojects for a
lifecycle.allprojects for b
subprojects for b
:a
:b
"""
    private static def projectExpectedOutput = """
lifecycle.allprojects for root
Root
lifecycle.allprojects for a
project for a
:a
lifecycle.allprojects for b
:b
"""
}
