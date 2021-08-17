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

import org.gradle.initialization.LoadProjectsBuildOperationType
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheRecreateOption
import org.gradle.integtests.fixtures.BuildOperationsFixture

class ConfigurationCacheIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

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

    def "configuration cache for help on empty project"() {
        given:
        settingsFile.createFile()
        configurationCacheRun "help"
        def firstRunOutput = removeVfsLogOutput(result.normalizedOutput)
            .replaceAll(/Calculating task graph as no configuration cache is available for tasks: help\n/, '')
            .replaceAll(/Configuration cache entry stored.\n/, '')

        when:
        configurationCacheRun "help"
        def secondRunOutput = removeVfsLogOutput(result.normalizedOutput)
            .replaceAll(/Reusing configuration cache.\n/, '')
            .replaceAll(/Configuration cache entry reused.\n/, '')

        then:
        firstRunOutput == secondRunOutput
    }

    private static String removeVfsLogOutput(String normalizedOutput) {
        normalizedOutput
            .replaceAll(/Received \d+ file system events .*\n/, '')
            .replaceAll(/Spent \d+ ms processing file system events since last build\n/, '')
            .replaceAll(/Watching \d+ (directory hierarchies to track changes between builds in \d+ directories|directories to track changes between builds)\n/, '')
            .replaceAll(/Spent \d+ ms registering watches for file system events\n/, '')
            .replaceAll(/Virtual file system .*\n/, '')
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

    def "restores some details of the project structure"() {
        def fixture = new BuildOperationsFixture(executer, temporaryFolder)

        settingsFile << """
            rootProject.name = 'thing'
            include 'a', 'b', 'c'
            include 'a:b'
            project(':a:b').projectDir = file('custom')
            gradle.rootProject {
                allprojects {
                    task thing
                }
            }
        """

        when:
        configurationCacheRun "help"

        then:
        def event = fixture.first(LoadProjectsBuildOperationType)
        event.result.rootProject.name == 'thing'
        event.result.rootProject.path == ':'
        event.result.rootProject.children.size() == 3 // All projects are created when storing

        when:
        configurationCacheRun "help"

        then:
        def event2 = fixture.first(LoadProjectsBuildOperationType)
        event2.result.rootProject.name == 'thing'
        event2.result.rootProject.path == ':'
        event2.result.rootProject.projectDir == testDirectory.absolutePath
        event2.result.rootProject.children.empty // None of the child projects are created when loading, as they have no tasks scheduled

        when:
        configurationCacheRun ":a:thing"

        then:
        def event3 = fixture.first(LoadProjectsBuildOperationType)
        event3.result.rootProject.name == 'thing'
        event3.result.rootProject.children.size() == 3 // All projects are created when storing

        when:
        configurationCacheRun ":a:thing"

        then:
        def event4 = fixture.first(LoadProjectsBuildOperationType)
        event4.result.rootProject.name == 'thing'
        event4.result.rootProject.path == ':'
        event4.result.rootProject.projectDir == testDirectory.absolutePath
        event4.result.rootProject.children.size() == 1 // Only project a is created when loading
        def project1 = event4.result.rootProject.children.first()
        project1.name == 'a'
        project1.path == ':a'
        project1.projectDir == file('a').absolutePath
        project1.children.empty

        when:
        configurationCacheRun ":a:b:thing"

        then:
        def event5 = fixture.first(LoadProjectsBuildOperationType)
        event5.result.rootProject.name == 'thing'
        event5.result.rootProject.children.size() == 3 // All projects are created when storing

        when:
        configurationCacheRun ":a:b:thing"

        then:
        def event6 = fixture.first(LoadProjectsBuildOperationType)
        event6.result.rootProject.name == 'thing'
        event6.result.rootProject.path == ':'
        event6.result.rootProject.projectDir == testDirectory.absolutePath
        event6.result.rootProject.children.size() == 1
        def project3 = event6.result.rootProject.children.first()
        project3.name == 'a'
        project3.path == ':a'
        project3.projectDir == file('a').absolutePath
        project3.children.size() == 1
        def project4 = project3.children.first()
        project4.name == 'b'
        project4.path == ':a:b'
        project4.projectDir == file('custom').absolutePath
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
}
