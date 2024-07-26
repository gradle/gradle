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
        settingsFile """
            gradle.lifecycle.beforeProject {
                ext.foo = "\$name bar"
            }
            include(":a")
        """
        file("a/build.gradle") << ""
        buildFile """
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
        settingsFile """
            gradle.lifecycle.beforeProject {
                println "lifecycle.beforeProject: \${name}"
            }
            include(":a")
        """
        file("a/build.gradle") << "println('evaluate a')"
        buildFile """
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
        settingsFile """
            gradle.lifecycle.beforeProject {
                println "lifecycle.beforeProject: \${name}"
            }
            include(":a")
        """
        file("a/build.gradle") << ""
        buildFile """
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
            "group",
            "version",
            "description",
            "status",
            "evaluate()",
            "beforeEvaluate{}",
            "afterEvaluate{}",
            "evaluationDependsOn(':a')",
            "evaluationDependsOnChildren()",
            "bindAllModelRules()",
            "prepareForRuleBasedPlugins()",
            "services",
            "serviceRegistryFactory",
            "configurationActions",
            "modelRegistry",
            "model",
            "fireDeferredConfiguration()",
            "addDeferredConfiguration{}",
            "normalization",
            "normalization{}",
            "dependencyLocking",
            "dependencyLocking{}",
            "tasks",
            "getAllTasks(true)",
            "getTasksByName('foo', true)",
            "task('foo')",
            "defaultTasks",
            "getAnt()",
            "ant{}",
            "createAntBuilder()",
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
            "getProperties()",
            "findProperty('foo')",
            "getProperties()",
            "hasProperty('foo')",
            "apply{}"
        ]
    }

    def 'lifecycle.beforeProject is executed only once for a project'() {
        given:
        settingsFile """
            rootProject.name = 'root'
            gradle.lifecycle.beforeProject {
                println "lifecycle.beforeProject for \${name}"
            }
            include(":a")
        """

        file("a/build.gradle") << ""

        buildFile """
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

    def 'lifecycle.beforeProject eager execution can be triggered from project.#api'() {
        given:
        settingsFile """
            rootProject.name = 'root'
            gradle.lifecycle.beforeProject {
                println "lifecycle :\$name"
                ext.foo = "bar :\$name"
            }
            include(":a")
            include(":b")
        """

        file("a/build.gradle") << ""
        file("b/build.gradle") << ""

        buildFile"""
            $invocation { project ->
                println "access :\${project.name}"
                println "\${project.foo}"
            }
        """

        when:
        run "help", "-q"

        then:
        outputContains expectedOutput

        where:
        api                | invocation                            | expectedOutput
        "allprojects"      | "allprojects"                         | "lifecycle :root\naccess :root\nbar :root\naccess :a\nlifecycle :a\nbar :a\naccess :b\nlifecycle :b\nbar :b"
        "subprojects"      | "subprojects"                         | "lifecycle :root\naccess :a\nlifecycle :a\nbar :a\naccess :b\nlifecycle :b\nbar :b"
        "project"          | "project(':a')"                       | "lifecycle :root\naccess :a\nlifecycle :a\nbar :a\nlifecycle :b"
        "findProject"      | "configure(findProject(':a'))"        | "lifecycle :root\naccess :a\nlifecycle :a\nbar :a\nlifecycle :b"
        "getAllprojects"   | "getAllprojects().forEach"            | "lifecycle :root\naccess :root\nbar :root\naccess :a\nlifecycle :a\nbar :a\naccess :b\nlifecycle :b\nbar :b"
        "getSubprojects"   | "getSubprojects().forEach"            | "lifecycle :root\naccess :a\nlifecycle :a\nbar :a\naccess :b\nlifecycle :b\nbar :b"
        "getChildProjects" | "getChildProjects().values().forEach" | "lifecycle :root\naccess :a\nlifecycle :a\nbar :a\naccess :b\nlifecycle :b\nbar :b"
    }

    def 'lifecycle.beforeProject eager execution can be triggered from gradle.allprojects'() {
        settingsFile """
            rootProject.name = 'root'
            gradle.allprojects {
                println "allprojects :\$name"
                println "\${foo}"
            }
            gradle.lifecycle.beforeProject {
                println "lifecycle :\$name"
                ext.foo = "bar :\$name"
            }
            include(":a")
            include(":b")
        """

        file("a/build.gradle") << ""
        file("b/build.gradle") << ""

        when:
        run "help", "-q"

        then:
        outputContains """
lifecycle :root
allprojects :root
bar :root
allprojects :a
lifecycle :a
bar :a
allprojects :b
lifecycle :b
bar :b
"""
    }

    static def projectMutableStateAccess = "version"
}
