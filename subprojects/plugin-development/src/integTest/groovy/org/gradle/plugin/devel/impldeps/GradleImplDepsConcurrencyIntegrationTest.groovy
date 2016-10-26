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

class GradleImplDepsConcurrencyIntegrationTest extends BaseGradleImplDepsIntegrationTest {

    private static final int CONCURRENT_BUILDS_PROJECT_COUNT = 4
    private static final int CONCURRENT_TASKS_PROJECT_COUNT = 4

    def setup() {
        requireOwnGradleUserHomeDir()
    }

    def "Gradle API and TestKit dependency can be resolved and used by concurrent Gradle builds"() {
        given:
        setupProjects(CONCURRENT_BUILDS_PROJECT_COUNT) { projectDirName, buildFile ->
            buildFile << testableGroovyProject()
            file("$projectDirName/src/test/groovy/MyTest.groovy") << gradleApiAndTestKitClassLoadingTestClass()
        }

        when:
        def outputs = executeBuildsConcurrently(CONCURRENT_BUILDS_PROJECT_COUNT, 'test')

        then:
        assertConcurrentBuildsOutput(outputs)
    }

    def "Gradle API and TestKit dependency JAR files are the same when run by concurrent Gradle builds"() {
        given:
        setupProjects(CONCURRENT_BUILDS_PROJECT_COUNT) { projectDirName, buildFile ->
            buildFile << resolveGradleApiAndTestKitDependencies()
        }

        when:
        def outputs = executeBuildsConcurrently(CONCURRENT_BUILDS_PROJECT_COUNT, 'resolveDependencies')

        then:
        assertConcurrentBuildsOutput(outputs)
        assertSameDependencyChecksums(CONCURRENT_BUILDS_PROJECT_COUNT)
    }

    def "Gradle API and TestKit dependency can be resolved and used by concurrent tasks within one build"() {
        given:
        setupProjects(CONCURRENT_TASKS_PROJECT_COUNT) { projectDirName, buildFile ->
            buildFile << testableGroovyProject()
            file("$projectDirName/src/test/groovy/MyTest.groovy") << gradleApiAndTestKitClassLoadingTestClass()
        }

        setupSettingsFile(CONCURRENT_TASKS_PROJECT_COUNT)

        when:
        executeBuildInParallel('test')

        then:
        assertApiGenerationOutput(output)
        assertTestKitGenerationOutput(output)
    }

    def "Gradle API and TestKit dependency JAR files are the same when run by concurrent tasks within one build"() {
        given:
        setupProjects(CONCURRENT_TASKS_PROJECT_COUNT) { projectDirName, buildFile ->
            file("$projectDirName/build.gradle") << resolveGradleApiAndTestKitDependencies()
        }

        setupSettingsFile(CONCURRENT_TASKS_PROJECT_COUNT)

        when:
        executeBuildInParallel('resolveDependencies')

        then:
        assertApiGenerationOutput(output)
        assertTestKitGenerationOutput(output)
        assertSameDependencyChecksums(CONCURRENT_TASKS_PROJECT_COUNT)
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
                        org.gradle.internal.hash.HashUtil.createHash(it, 'MD5').asByteArray().encodeHex().toString()
                    }.join(',')
                }
            }
        """
    }

    static String gradleApiAndTestKitClassLoadingTestClass() {
        """
            class MyTest extends groovy.util.GroovyTestCase {

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

    private List<String> executeBuildsConcurrently(int projectCount, String... taskNames) {
        def handles = []
        (1..projectCount).each { count ->
            handles << executer.inDirectory(file(createProjectName(count))).withTasks(taskNames).start()
        }

        def outputs = []
        handles.each { handle ->
            handle.waitForFinish()
            outputs << handle.standardOutput
        }

        outputs
    }

    private void executeBuildInParallel(String... taskNames) {
        executer.withBuildJvmOpts('-Xmx1024m')
        args('--parallel')
        succeeds taskNames
    }

    static void assertConcurrentBuildsOutput(List<String> outputs) {
        def apiGenerationOutputs = outputs.findAll { it =~ /$API_JAR_GENERATION_OUTPUT_REGEX/ }
        def testKitGenerationOutputs = outputs.findAll { it =~ /$TESTKIT_GENERATION_OUTPUT_REGEX/ }
        assert apiGenerationOutputs.size() == 1
        assert testKitGenerationOutputs.size() == 1
        assertApiGenerationOutput(apiGenerationOutputs[0])
        assertTestKitGenerationOutput(testKitGenerationOutputs[0])
    }

    private void assertSameDependencyChecksums(int projectCount) {
        def checksums = (1..projectCount).collect { count ->
            def projectDirName = file(createProjectName(count)).name
            file("$projectDirName/deps.txt").text.split(',') as Set
        }

        def singleChecksum = checksums.first()
        assert checksums.findAll { it == singleChecksum }.size() == projectCount
    }

    static String createProjectName(int projectNo) {
        "project$projectNo"
    }

    static void assertApiGenerationOutput(String output) {
        assertSingleGenerationOutput(output, API_JAR_GENERATION_OUTPUT_REGEX)
    }

    static void assertTestKitGenerationOutput(String output) {
        assertSingleGenerationOutput(output, TESTKIT_GENERATION_OUTPUT_REGEX)
    }
}
