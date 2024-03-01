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

package org.gradle.launcher.cli

import org.gradle.api.Action
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.LoggingConfiguration
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.internal.logging.DefaultLoggingConfiguration
import org.gradle.internal.logging.ToStringLogger
import org.gradle.launcher.bootstrap.ExecutionListener
import spock.lang.Specification

class DebugLoggerWarningActionTest extends Specification {

    ToStringLogger log
    LoggingConfiguration loggingConfiguration
    Action<ExecutionListener> delegateAction
    ExecutionListener listener

    def setup() {
        log = new ToStringLogger()
        loggingConfiguration = new DefaultLoggingConfiguration()
        delegateAction = Mock()
        listener = Mock()
    }

    private DebugLoggerWarningAction createDebugLoggerWarning() {
        return new DebugLoggerWarningAction(log, loggingConfiguration, delegateAction)
    }

    private void assertDoublePrintInOutput(String output) {
        // This should exist twice
        assert output.count(DebugLoggerWarningAction.WARNING_MESSAGE_BODY) == 2
    }

    def "prints twice when debugging is enabled"() {
        given:
        loggingConfiguration.setLogLevel(LogLevel.DEBUG)
        def action = createDebugLoggerWarning()
        when:
        action.execute(listener)
        then:
        def output = TextUtil.normaliseFileSeparators(log.toString())
        assertDoublePrintInOutput(output)
        1 * delegateAction.execute(_)
    }

    def "prints twice when debugging is enabled and an exception is thrown"() {
        delegateAction.execute(listener) >> { throw new RuntimeException("Boom!") }

        given:
        loggingConfiguration.setLogLevel(LogLevel.DEBUG)
        def action = createDebugLoggerWarning()

        when:
        action.execute(listener)

        then:
        thrown(RuntimeException)
        def output = TextUtil.normaliseFileSeparators(log.toString())
        assertDoublePrintInOutput(output)
    }
}
