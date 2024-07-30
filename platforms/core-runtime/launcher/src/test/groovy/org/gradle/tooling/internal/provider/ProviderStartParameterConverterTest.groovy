/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.tooling.internal.provider

import org.gradle.TaskExecutionRequest
import org.gradle.api.logging.configuration.WarningMode
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.launcher.configuration.AllProperties
import org.gradle.launcher.configuration.BuildLayoutResult
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.tooling.internal.protocol.InternalLaunchable
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters
import org.junit.Rule
import spock.lang.Specification

class ProviderStartParameterConverterTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider(getClass())
    def params = Stub(ProviderOperationParameters)
    def layout = Stub(BuildLayoutResult)
    def properties = Stub(AllProperties)

    def "allows configuring the start parameter with build arguments"() {
        params.getArguments() >> ['-m' , '--warning-mode', 'fail']

        when:
        def start = new ProviderStartParameterConverter().toStartParameter(params, layout, properties)

        then:
        start.dryRun
        start.warningMode == WarningMode.Fail
    }

    def "the start parameter is configured from properties"() {
        given:
        _ * properties.properties >> [
            (StartParameterBuildOptions.ConfigureOnDemandOption.GRADLE_PROPERTY): "true",
        ]

        when:
        def start = new ProviderStartParameterConverter().toStartParameter(params, layout, properties)

        then:
        start.configureOnDemand
    }

    abstract class LaunchableExecutionRequest implements InternalLaunchable, TaskExecutionRequest {}

    def "accepts launchables from consumer"() {
        given:
        def selector = Mock(LaunchableExecutionRequest)
        _ * selector.args >> ['myTask']
        _ * selector.projectPath >> ':child'

        params.getLaunchables() >> [selector]

        when:
        def start = new ProviderStartParameterConverter().toStartParameter(params, layout, properties)

        then:
        start.taskRequests.size() == 1
        start.taskRequests[0].projectPath == ':child'
        start.taskRequests[0].args == ['myTask']
    }

    def "cmdline properties are extracted from AllProperties"() {
        properties.requestedSystemProperties >> ["fooz": "barz"]
        properties.requestedProjectProperties >> ["foo": "bar"]

        when:
        def start = new ProviderStartParameterConverter().toStartParameter(params, layout, properties)

        then:
        start.systemPropertiesArgs['fooz'] == 'barz'
        start.projectProperties['foo'] == 'bar'
    }
}
