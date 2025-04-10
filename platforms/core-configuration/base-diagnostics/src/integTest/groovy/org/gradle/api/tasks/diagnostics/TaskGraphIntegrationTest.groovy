/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.tasks.diagnostics

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskGraphIntegrationTest extends AbstractIntegrationSpec {

    def "shows simple graph of tasks"() {
        given:
        buildFile """
            def leaf1 = tasks.register("leaf1") {
                doLast {
                    println("I'm a task called leaf1")
                }
            }
            def leaf2 = tasks.register("leaf2")
            def middle = tasks.register("middle") {
                dependsOn(leaf1, leaf2)
            }
            tasks.register("root") {
                dependsOn(leaf1, middle)
                doLast {
                    println("I'm a task called root")
                }
            }
        """

        when:
        succeeds("root", "--task-graph")

        then:
        outputContains("""
Tasks graph for: root
\\--- :root (org.gradle.api.DefaultTask)
     +--- :leaf1 (org.gradle.api.DefaultTask)
     \\--- :middle (org.gradle.api.DefaultTask)
          +--- :leaf1 (org.gradle.api.DefaultTask) (*)
          \\--- :leaf2 (org.gradle.api.DefaultTask)

(*) - details omitted (listed previously)
""")
        outputDoesNotContain("I'm a task called")
    }

    def "shows simple graph of tasks with multiple roots"() {
        given:
        buildFile """
            def leaf1 = tasks.register("leaf1")
            def leaf2 = tasks.register("leaf2")
            def middle = tasks.register("middle") {
                dependsOn(leaf1, leaf2)
            }
            tasks.register("root") {
                dependsOn(leaf1, middle)
            }
            tasks.register("root2") {
                dependsOn(leaf2)
            }
        """

        when:
        succeeds("root", "r2", "--task-graph")

        then:
        outputContains("""
Tasks graph for: root r2
+--- :root (org.gradle.api.DefaultTask)
|    +--- :leaf1 (org.gradle.api.DefaultTask)
|    \\--- :middle (org.gradle.api.DefaultTask)
|         +--- :leaf1 (org.gradle.api.DefaultTask) (*)
|         \\--- :leaf2 (org.gradle.api.DefaultTask)
\\--- :root2 (org.gradle.api.DefaultTask)
     \\--- :leaf2 (org.gradle.api.DefaultTask) (*)

(*) - details omitted (listed previously)
""")
    }

    def "shows graph of tasks included from included build"() {
        given:
        settingsFile """
            rootProject.name = 'root'
            includeBuild "included"
        """
        file("included/settings.gradle") << """
            rootProject.name = "included"
        """
        file('included/build.gradle') << """
            tasks.register("fromIncluded")
        """
        buildFile """
            def leaf1 = tasks.register("leaf1")
            def leaf2 = tasks.register("leaf2")
            def middle = tasks.register("middle") {
                dependsOn(leaf1, leaf2)
            }
            tasks.register("root") {
                dependsOn(leaf1, middle)
            }
            tasks.register("root2") {
                dependsOn(gradle.includedBuild("included").task(":fromIncluded"))
            }
        """

        when:
        succeeds("root", "r2", "--task-graph")

        then:
        outputContains("""
Tasks graph for: root r2
+--- :root (org.gradle.api.DefaultTask)
|    +--- :leaf1 (org.gradle.api.DefaultTask)
|    \\--- :middle (org.gradle.api.DefaultTask)
|         +--- :leaf1 (org.gradle.api.DefaultTask) (*)
|         \\--- :leaf2 (org.gradle.api.DefaultTask)
\\--- :root2 (org.gradle.api.DefaultTask)
     \\--- other build task :included:fromIncluded (org.gradle.api.DefaultTask)

(*) - details omitted (listed previously)
""")
    }

    def "shows simple graph of tasks with task removed"() {
        given:
        buildFile """
            def leaf1 = tasks.register("leaf1")
            def leaf2 = tasks.register("leaf2")
            def middle = tasks.register("middle") {
                dependsOn(leaf1, leaf2)
            }
            tasks.register("root") {
                dependsOn(leaf1, middle)
            }
        """

        when:
        succeeds("root", "-x", "middle", "--task-graph")

        then:
        outputContains("""
Tasks graph for: root
\\--- :root (org.gradle.api.DefaultTask)
     \\--- :leaf1 (org.gradle.api.DefaultTask)
""")
    }

    def "shows simple graph of tasks with task disabled"() {
        given:
        buildFile """
            def leaf1 = tasks.register("leaf1")
            def leaf2 = tasks.register("leaf2")
            def middle = tasks.register("middle") {
                dependsOn(leaf1, leaf2)
                enabled = false
            }
            tasks.register("root") {
                dependsOn(leaf1, middle)
            }
        """

        when:
        succeeds("root", "--task-graph")

        then:
        outputContains("""
Tasks graph for: root
\\--- :root (org.gradle.api.DefaultTask)
     +--- :leaf1 (org.gradle.api.DefaultTask)
     \\--- :middle (org.gradle.api.DefaultTask, disabled)
          +--- :leaf1 (org.gradle.api.DefaultTask) (*)
          \\--- :leaf2 (org.gradle.api.DefaultTask)

(*) - details omitted (listed previously)
""")
    }

    def "ignores mustRunAfter and shouldRunAfter but displays finalizations"() {
        given:
        buildFile """
            def leaf1 = tasks.register("leaf1")
            def leaf2 = tasks.register("leaf2")
            def leaf3 = tasks.register("leaf3")
            def leaf4 = tasks.register("leaf4") {
                dependsOn(leaf3)
            }
            def middle = tasks.register("middle") {
                dependsOn(leaf1, leaf2)
                shouldRunAfter(leaf3)
            }
            tasks.register("root") {
                dependsOn(middle)
                mustRunAfter(leaf4)
            }
            tasks.register("root2") {
                dependsOn(leaf1)
                finalizedBy(leaf4)
            }
        """

        when:
        succeeds("root", "root2", "--task-graph")

        then:
        outputContains("""
Tasks graph for: root root2
+--- :root (org.gradle.api.DefaultTask)
|    \\--- :middle (org.gradle.api.DefaultTask)
|         +--- :leaf1 (org.gradle.api.DefaultTask)
|         \\--- :leaf2 (org.gradle.api.DefaultTask)
\\--- :root2 (org.gradle.api.DefaultTask)
     +--- :leaf1 (org.gradle.api.DefaultTask) (*)
     \\--- :leaf4 (org.gradle.api.DefaultTask, finalizer)
          \\--- :leaf3 (org.gradle.api.DefaultTask)

(*) - details omitted (listed previously)
""")
    }

    def "shows simple graph of tasks with clean"() {
        given:
        buildFile """
            plugins {
                id "base"
            }
            def leaf1 = tasks.register("leaf1")
            def leaf2 = tasks.register("leaf2")
            def middle = tasks.register("middle") {
                dependsOn(leaf1, leaf2)
            }
            tasks.register("root") {
                dependsOn(leaf1, middle)
            }
        """

        when:
        succeeds("clean", "root", "--task-graph")

        then:
        outputContains("""
Tasks graph for: clean root
+--- :clean (org.gradle.api.tasks.Delete)
\\--- :root (org.gradle.api.DefaultTask)
     +--- :leaf1 (org.gradle.api.DefaultTask)
     \\--- :middle (org.gradle.api.DefaultTask)
          +--- :leaf1 (org.gradle.api.DefaultTask) (*)
          \\--- :leaf2 (org.gradle.api.DefaultTask)

(*) - details omitted (listed previously)
""")
    }

    def "shows graph for simple java task"() {
        given:
        buildFile """
            plugins {
                id "java-library"
            }
            def leaf1 = tasks.register("leaf1")
            def leaf2 = tasks.register("leaf2")
            def middle = tasks.register("middle") {
                dependsOn(tasks.named("compileJava"), leaf2)
            }
            tasks.register("root") {
                dependsOn(leaf1, middle)
            }
        """

        when:
        succeeds("clean", "root", "--task-graph")

        then:
        outputContains("""
Tasks graph for: clean root
+--- :clean (org.gradle.api.tasks.Delete)
\\--- :root (org.gradle.api.DefaultTask)
     +--- :leaf1 (org.gradle.api.DefaultTask)
     \\--- :middle (org.gradle.api.DefaultTask)
          +--- :compileJava (org.gradle.api.tasks.compile.JavaCompile)
          \\--- :leaf2 (org.gradle.api.DefaultTask)
""")
    }

    def "shows dependencies via inputs"() {
        given:
        buildFile """
            def generator = tasks.register("generateFile") {
                def outputFile = layout.buildDirectory.file("generated/output.txt")

                outputs.file(outputFile)
                doLast {
                    outputFile.get().asFile.text = "Hello, world!"
                }
            }

            tasks.register("consumeFile") {
                def inputFile = generator.map { it.outputs.files.singleFile }
                inputs.file(inputFile)
                doLast {
                    println(inputFile.get().text)
                }
            }
        """

        when:
        succeeds("consumeFile", "--task-graph")

        then:
        outputContains("""
Tasks graph for: consumeFile
\\--- :consumeFile (org.gradle.api.DefaultTask)
     \\--- :generateFile (org.gradle.api.DefaultTask)
""")
    }
}
