/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.tooling.internal.provider.connection

import org.gradle.api.logging.LogLevel
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 1/18/13
 */
class BuildLogLevelMixInTest extends Specification {

    final parameters = Mock(ProviderOperationParameters)
    final mixin = new BuildLogLevelMixIn(parameters)

    def "knows build log level for mixed set of arguments"() {
        when:
        parameters.getArguments([]) >> args
        parameters.getVerboseLogging(false) >> false

        then:
        mixin.getBuildLogLevel() == logLevel

        where:
        args                     | logLevel
        ['-i']                   | LogLevel.INFO
        ['-q']                   | LogLevel.QUIET
        ['foo', '--info', 'bar'] | LogLevel.INFO
        ['-i', 'foo', 'bar']     | LogLevel.INFO
        ['foo', 'bar', '-i']     | LogLevel.INFO
    }

    def "verbose flag is only used when no log level arguments"() {
        when:
        parameters.getArguments([]) >> args
        parameters.getVerboseLogging(false) >> verbose

        then:
        mixin.getBuildLogLevel() == logLevel

        where:
        args                     | verbose | logLevel
        ['-q']                   | false   | LogLevel.QUIET
        ['-q']                   | true    | LogLevel.QUIET
        ['noLogLevelArguments']  | true    | LogLevel.DEBUG
    }

    def "default log level is lifecycle"() {
        when:
        parameters.getArguments([]) >> ['no log level arguments']
        parameters.getVerboseLogging(false) >> false

        then:
        mixin.getBuildLogLevel() == LogLevel.LIFECYCLE
    }
}
