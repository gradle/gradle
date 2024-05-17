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

class BuildLogLevelMixInTest extends Specification {

    final parameters = Mock(ProviderOperationParameters)

    def "knows build log level for mixed set of arguments (or not arguments)"() {
        when:
        parameters.getArguments() >> args
        parameters.getVerboseLogging() >> verbose
        def mixin = new BuildLogLevelMixIn(parameters)

        then:
        mixin.getBuildLogLevel() == logLevel

        where:
        args                                | verbose | logLevel
        ['-i']                              | false   | LogLevel.INFO
        ['no log level arguments']          | false   | null
        null                                | false   | null
        ['-q']                              | false   | LogLevel.QUIET
        ['-w']                              | false   | LogLevel.WARN
        ['foo', '--info', 'bar']            | false   | LogLevel.INFO
        ['-i', 'foo', 'bar']                | false   | LogLevel.INFO
        ['foo', 'bar', '-i']                | false   | LogLevel.INFO
        ['-Dorg.gradle.logging.level=info'] | false   | LogLevel.INFO
        ['-q']                              | true    | LogLevel.QUIET
        ['no log level arguments']          | true    | LogLevel.DEBUG
        null                                | true    | LogLevel.DEBUG

    }
}
