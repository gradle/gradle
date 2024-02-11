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

package org.gradle.api.logging

import spock.lang.Specification

class LogLevelTest extends Specification {
    def logLevelsAreOrderedByIncreasingSeverity() {
        expect:
        LogLevel.values().sort() == [LogLevel.DEBUG, LogLevel.INFO, LogLevel.LIFECYCLE, LogLevel.WARN, LogLevel.QUIET, LogLevel.ERROR]
        LogLevel.DEBUG < LogLevel.INFO
        LogLevel.INFO < LogLevel.LIFECYCLE
        LogLevel.LIFECYCLE < LogLevel.WARN
        LogLevel.WARN < LogLevel.QUIET
        LogLevel.QUIET < LogLevel.ERROR
    }
}
