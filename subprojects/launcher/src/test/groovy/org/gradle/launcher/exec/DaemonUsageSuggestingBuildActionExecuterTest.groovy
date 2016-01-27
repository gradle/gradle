/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.exec

import org.gradle.StartParameter
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.logging.LogLevel
import org.gradle.initialization.BuildRequestContext
import org.gradle.internal.environment.GradleBuildEnvironment
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.service.ServiceRegistry
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutputFactory
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.launcher.daemon.configuration.DaemonUsage.*

class DaemonUsageSuggestingBuildActionExecuterTest extends Specification {
    static final String DAEMON_DOCS_URL = "gradle-daemon-docs-url"

    final BuildActionExecuter<BuildActionParameters> delegate = Mock(BuildActionExecuter)
    final StyledTextOutput textOutput = Mock()
    final StyledTextOutputFactory textOutputFactory = Mock() {
        create(DaemonUsageSuggestingBuildActionExecuter, LogLevel.LIFECYCLE) >> textOutput
    }
    final GradleBuildEnvironment buildEnvironment = Mock()
    final DocumentationRegistry documentationRegistry = Mock() {
        getDocumentationFor("gradle_daemon") >> DAEMON_DOCS_URL
    }

    final OperatingSystem os = Mock(OperatingSystem)
    final DaemonUsageSuggestingBuildActionExecuter executer = new DaemonUsageSuggestingBuildActionExecuter(delegate, textOutputFactory, documentationRegistry, os)
    final StartParameter startParameter = Mock()
    final BuildAction action = Mock() {
        getStartParameter() >> startParameter
    }
    final BuildRequestContext buildRequestContext = Mock()
    final BuildActionParameters params = Mock()
    final ServiceRegistry serviceRegistry = Mock()

    def "delegates execution to the underlying executer"() {
        given:
        def executionResult = new Object()
        delegate.execute(action, buildRequestContext, params, serviceRegistry) >> executionResult
        params.daemonUsage >> EXPLICITLY_ENABLED

        when:
        def result = executer.execute(action, buildRequestContext, params, serviceRegistry)

        then:
        result == executionResult
    }

    def "suggests using daemon when not on windows, daemon usage is not explicitly specified and CI env var is not specified"() {
        given:
        params.daemonUsage >> IMPLICITLY_DISABLED
        params.envVariables >> [CI: null]
        os.windows >> false

        when:
        executer.execute(action, buildRequestContext, params, serviceRegistry)

        then:
        1 * textOutput.println()

        and:
        1 * textOutput.println(DaemonUsageSuggestingBuildActionExecuter.PLEASE_USE_DAEMON_MESSAGE_PREFIX + DAEMON_DOCS_URL)
    }

    @Unroll
    def "does not suggest using daemon [#daemonUsage, #ciEnvValue, #isWindows]"() {
        given:
        params.daemonUsage >> daemonUsage
        params.getEnvVariables() >> [CI: ciEnvValue]
        os.windows >> isWindows

        when:
        executer.execute(action, buildRequestContext, params, serviceRegistry)

        then:
        0 * textOutput._

        where:
        daemonUsage         | ciEnvValue | isWindows
        IMPLICITLY_DISABLED | null       | true
        IMPLICITLY_DISABLED | "true"     | true
        IMPLICITLY_DISABLED | "true"     | false
        EXPLICITLY_DISABLED | null       | true
        EXPLICITLY_DISABLED | null       | false
        EXPLICITLY_DISABLED | "true"     | true
        EXPLICITLY_DISABLED | "true"     | false
        EXPLICITLY_ENABLED  | null       | true
        EXPLICITLY_ENABLED  | null       | false
        EXPLICITLY_ENABLED  | "true"     | true
        EXPLICITLY_ENABLED  | "true"     | false
    }
}
