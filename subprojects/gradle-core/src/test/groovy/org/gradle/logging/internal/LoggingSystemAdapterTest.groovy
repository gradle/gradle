/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.logging.internal

import spock.lang.Specification

import org.gradle.api.logging.LogLevel

class LoggingSystemAdapterTest extends Specification {
    private final LoggingConfigurer loggingConfigurer = Mock()
    private final LoggingSystemAdapter loggingSystem = new LoggingSystemAdapter(loggingConfigurer)

    def onUsesLoggingConfigurerToSetLoggingLevel() {
        when:
        loggingSystem.on(LogLevel.DEBUG)

        then:
        1 * loggingConfigurer.configure(LogLevel.DEBUG)
        0 * loggingConfigurer._
    }

    def offDoesNothing() {
        when:
        loggingSystem.off()

        then:
        0 * loggingConfigurer._
    }

    def restoreSetsLoggingLevelToPreviousLoggingLevel() {
        when:
        def snapshot = loggingSystem.snapshot()
        loggingSystem.restore(snapshot)

        then:
        1 * loggingConfigurer.configure(LogLevel.LIFECYCLE)
    }
    
}
