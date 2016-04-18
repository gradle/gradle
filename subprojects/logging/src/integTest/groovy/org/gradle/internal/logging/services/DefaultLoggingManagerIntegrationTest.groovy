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

package org.gradle.internal.logging.services

import org.gradle.integtests.fixtures.AbstractIntegrationSpec


class DefaultLoggingManagerIntegrationTest extends AbstractIntegrationSpec {
    def "deprecation message when log level is set from a task"() {
        executer.expectDeprecationWarning()
        buildFile << """
            logger.info("this should not be in the output")

            task changeLogLevel {
                doLast {
                    logging.level = LogLevel.INFO
                    logger.info("An info level message")
                    logging.level = LogLevel.LIFECYCLE
                }
            }
        """

        when:
        succeeds("changeLogLevel")

        then:
        result.assertOutputContains("An info level message")
        result.assertOutputContains("LoggingManager.setLevel(LogLevel) has been deprecated")
        ! result.output.contains("this should not be in the output")
    }

    def "deprecation message when log level is set from project"() {
        executer.expectDeprecationWarning()
        buildFile << """
            logger.info("this should not be in the output")
            logging.level = LogLevel.INFO
            logger.info("An info level message")
            logging.level = LogLevel.LIFECYCLE
        """

        when:
        succeeds("help")

        then:
        result.assertOutputContains("An info level message")
        result.assertOutputContains("LoggingManager.setLevel(LogLevel) has been deprecated")
        ! result.output.contains("this should not be in the output")
    }
}
