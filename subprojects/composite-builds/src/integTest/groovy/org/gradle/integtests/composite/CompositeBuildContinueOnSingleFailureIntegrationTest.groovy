/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.composite

import org.gradle.initialization.StartParameterBuildOptions.ContinueOption
import org.gradle.integtests.fixtures.build.BuildTestFile
import spock.lang.Issue

/**
 * Tests for composite build delegating to tasks in an included build.
 */
class CompositeBuildContinueOnSingleFailureIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    private static final String CONTINUE_COMMAND_LINE_OPTION = "--$ContinueOption.LONG_OPTION"
    BuildTestFile buildB
    BuildTestFile buildC

    def setup() {
        buildB = singleProjectBuild("buildB") {
            buildFile << """
                task fails {
                    doLast {
                        throw new RuntimeException("failed")
                    }
                }
                task succeeds {
                }
                task checkContinueFlag {
                    def continueFlag = gradle.startParameter.continueOnFailure
                    doLast {
                        println "continueOnFailure = " + continueFlag
                    }
                }
"""
        }
        buildC = singleProjectBuild("buildC") {
            buildFile << """
                task succeeds
"""
        }
        includedBuilds << buildB << buildC
    }

    def "aborts build when delegated task in same build fails"() {
        when:
        buildA.buildFile << """
    task delegate {
        dependsOn gradle.includedBuild('buildB').task(':fails')
        dependsOn gradle.includedBuild('buildB').task(':succeeds')
    }
"""
        buildB.buildFile << """
    // force sequential execution
    tasks.succeeds.mustRunAfter tasks.fails
"""
        fails(buildA, ":delegate")

        then:
        failure.assertHasFailures(1)
        assertTaskExecuted(":buildB", ":fails")
        assertTaskNotExecuted(":buildB", ":succeeds")
        assertTaskNotExecuted(":", ":delegate")
    }

    def "attempts all dependencies when run with --continue when one delegated task dependency fails"() {
        when:
        buildA.buildFile << """
    task delegate {
        dependsOn gradle.includedBuild('buildB').task(':fails')
        dependsOn gradle.includedBuild('buildC').task(':succeeds')
        dependsOn gradle.includedBuild('buildB').task(':succeeds')
    }
"""
        executer.withArguments(CONTINUE_COMMAND_LINE_OPTION)
        fails(buildA, ":delegate")

        then:
        failure.assertHasFailures(1)
        assertTaskExecutedOnce(":buildB", ":fails")
        assertTaskExecutedOnce(":buildC", ":succeeds")
        assertTaskExecutedOnce(":buildB", ":succeeds")
        assertTaskNotExecuted(":", ":delegate")
    }

    @Issue("https://github.com/gradle/gradle/issues/2520")
    def "continues build when delegated task fails when run with --continue"() {
        when:
        buildA.buildFile << """
    task delegateWithFailure {
        dependsOn gradle.includedBuild('buildB').task(':fails')
    }
    task delegateWithSuccess {
        dependsOn gradle.includedBuild('buildB').task(':succeeds')
    }
    task delegate {
        dependsOn delegateWithSuccess, delegateWithFailure
    }
"""
        executer.withArguments(CONTINUE_COMMAND_LINE_OPTION)
        fails(buildA, ":delegate")

        then:
        failure.assertHasFailures(1)
        assertTaskExecutedOnce(":buildB", ":fails")
        assertTaskExecutedOnce(":buildB", ":succeeds")
        assertTaskExecutedOnce(":", ":delegateWithSuccess")
        assertTaskNotExecuted(":", ":delegateWithFailure")
    }

    def "executes delegate task with --continue"() {
        when:
        buildB.buildFile << """
    task included {
        dependsOn 'fails', 'succeeds', 'checkContinueFlag'
    }
"""
        buildA.buildFile << """
    task delegate {
        dependsOn gradle.includedBuild('buildB').task(':included')
    }
"""
        executer.withArguments(CONTINUE_COMMAND_LINE_OPTION)
        fails(buildA, ":delegate")

        then:
        outputContains("continueOnFailure = true")

        failure.assertHasFailures(1)
        assertTaskExecutedOnce(":buildB", ":checkContinueFlag")
        assertTaskExecutedOnce(":buildB", ":fails")
        assertTaskExecutedOnce(":buildB", ":succeeds")
        assertTaskNotExecuted(":buildB", ":included")
        assertTaskNotExecuted(":", ":delegate")
    }

    def "passes continueOnFailure flag when building dependency artifact"() {
        when:
        buildB.buildFile << """
            apply plugin: 'java'

            jar.dependsOn 'checkContinueFlag'
"""
        dependency "org.test:buildB:1.0"

        executer.withArguments("--continue")
        execute(buildA, ":assemble")

        then:
        outputContains("continueOnFailure = true")

        assertTaskExecutedOnce(":buildB", ":checkContinueFlag")
        assertTaskExecutedOnce(":buildB", ":jar")
    }
}
