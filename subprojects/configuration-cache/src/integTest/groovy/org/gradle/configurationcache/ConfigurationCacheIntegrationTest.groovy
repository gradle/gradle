/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheRecreateOption
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import spock.lang.Issue

import static org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheProblemsOption.Value.*

class ConfigurationCacheIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "configuration cache is out of incubation"() {
        given:
        settingsFile << ""

        when:
        run("help", "--configuration-cache")

        then:
        result.assertHasPostBuildOutput("Configuration cache entry stored.")
        !output.contains("Configuration cache is an incubating feature.")
    }


    def "can enable configuration cache using incubating and final variants"() {

        given:
        buildFile """
        task check {
            assert gradle.startParameter.configurationCache.get() == ${ccOn}
            assert ${maxProblems == _ } || gradle.startParameter.configurationCacheMaxProblems == ${maxProblems}
            assert ${problemsAs == _ } || gradle.startParameter.configurationCacheProblems.name() == "${problemsAs}"
            assert ${quiet == _ } || gradle.startParameter.configurationCacheQuiet == ${quiet}
            assert ${debug == _ } || gradle.startParameter.configurationCacheDebug == ${debug}
            assert ${recreate == _ } || gradle.startParameter.configurationCacheRecreateCache == ${recreate}
        }
        """

        when:
        run(task, *options)

        then:
        if (ccOn) {
            result.assertHasPostBuildOutput("Configuration cache entry stored.")
        }

        where:
        task    | ccOn  | problemsAs| maxProblems   | quiet | recreate  | debug | options
        "help"  | true  | WARN      | 100           | true  | true      | true  | ["-Dorg.gradle.configuration-cache=true", "-Dorg.gradle.configuration-cache.problems=warn", "-Dorg.gradle.configuration-cache.max-problems=100", "-Dorg.gradle.configuration-cache.quiet=true", "-Dorg.gradle.configuration-cache.recreate-cache=true", "-Dorg.gradle.configuration-cache.debug=true" ]
        "help"  | true  | WARN      | 100           | true  | true      | true  | ["-Dorg.gradle.unsafe.configuration-cache=true", "-Dorg.gradle.unsafe.configuration-cache-problems=warn", "-Dorg.gradle.unsafe.configuration-cache.max-problems=100", "-Dorg.gradle.unsafe.configuration-cache.quiet=true", "-Dorg.gradle.unsafe.configuration-cache.recreate-cache=true", "-Dorg.gradle.unsafe.configuration-cache.debug=true" ]
        "help"  | false | WARN      | _             | _     | _         | _     | ["-Dorg.gradle.unsafe.configuration-cache-problems=warn"]
        "help"  | false | _         | 100           | _     | _         | _     | ["-Dorg.gradle.unsafe.configuration-cache.max-problems=100"]
        "help"  | false | _         | _             | _     | true      | _     | ["-Dorg.gradle.unsafe.configuration-cache.recreate-cache=true"]
        "help"  | false | _         | _             | true  | _         | _     | ["-Dorg.gradle.unsafe.configuration-cache.quiet=true"]
        "help"  | false | _         | _             | _     | _         | true  | ["-Dorg.gradle.unsafe.configuration-cache.debug=true"]
        "help"  | true  | _         | _             | _     | _         | _     | ["--configuration-cache"]
        "help"  | true  | _         | _             | _     | _         | _     | ["-Dorg.gradle.unsafe.configuration-cache=true"]
        "help"  | false | _         | _             | _     | _         | _     | ["--no-configuration-cache"]
        "help"  | false | _         | _             | _     | _         | _     | ["-Dorg.gradle.unsafe.configuration-cache=false"]
    }

    def "can use finalized and incubating variants"() {

        given:
        buildFile """task check {
            assert gradle.startParameter.configurationCache.get() == ${expected}
        }
        """

        when:
        run(task, *options)

        then:
        if (expected) {
            result.assertHasPostBuildOutput("Configuration cache entry stored.")
        }

        where:
        task           | expected   | options
        "help"         | true       | ["-Dorg.gradle.configuration-cache=true"]
        "help"         | true       | ["-Dorg.gradle.unsafe.configuration-cache=true"]
        "help"         | false      | ["-Dorg.gradle.configuration-cache=false"]
        "help"         | false      | ["-Dorg.gradle.unsafe.configuration-cache=false"]
        "help"         | false      | ["--no-configuration-cache"]
        "help"         | false      | ["--no-configuration-cache"]
        "help"         | true       | ["--configuration-cache"]

        // "help"         | { it.configurationCacheProblems == WARN }              | ["-Dorg.gradle.configuration-cache.problems=WARN"]
        // "help"         | { it.configurationCacheProblems == WARN }              | ["-Dorg.gradle.unsafe.configuration-cache-problems=WARN"]
        // "help"         | { it.configurationCacheMaxProblems == 100 }               | ["-Dorg.gradle.configuration-cache.max-problems=100"]
        // "help"         | { it.configurationCacheMaxProblems == 100 }               | ["-Dorg.gradle.unsafe.configuration-cache.max-problems=100"]
    }


    def "configuration cache for Help plugin task '#task' on empty project"() {
        given:
        settingsFile.createFile()
        configurationCacheRun(task, *options)
        def firstRunOutput = removeVfsLogOutput(result.normalizedOutput)
            .replaceAll(/Calculating task graph as no configuration cache is available for tasks: ${task}.*\n/, '')
            .replaceAll(/Configuration cache entry stored.\n/, '')

        when:
        configurationCacheRun(task, *options)
        def secondRunOutput = removeVfsLogOutput(result.normalizedOutput)
            .replaceAll(/Reusing configuration cache.\n/, '')
            .replaceAll(/Configuration cache entry reused.\n/, '')

        then:
        firstRunOutput == secondRunOutput

        where:
        task           | options
        "help"         | []
        "properties"   | []
        "dependencies" | []
        "help"         | ["--task", "help"]
        "help"         | ["--rerun"]
    }

    def "can store task selection success/failure for :help --task"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile.text = """
        task aTask
        """
        when:
        configurationCacheFails "help", "--task", "bTask"
        then:
        failure.assertHasCause("Task 'bTask' not found in root project")
        configurationCache.assertStateStored()

        when:
        configurationCacheFails "help", "--task", "cTask"
        then:
        failure.assertHasCause("Task 'cTask' not found in root project")
        configurationCache.assertStateStored()

        when:
        configurationCacheFails "help", "--task", "bTask"
        then:
        failure.assertHasCause("Task 'bTask' not found in root project")
        configurationCache.assertStateLoaded()

        when:
        configurationCacheRun "help", "--task", "aTask"
        then:
        output.contains "Detailed task information for aTask"
        configurationCache.assertStateStored()

        when:
        configurationCacheFails "help", "--task", "cTask"
        then:
        failure.assertHasCause("Task 'cTask' not found in root project")
        configurationCache.assertStateLoaded()

        when:
        buildFile << """
        task bTask
        """
        configurationCacheRun "help", "--task", "bTask"
        then:
        output.contains "Detailed task information for bTask"
        configurationCache.assertStateStored()
    }

    @Issue("https://github.com/gradle/gradle/issues/18064")
    def "can build plugin with project dependencies"() {
        given:
        settingsFile << """
            include 'my-lib'
            include 'my-plugin'
        """
        file('my-lib/build.gradle') << """
            plugins { id 'java' }
        """
        file('my-plugin/build.gradle') << """
            plugins { id 'java-gradle-plugin' }

            dependencies {
              implementation project(":my-lib")
            }

            gradlePlugin {
              plugins {
                myPlugin {
                  id = 'com.example.my-plugin'
                  implementationClass = 'com.example.MyPlugin'
                }
              }
            }
        """
        file('src/main/java/com/example/MyPlugin.java') << """
            package com.example;
            public class MyPlugin implements $Plugin.name<$Project.name> {
              @Override
              public void apply($Project.name project) {
              }
            }
        """
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun "build"
        configurationCacheRun "build"

        then:
        configurationCache.assertStateLoaded()
    }

    def "can copy zipTree"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile """
            def jar = tasks.register("jar", org.gradle.jvm.tasks.Jar) {
                it.from("a.file")
                it.destinationDirectory.set(layout.buildDirectory)
                it.archiveFileName.set("output.jar")
            }

            tasks.register("copy", org.gradle.api.tasks.Copy) {
                it.from(zipTree(${provider}))
                it.destinationDir(new File(project.buildDir, "copied"))
            }
        """
        file("a.file") << "42"

        when:
        configurationCacheRun "copy"
        configurationCacheRun "copy"

        then:
        configurationCache.assertStateLoaded()

        where:
        provider                         | _
        "jar.flatMap { it.archiveFile }" | _
        "jar.get().archiveFile"          | _
    }

    @Issue("gradle/gradle#20390")
    def "can deserialize copy task with rename"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile """
            tasks.register('copyAndRename', Copy) {
                from('foo') { rename { 'bar' } }
            }
        """

        when:
        configurationCacheRun "copyAndRename"
        configurationCacheRun "copyAndRename"

        then:
        configurationCache.assertStateLoaded()
    }

    def "can request to recreate the cache"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun "help", "-D${ConfigurationCacheRecreateOption.PROPERTY_NAME}=true"

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "help", "-D${ConfigurationCacheRecreateOption.PROPERTY_NAME}=true"

        then:
        configurationCache.assertStateStored()
        outputContains("Recreating configuration cache")
    }

    def "does not configure build when task graph is already cached for requested tasks"() {

        def configurationCache = newConfigurationCacheFixture()

        given:
        buildFile << """
            println "running build script"

            class SomeTask extends DefaultTask {
                SomeTask() {
                    println("create task")
                }
            }
            task a(type: SomeTask) {
                println("configure task")
            }
            task b {
                dependsOn a
            }
        """

        when:
        configurationCacheRun "a"

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as no configuration cache is available for tasks: a")
        outputContains("running build script")
        outputContains("create task")
        outputContains("configure task")
        result.assertTasksExecuted(":a")

        when:
        configurationCacheRun "a"

        then:
        configurationCache.assertStateLoaded()
        outputContains("Reusing configuration cache.")
        outputDoesNotContain("running build script")
        outputDoesNotContain("create task")
        outputDoesNotContain("configure task")
        result.assertTasksExecuted(":a")

        when:
        configurationCacheRun "b"

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as no configuration cache is available for tasks: b")
        outputContains("running build script")
        outputContains("create task")
        outputContains("configure task")
        result.assertTasksExecuted(":a", ":b")

        when:
        configurationCacheRun "a"

        then:
        configurationCache.assertStateLoaded()
        outputContains("Reusing configuration cache.")
        outputDoesNotContain("running build script")
        outputDoesNotContain("create task")
        outputDoesNotContain("configure task")
        result.assertTasksExecuted(":a")
    }

    def "configuration cache for multi-level projects"() {
        given:
        settingsFile << """
            include 'a:b', 'a:c'
        """
        configurationCacheRun ":a:b:help", ":a:c:help"
        def firstRunOutput = result.groupedOutput

        when:
        configurationCacheRun ":a:b:help", ":a:c:help"

        then:
        result.groupedOutput.task(":a:b:help").output == firstRunOutput.task(":a:b:help").output
        result.groupedOutput.task(":a:c:help").output == firstRunOutput.task(":a:c:help").output
    }

    def "captures changes applied in task graph whenReady listener"() {
        buildFile << """
            class SomeTask extends DefaultTask {
                @Internal
                String value

                @TaskAction
                void run() {
                    println "value = " + value
                }
            }

            task ok(type: SomeTask)

            gradle.taskGraph.whenReady {
                ok.value = 'value'
            }
        """

        when:
        configurationCacheRun "ok"
        configurationCacheRun "ok"

        then:
        outputContains("value = value")
    }

    def "can init two projects in a row"() {
        def configurationCache = new ConfigurationCacheFixture(this)
        when:
        useTestDirectoryThatIsNotEmbeddedInAnotherBuild()
        configurationCacheRun "init", "--dsl", "groovy", "--type", "basic"

        then:
        outputContains("> Task :init")
        configurationCache.assertStateStoredAndDiscarded {
            assert totalProblems == 0
        }
        succeeds 'properties'
        def projectName1 = testDirectory.name
        outputContains("name: ${projectName1}")

        when:
        useTestDirectoryThatIsNotEmbeddedInAnotherBuild()
        configurationCacheRun "init", "--dsl", "groovy", "--type", "basic"

        then:
        outputContains("> Task :init")
        succeeds 'properties'
        def projectName2 = testDirectory.name
        outputContains("name: ${projectName2}")
        projectName1 != projectName2
    }
}
