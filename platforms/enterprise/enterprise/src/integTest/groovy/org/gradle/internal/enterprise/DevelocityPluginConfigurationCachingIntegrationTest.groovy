/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.enterprise

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.internal.TextUtil
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

import static org.gradle.internal.enterprise.GradleEnterprisePluginConfig.BuildScanRequest.NONE
import static org.gradle.internal.enterprise.GradleEnterprisePluginConfig.BuildScanRequest.REQUESTED
import static org.gradle.internal.enterprise.GradleEnterprisePluginConfig.BuildScanRequest.SUPPRESSED

// Note: most of the other tests are structure to implicitly also exercise configuration caching
// This tests some specific aspects, and serves as an early smoke test.
@Requires(IntegTestPreconditions.NotConfigCached)
class DevelocityPluginConfigurationCachingIntegrationTest extends AbstractIntegrationSpec {

    def plugin = new DevelocityPluginCheckInFixture(testDirectory, mavenRepo, createExecuter())

    def setup() {
        settingsFile << plugin.pluginManagement() << plugin.plugins()
        plugin.publishDummyPlugin(executer)
        buildFile << """
            task t
        """
    }

    def "registered service factory is invoked for from cache build"() {
        when:
        succeeds "t", "--configuration-cache"

        then:
        plugin.appliedOnce(output)
        plugin.assertBuildScanRequest(output, NONE)

        when:
        succeeds "t", "--configuration-cache"

        then:
        plugin.notApplied(output)
        plugin.assertBuildScanRequest(output, NONE)
    }

    def "returned service ref refers to service for that build"() {
        given:
        def taskName = "printInvocationId"
        buildFile << taskPrintBuildInvocationId(taskName)

        when:
        succeeds taskName, "--configuration-cache"
        def firstInvocationId = output.find(~/(?<=extension-buildInvocationId=).+(?=\n)/)
        succeeds taskName, "--configuration-cache"
        def secondInvocationId = output.find(~/(?<=extension-buildInvocationId=).+(?=\n)/)

        then:
        firstInvocationId != secondInvocationId
    }

    def "returned service ref for included build refers to service for the root build"() {
        given:
        def rootTaskName = "printInvocationId"
        def includedTaskName = "includedPrintInvocationId"

        and:
        buildTestFixture.withBuildInSubDir()
        singleProjectBuild("included") {
            settingsFile.prepend(plugin.plugins())
            settingsFile.prepend(plugin.pluginManagement())
            buildFile << taskPrintRootBuildInvocationId(includedTaskName)
        }

        and:
        settingsFile << "includeBuild('included')"
        buildFile << taskPrintBuildInvocationId(rootTaskName) << """
            tasks.$rootTaskName {
                dependsOn(gradle.includedBuild("included").task(":$includedTaskName"))
            }
        """

        when:
        succeeds rootTaskName, "--configuration-cache"
        def invocationIds = output.findAll(~/(?<=extension-buildInvocationId=).+(?=\n)/)

        then:
        invocationIds[0] == invocationIds[1]
    }

    def "--scan does not auto apply plugin for build from cache"() {
        when:
        succeeds "t", "--configuration-cache", "--scan"

        then:
        plugin.appliedOnce(output)
        plugin.assertBuildScanRequest(output, REQUESTED)

        when:
        succeeds "t", "--configuration-cache", "--scan"

        // If this stops working and the auto apply does happen,
        // the above will fail as the check in point is only expecting one plugin
        // and two will check in.

        then:
        plugin.notApplied(output)
        plugin.assertBuildScanRequest(output, REQUESTED)
    }

    @Issue('https://github.com/gradle/gradle/issues/24163')
    @ToBeImplemented
    def "does not consider scan arg part of key and provides value to service"() {
        when:
        succeeds "t", "--configuration-cache"

        then:
        plugin.appliedOnce(output)
        plugin.assertBuildScanRequest(output, NONE)

        when:
        succeeds "t", "--configuration-cache", "--scan"

        then:
        // TODO plugin.notApplied(output)
        plugin.appliedOnce(output)

        plugin.assertBuildScanRequest(output, REQUESTED)

        when:
        succeeds "t", "--configuration-cache", "--no-scan"

        then:
        // TODO plugin.notApplied(output)
        plugin.appliedOnce(output)
        plugin.assertBuildScanRequest(output, SUPPRESSED)
    }

    def "can use input handler when from cache"() {
        given:
        buildFile << """
            def serviceRef = gradle.serviceRef
            task read {
                doLast {
                    def response =  serviceRef.get()._requiredServices.userInputHandler.askYesNoQuestion("there?")
                    println "response: \$response"
                }
            }
        """

        when:
        runInteractive("read", "yes")

        then:
        output.contains("response: true")

        when:
        runInteractive("read", "no")

        then:
        output.contains("response: false")
    }

    private void runInteractive(String task, String answer) {
        executer.withForceInteractive(true)
        executer.withStdinPipe()
        executer.withTasks(task, "--configuration-cache")
        def handle = executer.start()
        ConcurrentTestUtil.poll(60) {
            assert handle.standardOutput.contains("there? [yes, no]")
        }
        handle.stdinPipe.write((answer + TextUtil.platformLineSeparator).bytes)
        handle.stdinPipe.close()
        result = handle.waitForFinish()
    }

    def "exposes correct start parameter"() {
        given:
        buildFile << """
            def serviceRef = gradle.serviceRef
            t.doLast {
                println "offline: " + serviceRef.get()._buildState.startParameter.offline
            }
        """
        when:
        succeeds "t", "--configuration-cache"

        then:
        output.contains("offline: false")

        when:
        succeeds "t", "--configuration-cache", "--offline"

        then:
        output.contains("offline: true")
    }

    private String taskPrintBuildInvocationId(String taskName) {
        """
            def serviceRef = gradle.extensions.serviceRef
            task $taskName {
                doLast {
                    println "extension-buildInvocationId=" + serviceRef.get()._buildState.buildInvocationId
                }
            }
        """
    }

    private String taskPrintRootBuildInvocationId(String taskName) {
        """
            def rootServiceRef() {
                def rootGradle = gradle
                while (rootGradle.parent != null) {
                    rootGradle = gradle.parent
                }
                rootGradle.extensions.serviceRef
            }

            def serviceRef = rootServiceRef()
            task $taskName {
                doLast {
                    println "extension-buildInvocationId=" + serviceRef.get()._buildState.buildInvocationId
                }
            }
        """
    }
}
