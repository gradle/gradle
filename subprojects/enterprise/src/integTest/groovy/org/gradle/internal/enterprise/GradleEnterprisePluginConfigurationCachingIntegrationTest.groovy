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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf

import static org.gradle.internal.enterprise.GradleEnterprisePluginConfig.BuildScanRequest.NONE

// Note: most of the other tests are structure to implicitly also exercise configuration caching
// This tests some specific aspects, and serves as an early smoke test.
@IgnoreIf({ GradleContextualExecuter.instant })
class GradleEnterprisePluginConfigurationCachingIntegrationTest extends AbstractIntegrationSpec {

    def plugin = new GradleEnterprisePluginCheckInFixture(testDirectory, mavenRepo, createExecuter())

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
        buildFile << """
            def serviceRef = gradle.extensions.serviceRef
            task p {
                doLast {
                    println "extension-buildInvocationId=" + serviceRef.get()._buildState.buildInvocationId
                }
            }
        """

        when:
        succeeds "p", "--configuration-cache"
        def firstInvocationId = output.find(~/(?<=extension-buildInvocationId=).+(?=\n)/)
        succeeds "p", "--configuration-cache"
        def secondInvocationId = output.find(~/(?<=extension-buildInvocationId=).+(?=\n)/)

        then:
        firstInvocationId != secondInvocationId
    }

}
