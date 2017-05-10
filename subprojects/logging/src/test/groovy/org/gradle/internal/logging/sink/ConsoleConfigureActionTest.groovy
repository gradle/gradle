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

package org.gradle.internal.logging.sink

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.internal.logging.DefaultLoggingConfiguration
import org.gradle.util.UsesNativeServices
import spock.lang.Specification
import spock.lang.Unroll

@UsesNativeServices
class ConsoleConfigureActionTest extends Specification {

    def outputEventRenderer = new OutputEventRenderer()

    @Unroll
    def "changes default log level if console output is #consoleOutput and default log level is configured"() {
        given:
        outputEventRenderer.configure(DefaultLoggingConfiguration.DEFAULT_LOG_LEVEL)

        when:
        ConsoleConfigureAction.execute(outputEventRenderer, consoleOutput)

        then:
        outputEventRenderer.logLevel == LogLevel.LIFECYCLE

        where:
        consoleOutput << [ConsoleOutput.Plain, ConsoleOutput.Auto]
    }

    @Unroll
    def "does not change default log level if console output is Plain and log level is #logLevel"() {
        given:
        outputEventRenderer.configure(logLevel)

        when:
        ConsoleConfigureAction.execute(outputEventRenderer, ConsoleOutput.Plain)

        then:
        outputEventRenderer.logLevel == logLevel

        where:
        logLevel << [LogLevel.QUIET, LogLevel.LIFECYCLE, LogLevel.ERROR, LogLevel.INFO, LogLevel.DEBUG]
    }

    @Unroll
    def "does not change default log level if console output is Auto and log level is #logLevel"() {
        given:
        outputEventRenderer.configure(logLevel)

        when:
        ConsoleConfigureAction.execute(outputEventRenderer, ConsoleOutput.Auto)

        then:
        outputEventRenderer.logLevel == logLevel

        where:
        logLevel << [LogLevel.QUIET, LogLevel.LIFECYCLE, LogLevel.ERROR, LogLevel.INFO, LogLevel.DEBUG]
    }

    def "does not change default log level if console output is Rich"() {
        given:
        outputEventRenderer.configure(DefaultLoggingConfiguration.DEFAULT_LOG_LEVEL)
        outputEventRenderer.attachSystemOutAndErr()

        when:
        ConsoleConfigureAction.execute(outputEventRenderer, ConsoleOutput.Rich)

        then:
        outputEventRenderer.logLevel == DefaultLoggingConfiguration.DEFAULT_LOG_LEVEL
    }
}
