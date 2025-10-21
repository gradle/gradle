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

import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture

class TaskGraphIntegrationTest extends AbstractIntegrationSpec {

    def "shows simple graph of tasks"() {
        given:
        buildFile sampleGraph

        when:
        succeeds("root", "--task-graph")

        then:
        outputContains("""
Tasks graph for: root
\\--- :root (org.gradle.api.DefaultTask)
     +--- :leaf1 (org.gradle.api.DefaultTask)
     \\--- :middle (org.gradle.api.DefaultTask)
          +--- :leaf1 (*)
          \\--- :leaf2 (org.gradle.api.DefaultTask)

(*) - details omitted (listed previously)
""")
        outputDoesNotContain("I'm a task called")

        and:
        outputContains("Task graph printing is an incubating feature.")
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
|         +--- :leaf1 (*)
|         \\--- :leaf2 (org.gradle.api.DefaultTask)
\\--- :root2 (org.gradle.api.DefaultTask)
     \\--- :leaf2 (*)

(*) - details omitted (listed previously)
""")
    }

    def "shows graph of tasks with tasks included from an included build"() {
        given:
        settingsFile """
            includeBuild "included"
        """
        buildFile 'included/build.gradle', """
            def leaf1 = tasks.register("leaf1"){
                doLast {
                    println("I'm a task called included leaf1")
                }
            }
            def leaf2 = tasks.register("leaf2")
            tasks.register("fromIncluded") {
                dependsOn(leaf1, leaf2)
            }
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
|         +--- :leaf1 (*)
|         \\--- :leaf2 (org.gradle.api.DefaultTask)
\\--- :root2 (org.gradle.api.DefaultTask)
     \\--- other build task :included:fromIncluded (org.gradle.api.DefaultTask)
          +--- :included:leaf1 (org.gradle.api.DefaultTask)
          \\--- :included:leaf2 (org.gradle.api.DefaultTask)

(*) - details omitted (listed previously)
""")
        outputDoesNotContain("I'm a task called included leaf1")
    }

    def "shows the subgraph of a task included from included build for qualified task invocation"() {
        given:
        settingsFile """
            includeBuild "included"
        """
        settingsFile 'included/settings.gradle', """
            rootProject.name = "included"
        """
        buildFile 'included/build.gradle', """
            def leaf1 = tasks.register("leaf1"){
                doLast {
                    println("I'm a task called included leaf1")
                }
            }
            def leaf2 = tasks.register("leaf2")
            def leaf3 = tasks.register("leaf3")
            def middle = tasks.register("middle") {
                dependsOn(leaf1, leaf2)
            }
            tasks.register("fromIncluded") {
                dependsOn(middle, leaf2)
                finalizedBy(leaf3)
            }
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
        succeeds(":included:fromIncluded", "--task-graph")

        then:
        outputContains("""
Tasks graph for: :included:fromIncluded
\\--- other build task :included:fromIncluded (org.gradle.api.DefaultTask)
     +--- :included:leaf2 (org.gradle.api.DefaultTask)
     +--- :included:middle (org.gradle.api.DefaultTask)
     |    +--- :included:leaf1 (org.gradle.api.DefaultTask)
     |    \\--- :included:leaf2 (*)
     \\--- :included:leaf3 (org.gradle.api.DefaultTask, finalizer)

(*) - details omitted (listed previously)
""")
        outputDoesNotContain("I'm a task called included leaf1")

        and: "shows the task graph for included build tasks if invoked in it's directory"

        when:
        executer
            .inDirectory(file("included"))

        succeeds("fromIncluded", "--task-graph")

        then:
        outputContains("""
Tasks graph for: fromIncluded
\\--- :fromIncluded (org.gradle.api.DefaultTask)
     +--- :leaf2 (org.gradle.api.DefaultTask)
     +--- :middle (org.gradle.api.DefaultTask)
     |    +--- :leaf1 (org.gradle.api.DefaultTask)
     |    \\--- :leaf2 (*)
     \\--- :leaf3 (org.gradle.api.DefaultTask, finalizer)

(*) - details omitted (listed previously)
""")
    }

