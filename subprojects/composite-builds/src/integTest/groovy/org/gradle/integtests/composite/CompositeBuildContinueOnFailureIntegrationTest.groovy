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

import org.gradle.integtests.fixtures.build.BuildTestFile

/**
 * Tests for composite build delegating to tasks in an included build.
 */
class CompositeBuildContinueOnFailureIntegrationTest extends AbstractCompositeBuildIntegrationTest {
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
                    shouldRunAfter fails
                }
                task checkContinueFlag {
                    doLast {
                        println "continueOnFailure = " + gradle.startParameter.continueOnFailure
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

    def "aborts build when delegated task fails"() {
        when:
        buildA.buildFile << """
    task delegate {
        dependsOn gradle.includedBuild('buildB').task(':fails')
        dependsOn gradle.includedBuild('buildC').task(':succeeds')
    }
"""

        fails(buildA, ":delegate")

        then:
        executed ":buildB:fails"
        notExecuted ":buildC:succeeds"
    }

    def "aborts build when delegated task in same build fails"() {
        when:
        buildA.buildFile << """
    task delegate {
        dependsOn gradle.includedBuild('buildB').task(':fails')
        dependsOn gradle.includedBuild('buildB').task(':succeeds')
    }
"""

        fails(buildA, ":delegate")

        then:
        executed ":buildB:fails"
        notExecuted ":buildB:succeeds"
    }

    def "continues build when delegated task fails when run with --continue"() {
        when:
        buildA.buildFile << """
    task delegate {
        dependsOn gradle.includedBuild('buildB').task(':fails')
        dependsOn gradle.includedBuild('buildC').task(':succeeds')
        dependsOn gradle.includedBuild('buildB').task(':succeeds')
    }
"""
        executer.withArguments("--continue")
        fails(buildA, ":delegate")

        then:
        executed ":buildB:fails", ":buildC:succeeds", ":buildB:succeeds"
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
        executer.withArguments("--continue")
        fails(buildA, ":delegate")

        then:
        executed ":buildB:checkContinueFlag", ":buildB:fails", ":buildB:succeeds"
        outputContains("continueOnFailure = true")
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
        executed ":buildB:jar", ":buildB:checkContinueFlag"
        outputContains("continueOnFailure = true")
    }
}
