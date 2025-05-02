/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.logging

import spock.lang.Specification

import static org.gradle.api.tasks.testing.logging.TestLogEvent.*
import static org.gradle.api.tasks.testing.logging.TestStackTraceFilter.GROOVY
import static org.gradle.api.tasks.testing.logging.TestStackTraceFilter.TRUNCATE

class DefaultTestLoggingTest extends Specification {
    private logging = new DefaultTestLogging()

    def "has defaults"() {
        expect:
        logging.events == [] as Set
        logging.minGranularity == -1
        logging.maxGranularity == -1
        logging.stackTraceFilters == [TRUNCATE] as Set
    }

    def "allows events to be added as enum values"() {
        when:
        logging.events STARTED, SKIPPED

        then:
        logging.events == [STARTED, SKIPPED] as Set
    }

    def "allows events to be added as string values"() {
        when:
        logging.events "started", "skipped"

        then:
        logging.events == [STARTED, SKIPPED] as Set
    }

    def "allows stack trace formats to be added as enum values"() {
        when:
        logging.stackTraceFilters TRUNCATE, GROOVY

        then:
        logging.stackTraceFilters == [TRUNCATE, GROOVY] as Set
    }

    def "allows stack trace formats to be added as string values"() {
        when:
        logging.stackTraceFilters "truncate", "groovy"

        then:
        logging.stackTraceFilters == [TRUNCATE, GROOVY] as Set
    }

    def "allows standardStreams to be turned on and off"() {
        when:
        logging.setShowStandardStreams(true)

        then:
        logging.showStandardStreams
        logging.events == [STANDARD_OUT, STANDARD_ERROR] as Set

        when:
        logging.setShowStandardStreams(false)

        then:
        !logging.showStandardStreams
        logging.events == [] as Set
    }
}
