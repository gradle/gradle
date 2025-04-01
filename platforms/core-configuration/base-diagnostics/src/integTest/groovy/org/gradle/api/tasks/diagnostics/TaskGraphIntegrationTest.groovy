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
        settingsFile """rootProject.name = 'my-root'"""
        buildFile """
            def leaf1 = tasks.register("leaf1")
            def leaf2 = tasks.register("leaf2")
            def middle = tasks.register("middle"){
                dependsOn(leaf1, leaf2)
            }
            tasks.register("root"){
                dependsOn(leaf1, middle)
            }
        """

        when:
        succeeds("root", "--task-graph")

        then:
        outputContains("""
Tasks graph for: root
\\--- :root
     +--- :leaf1
     \\--- :middle
          +--- :leaf1 (*)
          \\--- :leaf2

(*) - details omitted (listed previously)
""")
    }

    def "shows simple graph of tasks with multiple roots"() {
        given:
        settingsFile """rootProject.name = 'my-root'"""
        buildFile """
            def leaf1 = tasks.register("leaf1")
            def leaf2 = tasks.register("leaf2")
            def middle = tasks.register("middle"){
                dependsOn(leaf1, leaf2)
            }
            tasks.register("root"){
                dependsOn(leaf1, middle)
            }
            tasks.register("root2"){
                dependsOn(leaf2)
            }
        """

        when:
        succeeds("root", "r2", "--task-graph")

        then:
        outputContains("""
Tasks graph for: root r2
+--- :root
|    +--- :leaf1
|    \\--- :middle
|         +--- :leaf1 (*)
|         \\--- :leaf2
\\--- :root2
     \\--- :leaf2 (*)

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
            def middle = tasks.register("middle"){
                dependsOn(leaf1, leaf2)
            }
            tasks.register("root"){
                dependsOn(leaf1, middle)
            }
            tasks.register("root2"){
                dependsOn(gradle.includedBuild("included").task(":fromIncluded"))
            }
        """

        when:
        succeeds("root", "r2", "--task-graph")

        then:
        outputContains("""
Tasks graph for: root r2
+--- :root
|    +--- :leaf1
|    \\--- :middle
|         +--- :leaf1 (*)
|         \\--- :leaf2
\\--- :root2
     \\--- other build task :included:fromIncluded

(*) - details omitted (listed previously)
""")
    }

    def "shows simple graph of tasks with task removed"() {
        given:
        settingsFile """rootProject.name = 'my-root'"""
        buildFile """
            def leaf1 = tasks.register("leaf1")
            def leaf2 = tasks.register("leaf2")
            def middle = tasks.register("middle"){
                dependsOn(leaf1, leaf2)
            }
            tasks.register("root"){
                dependsOn(leaf1, middle)
            }
        """

        when:
        succeeds("root", "-x", "middle", "--task-graph")

        then:
        outputContains("""
Tasks graph for: root
\\--- :root
     \\--- :leaf1
""")
    }

    def "shows simple graph of tasks with task disabled"() {
        given:
        settingsFile """rootProject.name = 'my-root'"""
        buildFile """
            def leaf1 = tasks.register("leaf1")
            def leaf2 = tasks.register("leaf2")
            def middle = tasks.register("middle"){
                dependsOn(leaf1, leaf2)
                enabled = false
            }
            tasks.register("root"){
                dependsOn(leaf1, middle)
            }
        """

        when:
        succeeds("root", "--task-graph")

        then:
        outputContains("""
Tasks graph for: root
\\--- :root
     +--- :leaf1
     \\--- :middle
          +--- :leaf1 (*)
          \\--- :leaf2

(*) - details omitted (listed previously)
""")
    }

    def "can handle mustRunAfter and finalizedBy"() {
        given:
        settingsFile """rootProject.name = 'my-root'"""
        buildFile """
            def leaf1 = tasks.register("leaf1")
            def leaf2 = tasks.register("leaf2")
            def middle = tasks.register("middle"){
                dependsOn(leaf1, leaf2)
            }
            tasks.register("root"){
                mustRunAfter(leaf1)
            }
            tasks.register("root2"){
                dependsOn(leaf1)
                finalizedBy(leaf2)
            }
        """

        when:
        succeeds("root", "root2", "--task-graph")

        then:
        outputContains("""
Tasks graph for: root root2
+--- :root
|    +--- :leaf1
|    \\--- :leaf1 (*)
\\--- :root2
     \\--- :leaf1 (*)

(*) - details omitted (listed previously)
""")
    }

    def "shows simple graph of tasks with clean"() {
        given:
        settingsFile """rootProject.name = 'my-root'"""
        buildFile """
            plugins {
                id "base"
            }
            def leaf1 = tasks.register("leaf1")
            def leaf2 = tasks.register("leaf2")
            def middle = tasks.register("middle"){
                dependsOn(leaf1, leaf2)
            }
            tasks.register("root"){
                dependsOn(leaf1, middle)
            }
        """

        when:
        succeeds("clean", "root", "--task-graph")

        then:
        outputContains("""
Tasks graph for: clean root
+--- :clean
\\--- :root
     +--- :leaf1
     \\--- :middle
          +--- :leaf1 (*)
          \\--- :leaf2

(*) - details omitted (listed previously)
""")
    }

    def "shows graph for simple java task"() {
        given:
        settingsFile """rootProject.name = 'my-root'"""
        buildFile """
            plugins {
                id "java-library"
            }
            def leaf1 = tasks.register("leaf1")
            def leaf2 = tasks.register("leaf2")
            def middle = tasks.register("middle"){
                dependsOn(tasks.named("compileJava"), leaf2)
            }
            tasks.register("root"){
                dependsOn(leaf1, middle)
            }
        """

        when:
        succeeds("clean", "root", "--task-graph")

        then:
        outputContains("""
Tasks graph for: clean root
+--- :clean
\\--- :root
     +--- :leaf1
     \\--- :middle
          +--- :compileJava
          |    \\--- destroyer locations for task group 0
          |         \\--- Resolve mutations for :clean
          \\--- :leaf2
""")
    }

    def "shows dependencies via inputs"() {
        given:
        settingsFile """rootProject.name = 'my-root'"""
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
\\--- :consumeFile
     \\--- :generateFile
""")
    }
}
