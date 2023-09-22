/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.logging.slf4j

import org.gradle.internal.time.Time
import spock.lang.Specification

import static org.gradle.api.logging.LogLevel.LIFECYCLE
import static org.slf4j.Logger.ROOT_LOGGER_NAME

class OutputEventListenerBackedLoggerContextTest extends Specification {
    OutputEventListenerBackedLoggerContext context = new OutputEventListenerBackedLoggerContext(Time.clock())

    private OutputEventListenerBackedLogger logger(String name) {
        context.getLogger(name)
    }

    def "can retrieve logger named #name"() {
        expect:
        logger(name).name == name

        where:
        name << [ROOT_LOGGER_NAME, "foo", "foo.bar"]
    }

    def "logger instances are cached"() {
        expect:
        logger(name).is(logger(name))

        where:
        name << [ROOT_LOGGER_NAME, "foo", "foo.bar"]
    }

    def "default log level is LIFECYCLE"() {
        expect:
        context.level == LIFECYCLE
    }

    def "cannot set global level to null"() {
        when:
        context.level = null

        then:
        IllegalArgumentException e = thrown()
        e.message == "Global log level cannot be set to null"
    }
}
