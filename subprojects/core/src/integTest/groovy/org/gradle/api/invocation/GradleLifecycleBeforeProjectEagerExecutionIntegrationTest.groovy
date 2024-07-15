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
class GradleLifecycleBeforeProjectEagerExecutionIntegrationTest extends AbstractIntegrationSpec {

    def 'lifecycle.beforeProject is executed eagerly when getProperty accessed in Groovy DSL'() {
        settingsFile << """
            gradle.lifecycle.beforeProject {
                ext.foo = "\$name bar"
            }
            include(":a")
        """
        file("a/build.gradle") << ""
        buildFile << """
            project(":a") { $projectReceiver
               println("before")
               $propertyAccess
               println("after")
            }
        """

        when:
        run "help", "-q"

        then:
        outputContains "before\na bar\nafter"

        where:
        propertyAccess         | projectReceiver
        "println(foo)"         | ""
        "println(project.foo)" | "project ->"
        "println(it.foo)"      | ""
    }

    def 'lifecycle.beforeProject is executed lazily before project evaluation if immutable state accessed'() {
        given:
        settingsFile << """
            gradle.lifecycle.beforeProject {
                println "lifecycle.beforeProject: \${name}"
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
        outputContains "eager a\nlifecycle.beforeProject: a\nevaluate a"
    }

    def 'lifecycle.beforeProject is executed eagerly before mutable state access'() {
        given:
        settingsFile << """
            gradle.lifecycle.beforeProject {
                println "lifecycle.beforeProject: \${name}"
            }
            include(":a")
        """
        file("a/build.gradle") << ""
        buildFile << """
            project(":a") {
                println("before")
                it.$mutableStateAccess
                println("after")
            }
        """

        when:
        run "help", "-q"

        then:
        outputContains "before\nlifecycle.beforeProject: a\nafter"

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
            "layout",
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
            "findProperty('foo')",
            "getProperties()",
            "hasProperty('foo')",
            "apply{}"
        ]
    }

    def 'lifecycle.beforeProject is executed only once for a project'() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            gradle.lifecycle.beforeProject {
                println "lifecycle.beforeProject for \${name}"
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
        output.count("lifecycle.beforeProject for root") == 1
        output.count("lifecycle.beforeProject for a") == 1

        where:
        secondEagerExecutionBlock << [
            "subprojects { $projectMutableStateAccess }",
            "afterEvaluate { allprojects { $projectMutableStateAccess } }"
        ]
    }

    def 'lifecycle.beforeProject can be executed before project.#api'() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            gradle.lifecycle.beforeProject {
                ext.foo = "\$name bar"
            }
            include(":a")
            include(":b")
        """

        file("a/build.gradle") << ""
        file("b/build.gradle") << ""

        buildFile << """
            $invocation { project ->
                println "\${project.foo}"
            }
        """

        when:
        run "help", "-q"

        then:
        outputContains expectedOutput

        where:
        api                | invocation                            | expectedOutput
        "allprojects"      | "allprojects"                         | "root bar\na bar\nb bar"
        "subprojects"      | "subprojects"                         | "a bar\nb bar"
        "project"          | "project(':a')"                       | "a bar"
        "findProject"      | "configure(findProject(':a'))"        | "a bar"
        "getAllprojects"   | "getAllprojects().forEach"            | "root bar\na bar\nb bar"
        "getSubprojects"   | "getSubprojects().forEach"            | "a bar\nb bar"
        "getChildProjects" | "getChildProjects().values().forEach" | "a bar\nb bar"
    }

    def 'lifecycle.beforeProject can be executed before gradle.allprojects'() {
        settingsFile << """
            rootProject.name = 'root'
            gradle.allprojects {
                println "\${foo}"
            }
            gradle.lifecycle.beforeProject {
                ext.foo = "\$name bar"
            }
            include(":a")
            include(":b")
        """

        file("a/build.gradle") << ""
        file("b/build.gradle") << ""

        when:
        run "help", "-q"

        then:
        outputContains "root bar\na bar\nb bar"
    }

    static def projectMutableStateAccess = "version"
}
