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

package org.gradle.tooling.internal.provider.connection

import org.gradle.api.logging.LogLevel
import org.gradle.tooling.internal.protocol.BuildOperationParametersVersion1
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 3/6/12
 */
class AdaptedOperationParametersTest extends Specification {

    interface BuildOperationParametersStub extends BuildOperationParametersVersion1 {
        List<String> getArguments()
    } 
    
    def delegate = Mock(BuildOperationParametersStub)
    def params = new AdaptedOperationParameters(delegate)

    def "configures build log level to debug if verbose logging requested"() {
        given:
        delegate.getVerboseLogging() >> true

        when:
        def level = params.getBuildLogLevel()

        then:
        level == LogLevel.DEBUG
    }

    def "uses log level from the arguments if verbose logging not configured"() {
        given:
        delegate.getArguments() >> ['--info']
        delegate.getVerboseLogging() >> false

        when:
        def level = params.getBuildLogLevel()

        then:
        level == LogLevel.INFO
    }

    def "uses lifecycle log level if verbose logging not configured"() {
        given:
        delegate.getArguments() >> []
        delegate.getVerboseLogging() >> false

        when:
        def level = params.getBuildLogLevel()

        then:
        //depends on implementation of the CommandLineConverter and the global default
        //but if feels important to validate it
        level == LogLevel.LIFECYCLE
    }
}