    def "does not show graph for buildSrc tasks"() {
        given:
        buildFile 'buildSrc/build.gradle', ""
        buildFile 'buildSrc/settings.gradle', ""
        buildFile sampleGraph

        when:
        succeeds("root", "--task-graph")

        then:
        outputContains("""
Tasks graph for: root
\\--- :root (org.gradle.api.DefaultTask)
     +--- :leaf1 (org.gradle.api.DefaultTask)
     \\--- :middle (org.gradle.api.DefaultTask)
          +--- :leaf1 (*)
          \\--- :leaf2 (org.gradle.api.DefaultTask)

(*) - details omitted (listed previously)
""")
        executed(":buildSrc:jar")
        outputDoesNotContain("--- :buildSrc:jar")

        and: "does not show the task graph for buildSrc tasks if invoked directly because jar is magic"

        when:
        executer
            .inDirectory(file("buildSrc"))

        fails("jar", "--task-graph")

        then:
        result.assertHasErrorOutput("""Task 'jar' not found in root project 'buildSrc'.""")

        and: "shows the task graph for buildSrc tasks if invoked directly when java plugin is explicit"

        when:
        buildFile 'buildSrc/build.gradle', """
            plugins {
                id "java-library"
            }
            def buildSrcTask = tasks.register("buildSorcerer") {
                doLast {
                    println("I'm a buildSorcerer task!")
                }
            }
            tasks.named("compileJava") {
                dependsOn(buildSrcTask)
            }
        """
        executer
            .inDirectory(file("buildSrc"))

        succeeds("jar", "--task-graph")

        then:
        outputContains("""
Tasks graph for: jar
\\--- :jar (org.gradle.api.tasks.bundling.Jar)
     +--- :classes (org.gradle.api.DefaultTask)
     |    +--- :compileJava (org.gradle.api.tasks.compile.JavaCompile)
     |    |    \\--- :buildSorcerer (org.gradle.api.DefaultTask)
     |    \\--- :processResources (org.gradle.language.jvm.tasks.ProcessResources)
     \\--- :compileJava (*)

(*) - details omitted (listed previously)
""")
        outputDoesNotContain("I'm a buildSorcerer task!")
    }

    def "does not show graph for tasks from early included builds"() {
        given:
        createDir('included') {
            file('build.gradle') << """
                plugins { id 'groovy-gradle-plugin' }
            """
            file('settings.gradle') << ""
            file('src/main/groovy/my-plugin.gradle') << """
                println 'In script plugin'
            """
            file('build.gradle') << """
                plugins { id 'java' }
                group = 'org.test'
                version = '1.0'
            """
            javaFile 'src/main/java/Lib.java', """
                public class Lib { public static void main() {
                    System.out.println("Before!");
                } }
            """
        }
        settingsFile """
             pluginManagement {
                includeBuild("included")
            }
        """
        buildFile """
            plugins {
                id 'my-plugin'
            }
            $sampleGraph
        """

        when:
        succeeds("root", "--task-graph")

        then:
        outputContains("""
Tasks graph for: root
\\--- :root (org.gradle.api.DefaultTask)
     +--- :leaf1 (org.gradle.api.DefaultTask)
     \\--- :middle (org.gradle.api.DefaultTask)
          +--- :leaf1 (*)
          \\--- :leaf2 (org.gradle.api.DefaultTask)

(*) - details omitted (listed previously)
""")
        outputContains("In script plugin")
        executed(":included:jar")
        outputDoesNotContain("--- :included:jar")

        and: "shows the task graph for early included build tasks if invoked in it's directory"

        when:
        executer
            .inDirectory(file("included"))

        succeeds("jar", "--task-graph")

        then:
        outputContains("""
Tasks graph for: jar
\\--- :jar (org.gradle.api.tasks.bundling.Jar)
     +--- :classes (org.gradle.api.DefaultTask)
     |    +--- :compileGroovy (org.gradle.api.tasks.compile.GroovyCompile)
     |    |    \\--- :compileJava (org.gradle.api.tasks.compile.JavaCompile)
     |    |         \\--- :generatePluginAdapters (org.gradle.plugin.devel.internal.precompiled.GeneratePluginAdaptersTask)
     |    |              \\--- :extractPluginRequests (org.gradle.plugin.devel.internal.precompiled.ExtractPluginRequestsTask)
     |    +--- :compileGroovyPlugins (org.gradle.plugin.devel.internal.precompiled.CompileGroovyScriptPluginsTask)
     |    |    +--- :compileGroovy (*)
     |    |    \\--- :compileJava (*)
     |    +--- :compileJava (*)
     |    +--- :extractPluginRequests (*)
     |    \\--- :processResources (org.gradle.language.jvm.tasks.ProcessResources)
     |         \\--- :pluginDescriptors (org.gradle.plugin.devel.tasks.GeneratePluginDescriptors)
     +--- :compileGroovy (*)
     +--- :compileGroovyPlugins (*)
     +--- :compileJava (*)
     \\--- :extractPluginRequests (*)

(*) - details omitted (listed previously)
""")
    }

