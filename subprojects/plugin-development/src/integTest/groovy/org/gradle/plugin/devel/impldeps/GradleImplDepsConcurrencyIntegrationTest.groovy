/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.devel.impldeps

import org.gradle.api.Plugin

//@Requires(UnitTestPreconditions.HighPerformance)
class GradleImplDepsConcurrencyIntegrationTest extends BaseGradleImplDepsIntegrationTest {

    private static final int CONCURRENT_BUILDS_PROJECT_COUNT = 4
    private static final int CONCURRENT_TASKS_PROJECT_COUNT = 4

    def "Gradle API and TestKit dependency can be resolved and used by concurrent Gradle builds"() {
        given:
        setupProjects(CONCURRENT_BUILDS_PROJECT_COUNT) { projectDirName, buildFile ->
            buildFile << testablePluginProject()
            file("$projectDirName/src/test/groovy/MyTest.groovy") << gradleApiAndTestKitClassLoadingTestClass()
        }

        expect:
        concurrentBuildSucceed(CONCURRENT_BUILDS_PROJECT_COUNT, 'test')
    }

    def "Gradle API and TestKit dependency JAR files are the same when run by concurrent Gradle builds"() {
        given:
        setupProjects(CONCURRENT_BUILDS_PROJECT_COUNT) { projectDirName, buildFile ->
            buildFile << resolveGradleApiAndTestKitDependencies()
        }

        expect:
        concurrentBuildSucceed(CONCURRENT_BUILDS_PROJECT_COUNT, 'resolveDependencies')
    }

    def "Gradle API and TestKit dependency can be resolved and used by concurrent tasks within one build"() {
        given:
        setupProjects(CONCURRENT_TASKS_PROJECT_COUNT) { projectDirName, buildFile ->
            buildFile << testablePluginProject()
            file("$projectDirName/src/test/groovy/MyTest.groovy") << gradleApiAndTestKitClassLoadingTestClass()
        }

        setupSettingsFile(CONCURRENT_TASKS_PROJECT_COUNT)

        expect:
        parallelBuildSucceeds('test')
    }

    def "Gradle API and TestKit dependency JAR files are the same when run by concurrent tasks within one build"() {
        given:
        setupProjects(CONCURRENT_TASKS_PROJECT_COUNT) { projectDirName, buildFile ->
            file("$projectDirName/build.gradle") << resolveGradleApiAndTestKitDependencies()
        }

        setupSettingsFile(CONCURRENT_TASKS_PROJECT_COUNT)

        expect:
        parallelBuildSucceeds('resolveDependencies')
    }

    static String resolveGradleApiAndTestKitDependencies() {
        """
            configurations {
                gradleImplDeps
            }

            dependencies {
                gradleImplDeps gradleApi(), gradleTestKit()
            }

            task resolveDependencies {
                doLast {
                    def files = configurations.gradleImplDeps.resolve()
                    file('deps.txt').text = files.collect {
                        org.gradle.internal.hash.Hashing.md5().hashFile(it).toString()
                    }.join(',')
                }
            }
        """
    }

    static String gradleApiAndTestKitClassLoadingTestClass() {
        """
            class MyTest extends groovy.test.GroovyTestCase {

                void testUsageOfGradleApiAndTestKitClasses() {
                    def classLoader = getClass().classLoader
                    classLoader.loadClass('${Plugin.class.getName()}')
                    classLoader.loadClass('org.gradle.testkit.runner.GradleRunner')
                }
            }
        """
    }

    private void setupProjects(int projectCount, Closure c) {
        (1..projectCount).each {
            def projectDirName = file(createProjectName(it)).name
            def buildFile = file("$projectDirName/build.gradle")
            c(projectDirName, buildFile)
        }
    }

    private void setupSettingsFile(int projectCount) {
        file('settings.gradle') << "include ${(1..projectCount).collect { "'${createProjectName(it)}'" }.join(',')}"
    }

    private void concurrentBuildSucceed(int projectCount, String... taskNames) {
        def handles = []
        (1..projectCount).each { count ->
            handles << executer.inDirectory(file(createProjectName(count))).withTasks(taskNames).start()
        }
        handles.each { handle ->
            handle.waitForFinish()
        }
    }

    private void parallelBuildSucceeds(String... taskNames) {
        executer.withBuildJvmOpts('-Xmx1024m')
        args('--parallel')
        succeeds taskNames
    }

    static String createProjectName(int projectNo) {
        "project$projectNo"
    }
}
