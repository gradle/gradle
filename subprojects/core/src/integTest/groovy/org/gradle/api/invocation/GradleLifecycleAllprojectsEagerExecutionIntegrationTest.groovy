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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@Requires(IntegTestPreconditions.NotIsolatedProjects)
class GradleLifecycleAllprojectsEagerExecutionIntegrationTest extends AbstractIntegrationSpec {

    def 'lifecycle.allproject is executed eagerly when triggered in #dsl DSL'() {
        settingsFile << """
            gradle.lifecycle.allprojects {
                println "lifecycle.allprojects: \${name}"
            }
            include(":a")
        """
        file("a/build.gradle") << ""
        file(buildFileName) << """
            project(":a") { $projectReceiver
               println("before")
               $mutableStateAccess
               println("after")
            }
        """

        when:
        run "help", "-q"

        then:
        outputContains "before\nlifecycle.allprojects: a\nafter"

        where:
        dsl      | buildFileName      | mutableStateAccess | projectReceiver
        "Kotlin" | "build.gradle.kts" | "version"          | ""
        "Groovy" | "build.gradle"     | "project.version"  | "project ->"
        "Groovy" | "build.gradle"     | "version"          | ""
    }

    def 'lifecycle.allprojects is executed lazily before project evaluation if immutable state accessed'() {
        given:
        settingsFile << """
            gradle.lifecycle.allprojects {
                println "lifecycle.allprojects: \${name}"
            }
            include(":a")
        """
        file("a/build.gradle") << "println('evaluate a')"
        buildFile << """
            project(":a") {
               println("eager \$name")
            }
        """

        when:
        run "help", "-q"

        then:
        outputContains "eager a\nlifecycle.allprojects: a\nevaluate a"
    }

    def 'lifecycle.allprojects is executed eagerly before mutable state access'() {
        given:
        settingsFile << """
            gradle.lifecycle.allprojects {
                println "lifecycle.allprojects: \${name}"
            }
            include(":a")
        """
        file("a/build.gradle") << ""
        buildFile << """
            project(":a") {
                println("before")
                $mutableStateAccess
                println("after")
            }
        """

        when:
        run "help", "-q"

        then:
        outputContains "before\nlifecycle.allprojects: a\nafter"

        where:
        mutableStateAccess << [
            "buildDir",
            "version",
            "description",
            "status",
            "tasks",
            "getAllTasks(true)",
            "getTasksByName('foo', true)",
            "task('foo')",
            "defaultTasks",
            "logger",
            "layout",
            "logging",
            "repositories",
            "dependencies",
            "state",
            "extensions",
            "buildscript",
            "configurations",
            "components",
            "artifacts",
            "convention",
            "plugins",
            "pluginManager",
            "findProperty('foo')"
        ]
    }

    def 'lifecycle.allprojects is executed only once for a project'() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            gradle.lifecycle.allprojects {
                println "lifecycle.allprojects for \${name}"
            }
            include(":a")
        """

        file("a/build.gradle") << ""

        buildFile << """
            allprojects { $projectMutableStateAccess }
            $secondEagerExecutionBlock
        """

        when:
        run "help", "-q"

        then:
        output.count("lifecycle.allprojects for root") == 1
        output.count("lifecycle.allprojects for a") == 1

        where:
        secondEagerExecutionBlock << [
            "subprojects { $projectMutableStateAccess }",
            "afterEvaluate { allprojects { $projectMutableStateAccess } }"
        ]
    }

    def 'lifecycle.allprojects can be executed before project.#api'() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            gradle.lifecycle.allprojects {
                ext.foo = "bar"
            }
            include(":a")
            include(":b")
        """

        file("a/build.gradle") << ""
        file("b/build.gradle") << ""

        buildFile << """
            $invocation { project ->
                println "\${project.name} foo=\${project.foo}"
            }
        """

        when:
        run "help", "-q"

        then:
        outputContains expectedOutput

        where:
        api                | invocation                            | expectedOutput
        "allprojects"      | "allprojects"                         | "root foo=bar\na foo=bar\nb foo=bar"
        "subprojects"      | "subprojects"                         | "a foo=bar\nb foo=bar"
        "project"          | "project(':a')"                       | "a foo=bar"
        "findProject"      | "configure(findProject(':a'))"        | "a foo=bar"
        "getAllprojects"   | "getAllprojects().forEach"            | "root foo=bar\na foo=bar\nb foo=bar"
        "getSubprojects"   | "getSubprojects().forEach"            | "a foo=bar\nb foo=bar"
        "getChildProjects" | "getChildProjects().values().forEach" | "a foo=bar\nb foo=bar"
    }

    def 'lifecycle.allprojects can be executed before gradle.allprojects'() {
        settingsFile << """
            rootProject.name = 'root'
            gradle.allprojects {
                println "\${name} foo=\${foo}"
            }
            gradle.lifecycle.allprojects {
                ext.foo = "bar"
            }
            include(":a")
            include(":b")
        """

        file("a/build.gradle") << ""
        file("b/build.gradle") << ""

        when:
        run "help", "-q"

        then:
        outputContains "root foo=bar\na foo=bar\nb foo=bar"
    }

    static def projectMutableStateAccess = "version"
}