    def "shows simple graph of tasks with task removed"() {
        given:
        buildFile sampleGraph

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
          +--- :leaf1 (*)
          \\--- :leaf2 (org.gradle.api.DefaultTask)

(*) - details omitted (listed previously)
""")
    }

    def "ignores mustRunAfter and shouldRunAfter"() {
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
     \\--- :leaf1 (*)

(*) - details omitted (listed previously)
""")
    }

    def "displays finalizations"() {
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
            }
            tasks.register("root") {
                dependsOn(middle)
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
     +--- :leaf1 (*)
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
            $sampleGraph
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
          +--- :leaf1 (*)
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

    def "task graph can be empty"() {
        given:
        buildFile sampleGraph

        when:
        succeeds("leaf1", "--task-graph")

        then:
        outputContains("""
Tasks graph for: leaf1
\\--- :leaf1 (org.gradle.api.DefaultTask)

""")
    }

    def "dry-run has higher priority than task graph"() {
        given:
        buildFile sampleGraph

        when:
        succeeds("root", "--task-graph", "--dry-run")

        then:
        outputDoesNotContain("""Tasks graph for""")
        outputContains(":leaf1 SKIPPED")
        outputContains(":root SKIPPED")

        when:
        succeeds("root", "--dry-run", "--task-graph")

        then:
        outputDoesNotContain("""Tasks graph for""")
        outputContains(":leaf1 SKIPPED")
        outputContains(":root SKIPPED")
    }

    def "does not show artifact transforms"() {
        settingsFile """
            include 'lib', 'app'
        """
        buildFile "lib/build.gradle", """
            apply plugin: 'java'
        """
        buildFile "app/build.gradle", """
            import org.gradle.api.artifacts.transform.TransformParameters

            apply plugin: 'java'

            def artifactType = Attribute.of('artifactType', String)

            abstract class FileSizer implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    def output = outputs.file(input.name + ".txt")
                    output.text = String.valueOf(input.length())
                }
            }
            dependencies {
                implementation project(":lib")
                registerTransform(FileSizer) {
                    from.attribute(artifactType, 'jar')
                    to.attribute(artifactType, 'size')
                }
            }
            tasks.register("resolve", Copy) {
                from configurations.runtimeClasspath.incoming.artifactView {
                    attributes { it.attribute(artifactType, 'size') }
                }.artifacts.artifactFiles
                into project.layout.buildDirectory.dir("libs")
            }
        """
        buildFile ""

        when:
        succeeds("resolve", "--task-graph")

        then:
        outputContains("""
Tasks graph for: resolve
\\--- :app:resolve (org.gradle.api.tasks.Copy)

""")
    }

    def "does not print detailed task graph for GradleBuild task"() {
        buildFile """
            tasks.register("b1", GradleBuild) {
                tasks = ["t"]
                buildName = 'bp'
            }
            tasks.register("b2", GradleBuild) {
                tasks = [":t"]
                buildName = 'bp'
                mustRunAfter ":b1"
            }
            tasks.register("t")
        """

        when:
        succeeds("b1", "b2", "--task-graph")

        then:
        outputContains("""
Tasks graph for: b1 b2
+--- :b1 (org.gradle.api.tasks.GradleBuild)
\\--- :b2 (org.gradle.api.tasks.GradleBuild)

""")

        outputDoesNotContain(":t")
    }

    def "does not mix the output with included build"() {
        given:
        def taskCount = 500
        settingsFile """
            includeBuild "included"
        """
        buildFile 'included/build.gradle', """
            tasks.register("task0")
            for (int i = 1; i < $taskCount; i++) {
                def j = i
                tasks.register("task\$j"){
                    dependsOn("task\${j - 1}")
                }
            }
            tasks.register("fromIncluded") {
                dependsOn("task${taskCount - 1}")
            }
        """
        buildFile """
            tasks.register("task0")
            for (int i = 1; i < $taskCount; i++) {
                def j = i
                tasks.register("task\$j"){
                    dependsOn("task\${j - 1}")
                }
            }
            tasks.register("root") {
                dependsOn(gradle.includedBuild("included").task(":fromIncluded"))
                dependsOn("task${taskCount - 1}")
            }
        """

        when:
        succeeds(":included:fromIncluded", "root", "--task-graph")

        then:
        // verify that the graph output is not mixed with Gradle Task output during the parallel execution
        output.find(~/(?m):task\d+ \(org.gradle.api.DefaultTask\)\s*> Task /) == null
    }

    def "shows graph of tasks when project dependency is involved"() {
        given:
        settingsFile """
            include "lib"
            include "app"
        """
        buildFile 'lib/build.gradle', """
            plugins {
                id 'java-library'
            }
            def leaf1 = tasks.register("libleaf1") {
                doLast {
                    println("I'm a task called libleaf1")
                }
            }
            tasks.named("compileJava") {
                dependsOn(leaf1)
            }
        """
        buildFile 'app/build.gradle', """
            plugins {
                id 'java'
            }
            dependencies {
                implementation(project(":lib"))
            }
            def leaf1 = tasks.register("leaf1")
            def leaf2 = tasks.register("leaf2")
            def middle = tasks.register("middle") {
                dependsOn(leaf1, leaf2)
            }
            tasks.register("root") {
                dependsOn(leaf1, middle, "compileJava")
            }
        """

        when:
        succeeds("root", "--task-graph")

        then:
        outputContains("""
Tasks graph for: root
\\--- :app:root (org.gradle.api.DefaultTask)
     +--- :app:compileJava (org.gradle.api.tasks.compile.JavaCompile)
     |    \\--- :lib:compileJava (org.gradle.api.tasks.compile.JavaCompile)
     |         \\--- :lib:libleaf1 (org.gradle.api.DefaultTask)
     +--- :app:leaf1 (org.gradle.api.DefaultTask)
     \\--- :app:middle (org.gradle.api.DefaultTask)
          +--- :app:leaf1 (*)
          \\--- :app:leaf2 (org.gradle.api.DefaultTask)

(*) - details omitted (listed previously)
""")

        outputDoesNotContain("I'm a task called")
    }

    def "can execute logic from included builds if it's required for configuration"() {
        given:
        settingsFile """
            pluginManagement {
                includeBuild 'build-logic-settings'
            }

            plugins {
                id 'org.test.plugin.SettingsPlugin'
            }

            includeBuild 'build-logic-commons'
            includeBuild "build-logic"
        """
        settingsFile "build-logic-settings/settings.gradle", """
            println("I'm a build logic settings file")
        """
        buildFile "build-logic-settings/build.gradle", """
            plugins {
                id 'java-gradle-plugin'
            }

            gradlePlugin {
                plugins {
                    myPlugin {
                        id = "org.test.plugin.SettingsPlugin"
                        implementationClass = "org.test.SettingsPlugin"
                    }
                }
            }

            println "I'm settings plugin"
            tasks.register("settingsTask") {
                doLast {
                    println "I'm settings task"
                }
            }
            tasks.named("compileJava") {
                dependsOn "settingsTask"
            }
        """
        file("build-logic-settings/src/main/java/org/test/SettingsPlugin.java") << """
            package org.test;

            import org.gradle.api.Plugin;
            import org.gradle.api.initialization.Settings;

            public class SettingsPlugin implements Plugin<Settings> {
                public void apply(Settings settings) {
                    System.out.println("I'm SettingsPlugin");
                }
            }
        """
        settingsFile "build-logic-commons/settings.gradle", """
            includeBuild('../build-logic-settings')
            include("basics")
        """
        buildFile "build-logic-commons/build.gradle", """
            plugins {
                id "base"
            }
            tasks.register("commonsTask") {
                dependsOn(":basics:commonsTask")
            }
        """
        buildFile "build-logic-commons/basics/build.gradle", """
            plugins {
                id 'java'
                id 'groovy-gradle-plugin'
            }
            tasks.register("commonsTask") {
                doLast {
                    println "I'm commons task"
                }
            }
            tasks.named("compileJava") {
                dependsOn "commonsTask"
            }
        """
        file('build-logic-commons/basics/src/main/groovy/dummy.plugin.gradle') << ""
        settingsFile "build-logic/settings.gradle", """
            pluginManagement {
                includeBuild '../build-logic-commons'
            }
        """
        buildFile "build-logic/build.gradle", """
            plugins {
                id "base"
                id 'dummy.plugin'
            }
        """
        buildFile """
            tasks.register("root") {
                dependsOn(gradle.includedBuild("build-logic-commons").task(":commonsTask"))
                dependsOn(gradle.includedBuild("build-logic").task(":check"))
                doLast {
                    println "I'm root task"
                }
            }
        """

        when:
        succeeds("root", "--task-graph")

        then:
        outputContains("""
Tasks graph for: root
\\--- :root (org.gradle.api.DefaultTask)
     +--- other build task :build-logic:check (org.gradle.api.DefaultTask)
     \\--- other build task :build-logic-commons:commonsTask (org.gradle.api.DefaultTask)
""")
        executedAndNotSkipped(
            ":build-logic-settings:settingsTask",
            ":build-logic-commons:basics:commonsTask", ":build-logic-commons:basics:jar"
        )
    }

    def "configuration cache is reused"() {
        given:
        buildFile """
            def leaf1 = tasks.register("leaf1")
            def leaf2 = tasks.register("leaf2") {
                enabled = false
            }
            def leaf3 = tasks.register("leaf3")
            def leaf4 = tasks.register("leaf4") {
                dependsOn(leaf3)
            }
            def middle = tasks.register("middle") {
                dependsOn(leaf1, leaf2)
            }
            tasks.register("root") {
                dependsOn(middle)
            }
            tasks.register("root2") {
                dependsOn(leaf1)
                finalizedBy(leaf4)
            }
        """
        def expectedOutput = """
Tasks graph for: root r2
+--- :root (org.gradle.api.DefaultTask)
|    \\--- :middle (org.gradle.api.DefaultTask)
|         +--- :leaf1 (org.gradle.api.DefaultTask)
|         \\--- :leaf2 (org.gradle.api.DefaultTask, disabled)
\\--- :root2 (org.gradle.api.DefaultTask)
     +--- :leaf1 (*)
     \\--- :leaf4 (org.gradle.api.DefaultTask, finalizer)
          \\--- :leaf3 (org.gradle.api.DefaultTask)

(*) - details omitted (listed previously)
        """

        def configurationCache = new ConfigurationCacheFixture(this)
        run("root", "r2", "--task-graph", "--${StartParameterBuildOptions.ConfigurationCacheOption.LONG_OPTION}")
        outputContains(expectedOutput)

        when:
        run("root", "r2", "--task-graph", "--${StartParameterBuildOptions.ConfigurationCacheOption.LONG_OPTION}")

        then:
        outputContains(expectedOutput)
        configurationCache.assertStateLoaded()
    }

    private def sampleGraph = buildScriptSnippet """
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
}
