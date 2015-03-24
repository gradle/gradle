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

package org.gradle.logging.internal.slf4j

import org.gradle.api.logging.Logging
import spock.lang.Specification

class NoOpLoggerTest extends Specification {

    def "name can be passed to constructor"() {
        when:
        NoOpLogger logger = new NoOpLogger("logger name")

        then:
        logger.name == "logger name"
    }

    def "logging is not enabled on any level"() {
        when:
        NoOpLogger logger = new NoOpLogger(null)

        then:
        !logger.isTraceEnabled()
        !logger.isTraceEnabled(null)
        !logger.isDebugEnabled()
        !logger.isDebugEnabled(null)
        !logger.isInfoEnabled()
        !logger.isInfoEnabled(null)
        !logger.isInfoEnabled(Logging.LIFECYCLE)
        !logger.isInfoEnabled(Logging.QUIET)
        !logger.isWarnEnabled()
        !logger.isWarnEnabled(null)
        !logger.isErrorEnabled()
        !logger.isErrorEnabled(null)
    }
}
