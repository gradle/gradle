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
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.invocation.Gradle
import org.gradle.invocation.DefaultGradle
import spock.lang.Ignore

import static org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheProblemsFixture.resolveConfigurationCacheReportDirectory

class ConfigurationCacheProblemReportingIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "input files are reported with relative path"() {
        given:
        file('provider.txt').text = 'provider'
        file('fis-path.txt').text = 'fis-path'
        file('fis-file.txt').text = 'fis-file'
        file('fis-abs.txt').text = 'fis-abs'
        buildFile '''
            providers.fileContents(layout.projectDirectory.file("provider.txt")).with { provider ->
                println("provider = ${provider.asText.get()}")
            }

            new FileInputStream("fis-path.txt").withCloseable { fis ->
                println("fis = ${fis.text}")
            }

            new FileInputStream(new File("fis-file.txt")).withCloseable { fis ->
                println("fis = ${fis.text}")
            }

            new FileInputStream(file("fis-abs.txt")).withCloseable { fis ->
                println("fis = ${fis.text}")
            }
        '''

        when:
        configurationCacheRun 'help'

        then:
        outputContains 'provider = provider'
        outputContains 'fis = fis-path'
        outputContains 'fis = fis-file'
        outputContains 'fis = fis-abs'

        and:
        problems.assertResultHasProblems(result) {
            withInput "Build file 'build.gradle': file 'provider.txt'"
            withInput "Build file 'build.gradle': file 'fis-path.txt'"
            withInput "Build file 'build.gradle': file 'fis-file.txt'"
            withInput "Build file 'build.gradle': file 'fis-abs.txt'"
        }
    }

    @Ignore("wip: Currently failing on CI for unknown reason")
    def "report file is content addressable"() {
        given:
        settingsFile << """
            rootProject.name = 'car'
        """

        file("build.gradle") << """
            buildDir = 'out'
            tasks.register('broken') {
                doFirst { println(project.name) }
            }
            tasks.register('alsoBroken') {
                doFirst { println(project.name) }
            }
        """
        def reportDir = {
            resolveConfigurationCacheReportDirectory(testDirectory.file('out'), failure.error)
        }

        when:
        configurationCacheFails 'broken'

        then:
        def reportDir1 = reportDir()
        reportDir1?.isDirectory()

        when:
        configurationCacheFails 'alsoBroken'

        then:
        def reportDir2 = reportDir()
        reportDir2?.isDirectory()
        reportDir2 != reportDir1

        when:
        configurationCacheFails 'broken'

        then:
        def reportDir3 = reportDir()
        reportDir3 == reportDir1

        when:
        configurationCacheFails 'alsoBroken'

        then:
        def reportDir4 = reportDir()
        reportDir4 == reportDir2
    }

    def "report is written to root project's buildDir"() {
        file("build.gradle") << """
            buildDir = 'out'
            tasks.register('broken') {
                doFirst { println(project.name) }
            }
        """

        when:
        configurationCacheFails 'broken'

        then:
        resolveConfigurationCacheReportDirectory(testDirectory.file('out'), failure.error)?.isDirectory()
    }

    def "state serialization errors always halt the build and invalidate the cache"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        buildFile << """
            class BrokenSerializable implements java.io.Serializable {
                private Object writeReplace() {
                    throw new RuntimeException("BOOM")
                }
            }

            class BrokenTaskType extends DefaultTask {
                final prop = new BrokenSerializable()
            }

            task broken(type: BrokenTaskType)
        """

        when:
        configurationCacheFails 'broken'

        then:
        failure.assertTasksExecuted()

        and:
        configurationCache.assertStateStoreFailed()
        outputContains("Configuration cache entry discarded due to serialization error.")
        failure.assertHasFailures(1)
        failure.assertHasFileName("Build file '${buildFile.absolutePath}'")
        failure.assertHasLineNumber(4)
        failure.assertHasDescription("Configuration cache state could not be cached: field `prop` of task `:broken` of type `BrokenTaskType`: error writing value of type 'BrokenSerializable'")
        failure.assertHasCause("BOOM")
        problems.assertResultHasProblems(failure) {
        }

        when:
        configurationCacheFails WARN_PROBLEMS_CLI_OPT, 'broken'

        then:
        failure.assertTasksExecuted()

        and:
        configurationCache.assertStateStoreFailed()
        outputContains("Configuration cache entry discarded due to serialization error.")
        failure.assertHasFailures(1)
        failure.assertHasFileName("Build file '${buildFile.absolutePath}'")
        failure.assertHasLineNumber(4)
        failure.assertHasDescription("Configuration cache state could not be cached: field `prop` of task `:broken` of type `BrokenTaskType`: error writing value of type 'BrokenSerializable'")
        failure.assertHasCause("BOOM")
        problems.assertResultHasProblems(failure) {
        }
    }

    def "state serialization errors always halts the build and earlier problems reported"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        buildFile << """
            class BrokenSerializable implements java.io.Serializable {
                private Object writeReplace() {
                    throw new RuntimeException("BOOM")
                }
            }

            class BrokenTaskType extends DefaultTask {
                final prop = new BrokenSerializable()
            }

            task problems {
                inputs.property 'brokenProperty', project
                inputs.property 'otherBrokenProperty', project
            }

            task broken(type: BrokenTaskType)
        """

        when:
        configurationCacheFails 'problems', 'broken'

        then:
        failure.assertTasksExecuted()

        and:
        configurationCache.assertStateStoreFailed()
        outputContains("Configuration cache entry discarded with 2 problems.")
        failure.assertHasFailures(1)
        failure.assertHasFileName("Build file '${buildFile.absolutePath}'")
        failure.assertHasLineNumber(4)
        failure.assertHasDescription("Configuration cache state could not be cached: field `prop` of task `:broken` of type `BrokenTaskType`: error writing value of type 'BrokenSerializable'")
        failure.assertHasCause("BOOM")
        problems.assertResultHasProblems(failure) {
            totalProblemsCount = 2
            withProblem("Task `:problems` of type `org.gradle.api.DefaultTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 0
        }

        when:
        configurationCacheFails WARN_PROBLEMS_CLI_OPT, 'problems', 'broken'

        then:
        failure.assertTasksExecuted()

        and:
        configurationCache.assertStateStoreFailed()
        outputContains("Configuration cache entry discarded with 2 problems.")
        failure.assertHasFailures(1)
        failure.assertHasFileName("Build file '${buildFile.absolutePath}'")
        failure.assertHasLineNumber(4)
        failure.assertHasDescription("Configuration cache state could not be cached: field `prop` of task `:broken` of type `BrokenTaskType`: error writing value of type 'BrokenSerializable'")
        failure.assertHasCause("BOOM")
        problems.assertResultHasProblems(failure) {
            totalProblemsCount = 2
            withProblem("Task `:problems` of type `org.gradle.api.DefaultTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 0
        }
    }

    def "serialization problems are reported and fail the build by default invalidating the cache"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        buildFile << """
            class BrokenTaskType extends DefaultTask {
                @Internal final prop = project
                @Internal final anotherProp = project.configurations
            }

            task problems {
                inputs.property 'brokenProperty', project
                inputs.property 'otherBrokenProperty', project
            }

            task moreProblems(type: BrokenTaskType)

            task ok

            task all {
                dependsOn 'problems', 'moreProblems', 'ok'
            }
        """

        when:
        configurationCacheFails 'all'

        then:
        executed(':problems', ':moreProblems', ':ok', ':all')
        configurationCache.assertStateStored(false)
        outputContains("Configuration cache entry discarded with 4 problems.")
        problems.assertFailureHasProblems(failure) {
            totalProblemsCount = 4
            withProblem("Task `:moreProblems` of type `BrokenTaskType`: cannot serialize object of type 'org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer', a subtype of 'org.gradle.api.artifacts.ConfigurationContainer', as these are not supported with the configuration cache.")
            withProblem("Task `:moreProblems` of type `BrokenTaskType`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            withProblem("Task `:problems` of type `org.gradle.api.DefaultTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 0
        }
        failure.assertHasFailures(1)

        when:
        configurationCacheRunLenient 'all'

        then:
        executed(':problems', ':moreProblems', ':ok', ':all')
        configurationCache.assertStateStored() // stored again
        problems.assertResultHasProblems(result) {
            totalProblemsCount = 8
            withProblem("Task `:moreProblems` of type `BrokenTaskType`: cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            withProblem("Task `:moreProblems` of type `BrokenTaskType`: cannot deserialize object of type 'org.gradle.api.artifacts.ConfigurationContainer' as these are not supported with the configuration cache.")
            withProblem("Task `:moreProblems` of type `BrokenTaskType`: cannot serialize object of type 'org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer', a subtype of 'org.gradle.api.artifacts.ConfigurationContainer', as these are not supported with the configuration cache.")
            withProblem("Task `:moreProblems` of type `BrokenTaskType`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            withProblem("Task `:problems` of type `org.gradle.api.DefaultTask`: cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            withProblem("Task `:problems` of type `org.gradle.api.DefaultTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 0
        }
        postBuildOutputContains("Configuration cache entry stored with 8 problems.")

        when:
        configurationCacheFails 'all'

        then:
        executed(':problems', ':moreProblems', ':ok', ':all')
        configurationCache.assertStateLoaded()
        problems.assertFailureHasProblems(failure) {
            totalProblemsCount = 4
            withProblem("Task `:moreProblems` of type `BrokenTaskType`: cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            withProblem("Task `:moreProblems` of type `BrokenTaskType`: cannot deserialize object of type 'org.gradle.api.artifacts.ConfigurationContainer' as these are not supported with the configuration cache.")
            withProblem("Task `:problems` of type `org.gradle.api.DefaultTask`: cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            problemsWithStackTraceCount = 0
        }
        outputContains("Configuration cache entry reused with 4 problems.")
    }

    def "configuration time problems are reported and fail the build by default only when configuration is executed invalidating the cache"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        buildFile << """
            gradle.buildFinished { }
            gradle.buildFinished { }

            task all
        """

        when:
        configurationCacheFails 'all'

        then:
        executed(':all')
        configurationCache.assertStateStored(false)
        outputContains("Configuration cache entry discarded with 2 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file 'build.gradle': line 2: registration of listener on 'Gradle.buildFinished' is unsupported")
            withProblem("Build file 'build.gradle': line 3: registration of listener on 'Gradle.buildFinished' is unsupported")
        }
        failure.assertHasFailures(1)

        when:
        configurationCacheRunLenient 'all'

        then:
        executed(':all')
        configurationCache.assertStateStored() // does not fail
        postBuildOutputContains("Configuration cache entry stored with 2 problems.")
        problems.assertResultHasProblems(result) {
            withProblem("Build file 'build.gradle': line 2: registration of listener on 'Gradle.buildFinished' is unsupported")
            withProblem("Build file 'build.gradle': line 3: registration of listener on 'Gradle.buildFinished' is unsupported")
        }

        when:
        configurationCacheRunLenient 'all'

        then:
        executed(':all')
        configurationCache.assertStateLoaded()
        postBuildOutputContains("Configuration cache entry reused.")
        problems.assertResultHasProblems(result) {
            // TODO - should give some indication to the user that the build may not work correctly
        }

        when:
        configurationCacheRun 'all'

        then:
        executed(':all')
        configurationCache.assertStateLoaded()
        postBuildOutputContains("Configuration cache entry reused.")
        problems.assertResultHasProblems(result) {
            // TODO - should fail and give some indication to the user why
        }
    }

    def "task execution problems are reported and fail the build by default invalidating the cache"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        buildFile << """
            task broken {
                doLast {
                    println("project = " + project.name)
                }
            }
            task anotherBroken {
                doLast {
                    println("configurations = " + project.configurations.all)
                }
            }

            task all {
                dependsOn 'broken', 'anotherBroken'
            }
        """

        when:
        configurationCacheFails 'all'

        then:
        executed(':all')
        configurationCache.assertStateStored() // does not fail
        outputContains("Configuration cache entry discarded with 2 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file 'build.gradle': line 4: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 9: invocation of 'Task.project' at execution time is unsupported.")
        }
        failure.assertHasFailures(1)

        when:
        configurationCacheRunLenient 'all'

        then:
        executed(':all')
        configurationCache.assertStateStored() // does not fail
        postBuildOutputContains("Configuration cache entry stored with 2 problems.")
        problems.assertResultHasProblems(result) {
            withProblem("Build file 'build.gradle': line 4: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 9: invocation of 'Task.project' at execution time is unsupported.")
        }

        when:
        configurationCacheRunLenient 'all'

        then:
        executed(':all')
        configurationCache.assertStateLoaded()
        postBuildOutputContains("Configuration cache entry reused with 2 problems.")
        problems.assertResultHasProblems(result) {
            withProblem("Build file 'build.gradle': line 4: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 9: invocation of 'Task.project' at execution time is unsupported.")
        }

        when:
        configurationCacheFails 'all'

        then:
        executed(':all')
        configurationCache.assertStateLoaded()
        outputContains("Configuration cache entry reused with 2 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file 'build.gradle': line 4: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 9: invocation of 'Task.project' at execution time is unsupported.")
        }
    }

    def "problems are reported and fail the build when there are other build failures invalidating the cache"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        buildFile << """
            task broken {
                doLast {
                    println("project = " + project.name)
                    throw new RuntimeException("BOOM")
                }
            }

            task all {
                dependsOn 'broken'
            }
        """

        when:
        configurationCacheFails 'all'

        then:
        configurationCache.assertStateStored() // does not fail
        outputContains("Configuration cache entry discarded with 1 problem.")
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file 'build.gradle': line 4: invocation of 'Task.project' at execution time is unsupported.")
        }
        failure.assertHasDescription("Execution failed for task ':broken'.")
        failure.assertHasCause("BOOM")
        failure.assertHasFailures(2)

        when:
        configurationCacheFails WARN_PROBLEMS_CLI_OPT, 'all'

        then:
        configurationCache.assertStateStored()
        outputContains("Configuration cache entry stored with 1 problem.")
        problems.assertResultHasProblems(result) {
            withProblem("Build file 'build.gradle': line 4: invocation of 'Task.project' at execution time is unsupported.")
        }
        failure.assertHasDescription("Execution failed for task ':broken'.")
        failure.assertHasCause("BOOM")
        failure.assertHasFailures(1)

        when:
        configurationCacheFails 'all'

        then:
        configurationCache.assertStateLoaded()
        outputContains("Configuration cache entry reused with 1 problem.")
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file 'build.gradle': line 4: invocation of 'Task.project' at execution time is unsupported.")
        }
        failure.assertHasDescription("Execution failed for task ':broken'.")
        failure.assertHasCause("BOOM")
        failure.assertHasFailures(2)
    }

    def "problems are reported and fail the build when failOnProblems is false but maxProblems is reached"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        buildFile << """
            class BrokenTask extends DefaultTask {
                @Internal
                final broken = project

                @TaskAction
                def go() {
                    try {
                        println("project = " + project)
                    } catch(Exception e) {
                        // should not happen, problems are collected
                        throw new RuntimeException("broken")
                    }
                }
            }

            task problems(type: BrokenTask)
            task moreProblems(type: BrokenTask)

            task all {
                dependsOn('problems', 'moreProblems')
            }
        """

        when:
        configurationCacheFails WARN_PROBLEMS_CLI_OPT, "$MAX_PROBLEMS_SYS_PROP=2", 'all'

        then:
        executed(':problems', ':moreProblems', ':all')
        configurationCache.assertStateStored() // does not fail
        outputContains("Configuration cache entry discarded with too many problems (6 problems).")
        problems.assertFailureHasTooManyProblems(failure) {
            withProblem("Build file 'build.gradle': line 9: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Task `:moreProblems` of type `BrokenTask`: cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            withProblem("Task `:moreProblems` of type `BrokenTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            withProblem("Task `:problems` of type `BrokenTask`: cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            withProblem("Task `:problems` of type `BrokenTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            totalProblemsCount = 6
            problemsWithStackTraceCount = 2
        }

        when:
        configurationCacheFails WARN_PROBLEMS_CLI_OPT, "$MAX_PROBLEMS_SYS_PROP=5", 'all'

        then:
        executed(':problems', ':moreProblems', ':all')
        configurationCache.assertStateStored()
        outputContains("Configuration cache entry discarded with too many problems (6 problems).")
        problems.assertFailureHasTooManyProblems(failure) {
            withProblem("Build file 'build.gradle': line 9: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Task `:moreProblems` of type `BrokenTask`: cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            withProblem("Task `:moreProblems` of type `BrokenTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            withProblem("Task `:problems` of type `BrokenTask`: cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            withProblem("Task `:problems` of type `BrokenTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            totalProblemsCount = 6
            problemsWithStackTraceCount = 2
        }
        failure.assertHasFailures(1)

        when:
        configurationCacheRun WARN_PROBLEMS_CLI_OPT, "$MAX_PROBLEMS_SYS_PROP=6", 'all'

        then:
        executed(':problems', ':moreProblems', ':all')
        configurationCache.assertStateStored()
        postBuildOutputContains("Configuration cache entry stored with 6 problems.")
        problems.assertResultHasProblems(result) {
            withProblem("Build file 'build.gradle': line 9: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Task `:moreProblems` of type `BrokenTask`: cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            withProblem("Task `:moreProblems` of type `BrokenTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            withProblem("Task `:problems` of type `BrokenTask`: cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            withProblem("Task `:problems` of type `BrokenTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            totalProblemsCount = 6
            problemsWithStackTraceCount = 2
        }

        when:
        configurationCacheRun WARN_PROBLEMS_CLI_OPT, "$MAX_PROBLEMS_SYS_PROP=4", 'all'

        then:
        executed(':problems', ':moreProblems', ':all')
        configurationCache.assertStateLoaded()
        postBuildOutputContains("Configuration cache entry reused with 4 problems.")
        problems.assertResultHasProblems(result) {
            withProblem("Build file 'build.gradle': line 9: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Task `:moreProblems` of type `BrokenTask`: cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            withProblem("Task `:problems` of type `BrokenTask`: cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            totalProblemsCount = 4
            problemsWithStackTraceCount = 2
        }
    }

    def "fails regardless of maxProblems when failOnProblems is true"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        buildFile << """
            class BrokenTask extends DefaultTask {
                @Internal
                final broken = project

                @TaskAction
                def go() {
                    println("project = " + project)
                }
            }

            task problems(type: BrokenTask)
            task moreProblems(type: BrokenTask)

            task all {
                dependsOn('problems', 'moreProblems')
            }
        """

        when:
        configurationCacheFails "$MAX_PROBLEMS_SYS_PROP=2", 'all'

        then:
        executed(':problems', ':moreProblems', ':all')
        configurationCache.assertStateStored(false)
        outputContains("Configuration cache entry discarded with 4 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file 'build.gradle': line 8: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Task `:moreProblems` of type `BrokenTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            withProblem("Task `:problems` of type `BrokenTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            totalProblemsCount = 4
            problemsWithStackTraceCount = 2
        }

        when:
        configurationCacheFails "$MAX_PROBLEMS_SYS_PROP=2000", 'all'

        then:
        executed(':problems', ':moreProblems', ':all')
        configurationCache.assertStateStored(false)
        outputContains("Configuration cache entry discarded with 4 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file 'build.gradle': line 8: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Task `:moreProblems` of type `BrokenTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            withProblem("Task `:problems` of type `BrokenTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            totalProblemsCount = 4
            problemsWithStackTraceCount = 2
        }

        when:
        configurationCacheRunLenient "$MAX_PROBLEMS_SYS_PROP=2000", 'all'

        then:
        executed(':problems', ':moreProblems', ':all')
        configurationCache.assertStateStored()
        postBuildOutputContains("Configuration cache entry stored with 6 problems.")

        when:
        configurationCacheFails "$MAX_PROBLEMS_SYS_PROP=2000", 'all'

        then:
        executed(':problems', ':moreProblems', ':all')
        configurationCache.assertStateLoaded()
        outputContains("Configuration cache entry reused with 4 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file 'build.gradle': line 8: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Task `:moreProblems` of type `BrokenTask`: cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            withProblem("Task `:problems` of type `BrokenTask`: cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.")
            totalProblemsCount = 4
            problemsWithStackTraceCount = 2
        }
    }

    def "reports #invocation access during execution"() {

        def configurationCache = newConfigurationCacheFixture()

        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            abstract class MyTask extends DefaultTask {
                @TaskAction
                def action() {
                    println($code)
                }
            }

            class MyAction implements Action<Task> {
                void execute(Task task) {
                    task.$code
                }
            }

            tasks.register("a", MyTask)
            tasks.register("b", MyTask) {
                doLast(new MyAction())
            }
            tasks.register("c") {
                doFirst(new MyAction())
            }
            tasks.register("d") {
                doFirst { $code }
            }
        """

        when:
        configurationCacheFails "a", "b", "c", "d"

        then:
        configurationCache.assertStateStored()
        outputContains("Configuration cache entry discarded with 5 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file 'build.gradle': line 11: invocation of '$invocation' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 23: invocation of '$invocation' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 5: invocation of '$invocation' at execution time is unsupported.")
            withTotalProblemsCount(5)
        }

        when:
        configurationCacheRunLenient "a", "b", "c", "d"

        then:
        configurationCache.assertStateStored()
        postBuildOutputContains("Configuration cache entry stored with 5 problems.")
        problems.assertResultHasProblems(result) {
            withProblem("Build file 'build.gradle': line 11: invocation of '$invocation' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 23: invocation of '$invocation' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 5: invocation of '$invocation' at execution time is unsupported.")
            withTotalProblemsCount(5)
        }

        when:
        configurationCacheRunLenient "a", "b", "c", "d"

        then:
        configurationCache.assertStateLoaded()
        postBuildOutputContains("Configuration cache entry reused with 5 problems.")
        problems.assertResultHasProblems(result) {
            withProblem("Build file 'build.gradle': line 11: invocation of '$invocation' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 23: invocation of '$invocation' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 5: invocation of '$invocation' at execution time is unsupported.")
            withTotalProblemsCount(5)
        }

        where:
        invocation              | code
        'Task.project'          | 'project.name'
        'Task.dependsOn'        | 'dependsOn'
        'Task.taskDependencies' | 'taskDependencies'
    }

    def "reports build listener registration on #registrationPoint"() {

        given:
        buildFile << code

        when:
        executer.noDeprecationChecks()
        configurationCacheFails 'help'

        then:
        outputContains("Configuration cache entry discarded with 1 problem.")
        problems.assertFailureHasProblems(failure) {
            withUniqueProblems("Build file 'build.gradle': line 1: registration of listener on '$registrationPoint' is unsupported")
        }

        where:
        registrationPoint                             | code
        "Gradle.addBuildListener"                     | "gradle.addBuildListener(new BuildAdapter())"
        "Gradle.addListener"                          | "gradle.addListener(new BuildAdapter())"
        "Gradle.buildFinished"                        | "gradle.buildFinished {}"
        "Gradle.useLogger"                            | "gradle.useLogger(new TaskExecutionAdapter())"
        "TaskExecutionGraph.addTaskExecutionListener" | "gradle.taskGraph.addTaskExecutionListener(new TaskExecutionAdapter())"
        "TaskExecutionGraph.beforeTask"               | "gradle.taskGraph.beforeTask {}"
        "TaskExecutionGraph.afterTask"                | "gradle.taskGraph.afterTask {}"
    }

    def "reports unsupported build listener #listenerType registration on #registrationPoint"() {
        given:
        buildFile("""
            class TestAdapter implements TestListener {
                @Override void beforeSuite(TestDescriptor suite) {}
                @Override void afterSuite(TestDescriptor suite, TestResult result) {}
                @Override void beforeTest(TestDescriptor testDescriptor) {}
                @Override void afterTest(TestDescriptor testDescriptor, TestResult result) {}
            }

            class TestOutputAdapter implements TestOutputListener {
                @Override void onOutput(TestDescriptor testDescriptor, TestOutputEvent outputEvent) {}
            }

            gradle.$registrationPoint(new ${listenerType}())
        """)

        expect:
        executer.noDeprecationChecks()
        configurationCacheFails 'help'

        where:
        [registrationPoint, listenerType] << [
            ["addListener", "useLogger"],
            ["BuildAdapter", "TaskExecutionAdapter", "TestAdapter", "TestOutputAdapter"]
        ].combinations()
    }

    def "reports registration of unsupported build listener implementing supported listener too on #registrationPoint"() {
        given:
        buildFile("""
            class SneakyListener extends TaskExecutionAdapter implements TaskExecutionGraphListener {
                @Override void graphPopulated(TaskExecutionGraph graph) {}
            }

            gradle.$registrationPoint(new SneakyListener())
        """)

        expect:
        executer.noDeprecationChecks()
        configurationCacheFails 'help'

        where:
        registrationPoint << ["addListener", "useLogger"]
    }

    def "does not report problems on configuration listener registration on #registrationPoint"() {

        given:
        buildFile << """

            class ProjectEvaluationAdapter implements ProjectEvaluationListener {
                @Override void beforeEvaluate(Project project) {}
                @Override void afterEvaluate(Project project, ProjectState state) {}
            }

            class DependencyResolutionAdapter implements DependencyResolutionListener {
                @Override void beforeResolve(ResolvableDependencies dependencies) {}
                @Override void afterResolve(ResolvableDependencies dependencies) {}
            }

            $code
        """

        expect:
        configurationCacheRun 'help'
        postBuildOutputContains("Configuration cache entry stored.")

        where:
        registrationPoint                                  | code
        "Gradle.addProjectEvaluationListener"              | "gradle.addProjectEvaluationListener(new ProjectEvaluationAdapter())"
        "Gradle.addListener(ProjectEvaluationListener)"    | "gradle.addListener(new ProjectEvaluationAdapter())"
        "Gradle.addListener(TaskExecutionGraphListener)"   | "gradle.addListener({g -> } as TaskExecutionGraphListener)"
        "Gradle.addListener(DependencyResolutionListener)" | "gradle.addListener(new DependencyResolutionAdapter())"
        "Gradle.beforeSettings"                            | "gradle.beforeSettings {}"
        "Gradle.settingsEvaluated"                         | "gradle.settingsEvaluated {}"
        "Gradle.projectsLoaded"                            | "gradle.projectsLoaded {}"
        "Gradle.beforeProject"                             | "gradle.beforeProject {}"
        "Gradle.afterProject"                              | "gradle.afterProject {}"
        "Gradle.projectsEvaluated"                         | "gradle.projectsEvaluated {}"
        "Gradle.taskGraph.whenReady"                       | "gradle.taskGraph.whenReady {}"
        "Gradle.useLogger(ProjectEvaluationListener)"      | "gradle.useLogger(new ProjectEvaluationAdapter())"
        "Gradle.useLogger(TaskExecutionGraphListener)"     | "gradle.useLogger({g -> } as TaskExecutionGraphListener)"
        "Gradle.useLogger(DependencyResolutionListener)"   | "gradle.useLogger(new DependencyResolutionAdapter())"
    }

    def "summarizes unsupported properties"() {
        given:
        buildFile << """
            class SomeBean {
                Gradle gradle
                def nested = new NestedBean()
            }

            class NestedBean {
                Gradle gradle
                Project project
            }

            class SomeTask extends DefaultTask {
                private final bean = new SomeBean()

                SomeTask() {
                    bean.gradle = project.gradle
                    bean.nested.gradle = project.gradle
                    bean.nested.project = project
                }

                @TaskAction
                void run() {
                }
            }

            // ensure there are multiple warnings for the same properties
            task a(type: SomeTask)
            task b(type: SomeTask)
            task c(dependsOn: [a, b])
        """

        when:
        configurationCacheRunLenient "c"

        then:
        postBuildOutputContains("Configuration cache entry stored with 12 problems.")
        problems.assertResultHasProblems(result) {
            withTotalProblemsCount(12)
            withUniqueProblems(
                "Task `:a` of type `SomeTask`: cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.",
                "Task `:a` of type `SomeTask`: cannot deserialize object of type 'org.gradle.api.invocation.Gradle' as these are not supported with the configuration cache.",
                "Task `:a` of type `SomeTask`: cannot serialize object of type '${DefaultProject.name}', a subtype of '${Project.name}', as these are not supported with the configuration cache.",
                "Task `:a` of type `SomeTask`: cannot serialize object of type '${DefaultGradle.name}', a subtype of '${Gradle.name}', as these are not supported with the configuration cache.",
                "Task `:b` of type `SomeTask`: cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.",
                "Task `:b` of type `SomeTask`: cannot deserialize object of type 'org.gradle.api.invocation.Gradle' as these are not supported with the configuration cache.",
                "Task `:b` of type `SomeTask`: cannot serialize object of type '${DefaultProject.name}', a subtype of '${Project.name}', as these are not supported with the configuration cache.",
                "Task `:b` of type `SomeTask`: cannot serialize object of type '${DefaultGradle.name}', a subtype of '${Gradle.name}', as these are not supported with the configuration cache.",
            )
            problemsWithStackTraceCount = 0
        }
    }

    def "can request to not fail on problems"() {
        given:
        buildFile << """
            class Bean { Project p1 }

            class FooTask extends DefaultTask {
                private final bean = new Bean()
                FooTask() { bean.p1 = project }
                @TaskAction void run() {}
            }

            task foo(type: FooTask)
        """

        when:
        run ENABLE_CLI_OPT, WARN_PROBLEMS_CLI_OPT, "foo"

        then:
        postBuildOutputContains("Configuration cache entry stored with 2 problems.")
        problems.assertResultHasProblems(result) {
            withUniqueProblems(
                "Task `:foo` of type `FooTask`: cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.",
                "Task `:foo` of type `FooTask`: cannot serialize object of type '${DefaultProject.name}', a subtype of '${Project.name}', as these are not supported with the configuration cache."
            )
            problemsWithStackTraceCount = 0
        }
    }

    def "reports problems from container all { } block"() {
        file("script.gradle") << """
            tasks.all {
                System.getProperty("PROP")
                gradle.buildFinished { }
            }
        """
        buildFile << """
            apply from: 'script.gradle'
            task ok
        """

        when:
        configurationCacheFails("ok", "-DPROP=12")

        then:
        outputContains("Configuration cache entry discarded with 17 problems")
        problems.assertFailureHasProblems(failure) {
            totalProblemsCount = 17
            withInput("Script 'script.gradle': system property 'PROP'")
            withProblem("Script 'script.gradle': line 4: registration of listener on 'Gradle.buildFinished' is unsupported")
        }
    }

    def "reports problems from deferred task configuration action block"() {
        file("script.gradle") << """
            tasks.configureEach {
                System.getProperty("PROP")
                gradle.buildFinished { }
            }
        """
        buildFile << """
            apply from: 'script.gradle'
            task ok
        """

        when:
        configurationCacheFails("ok", "-DPROP=12")

        then:
        outputContains("Configuration cache entry discarded with 1 problem.")
        problems.assertFailureHasProblems(failure) {
            withInput("Script 'script.gradle': system property 'PROP'")
            withProblem("Script 'script.gradle': line 4: registration of listener on 'Gradle.buildFinished' is unsupported")
        }
    }

    def "reports problems from afterEvaluate { } block"() {
        file("script.gradle") << """
            afterEvaluate {
                System.getProperty("PROP")
                gradle.buildFinished { }
            }
        """
        buildFile << """
            apply from: 'script.gradle'
            task ok
        """

        when:
        configurationCacheFails("ok", "-DPROP=12")

        then:
        outputContains("Configuration cache entry discarded with 1 problem.")
        problems.assertFailureHasProblems(failure) {
            withInput("Script 'script.gradle': system property 'PROP'")
            withProblem("Script 'script.gradle': line 4: registration of listener on 'Gradle.buildFinished' is unsupported")
        }
    }

    def "reports problems from various callbacks on Configuration"() {
        file("script.gradle") << """
            configurations.whenObjectAdded {
                dependencies.whenObjectAdded {
                    gradle.buildFinished { }
                }
                dependencies.matching {
                    gradle.buildFinished { }
                    true
                }.whenObjectAdded {
                    gradle.buildFinished { }
                }
                incoming.beforeResolve {
                    gradle.buildFinished { }
                }
            }
        """
        buildFile << """
            apply from: 'script.gradle'
            ${mavenCentralRepository()}
            configurations {
                thing
            }
            dependencies {
                thing "junit:junit:4.12"
            }
            task ok {
                inputs.files configurations.thing
                doFirst { }
            }
        """

        when:
        configurationCacheFails("ok")

        then:
        outputContains("Configuration cache entry discarded with 4 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("Script 'script.gradle': line 10: registration of listener on 'Gradle.buildFinished' is unsupported")
            withProblem("Script 'script.gradle': line 13: registration of listener on 'Gradle.buildFinished' is unsupported")
            withProblem("Script 'script.gradle': line 4: registration of listener on 'Gradle.buildFinished' is unsupported")
            withProblem("Script 'script.gradle': line 7: registration of listener on 'Gradle.buildFinished' is unsupported")
        }
    }

    def "reports problems in project build scripts"() {
        settingsFile << """
            include 'a'
        """
        file("a/build.gradle") << """
            gradle.buildFinished { }
            task broken {
                doFirst {
                    println project.name
                }
            }
        """

        when:
        configurationCacheFails("broken")

        then:
        outputContains("Configuration cache entry discarded with 2 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file '${relativePath('a/build.gradle')}': line 2: registration of listener on 'Gradle.buildFinished' is unsupported")
            withProblem("Build file '${relativePath('a/build.gradle')}': line 5: invocation of 'Task.project' at execution time is unsupported.")
        }
    }

    def "reports problems in project Kotlin build scripts"() {
        settingsFile << """
            include 'a'
        """
        file("a/build.gradle.kts") << """
            gradle.buildFinished { }
            tasks.register("broken") {
                doFirst {
                    println(project.name)
                }
            }
        """

        when:
        configurationCacheFails("broken")

        then:
        outputContains("Configuration cache entry discarded with 2 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file '${relativePath('a/build.gradle.kts')}': registration of listener on 'Gradle.buildFinished' is unsupported")
            withProblem("Task `:a:broken` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
        }
    }

    def "reports problems in buildSrc plugin"() {
        file("buildSrc/src/main/java/SneakyPlugin.java") << """
            import ${Project.name};
            import ${Plugin.name};

            public class SneakyPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    project.getGradle().buildFinished(r -> {});
                    project.getTasks().register("broken", t -> {
                        t.doLast(t2 -> {
                            System.out.println(t2.getProject().getName());
                        });
                    });
                }
            }
        """
        file("buildSrc/src/main/resources/META-INF/gradle-plugins/sneaky.properties") << "implementation-class: SneakyPlugin"
        buildFile << """
            plugins { id("sneaky") }
        """

        when:
        configurationCacheFails("broken")

        then:
        outputContains("Configuration cache entry discarded with 2 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("Plugin 'sneaky': registration of listener on 'Gradle.buildFinished' is unsupported")
            withProblem("Task `:broken` of type `org.gradle.api.DefaultTask`: invocation of 'Task.project' at execution time is unsupported.")
        }
    }

    def "stops reporting problems at certain limits"() {
        buildFile << """
            task all
        """
        for (i in 1..530) {
            buildFile << """
                task broken$i { t ->
                    doLast({ println ("project = " + t.project) } as Action)
                }
                tasks.all.dependsOn("broken$i")
            """
        }

        when:
        configurationCacheFails("all", "--continue")

        then:
        outputContains("Configuration cache entry discarded with 530 problems.")
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file 'build.gradle': line 5: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 50: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 500: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 505: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 510: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 515: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 520: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 525: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 530: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 535: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 540: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 545: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 55: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 550: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 555: invocation of 'Task.project' at execution time is unsupported.")
            totalProblemsCount = 530
            problemsWithStackTraceCount = 50
        }
        failure.assertHasFailure("Configuration cache problems found in this build.") { failure ->
            failure.assertHasCauses(5)
        }
    }
}
