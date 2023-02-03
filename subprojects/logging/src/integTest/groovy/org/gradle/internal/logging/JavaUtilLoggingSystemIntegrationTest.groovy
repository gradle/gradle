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


package org.gradle.internal.logging

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Test that log levels are propagated to java.util.logging
 * such that logger.isLoggable(Level) gives the expected results.
 * Mapping of log levels is configured in
 * {@link org.gradle.internal.logging.source.JavaUtilLoggingSystem#LOG_LEVEL_MAPPING}.
 */
class JavaUtilLoggingSystemIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            import java.util.logging.Level;
            import java.util.logging.Logger;

        """
    }

    def 'JUL logger.isLoggable corresponds to gradle log level for --debug'() {
        given:
        buildFile << """
            task isLoggable() {
                doLast {
                    def logger = Logger.getLogger('some-logger')

                    assert logger.isLoggable(Level.SEVERE)
                    assert logger.isLoggable(Level.WARNING)
                    assert logger.isLoggable(Level.INFO)
                    assert logger.isLoggable(Level.CONFIG)
                    assert logger.isLoggable(Level.FINE)
                    assert !logger.isLoggable(Level.FINER)
                    assert !logger.isLoggable(Level.FINEST)
                }
            }
        """

        when:
        executer.withArgument("--debug")

        then:
        succeeds('isLoggable')
    }

    def 'JUL logger.isLoggable corresponds to gradle log level for --warn'() {
        given:
        buildFile << """
            task isLoggable() {
                doLast {
                    def logger = Logger.getLogger('some-logger')

                    assert logger.isLoggable(Level.SEVERE)
                    assert logger.isLoggable(Level.WARNING)
                    assert !logger.isLoggable(Level.INFO)
                    assert !logger.isLoggable(Level.CONFIG)
                    assert !logger.isLoggable(Level.FINE)
                    assert !logger.isLoggable(Level.FINER)
                    assert !logger.isLoggable(Level.FINEST)
                }
            }
        """

        when:
        executer.withArgument("--warn")

        then:
        succeeds('isLoggable')
    }

    def 'JUL logger.isLoggable corresponds to gradle log level for --info'() {
        given:
        buildFile << """
            task isLoggable() {
                doLast {
                    def logger = Logger.getLogger('some-logger')

                    assert logger.isLoggable(Level.SEVERE)
                    assert logger.isLoggable(Level.WARNING)
                    assert logger.isLoggable(Level.INFO)
                    assert logger.isLoggable(Level.CONFIG)
                    assert !logger.isLoggable(Level.FINE)
                    assert !logger.isLoggable(Level.FINER)
                    assert !logger.isLoggable(Level.FINEST)
                }
            }
        """

        when:
        executer.withArgument("--info")

        then:
        succeeds('isLoggable')
    }

    def 'JUL logger.isLoggable corresponds to gradle log level for LIFECYCLE'() {
        given:
        buildFile << """
            task isLoggable() {
                doLast {
                    def logger = Logger.getLogger('some-logger')

                    assert logger.isLoggable(Level.SEVERE)
                    assert logger.isLoggable(Level.WARNING)
                    assert !logger.isLoggable(Level.INFO)
                    assert !logger.isLoggable(Level.CONFIG)
                    assert !logger.isLoggable(Level.FINE)
                    assert !logger.isLoggable(Level.FINER)
                    assert !logger.isLoggable(Level.FINEST)
                }
            }
        """

        when:
        executer

        then:
        succeeds('isLoggable')
    }

    def 'JUL logger.isLoggable corresponds to gradle log level for --quiet'() {
        given:
        buildFile << """
            task isLoggable() {
                doLast {
                    def logger = Logger.getLogger('some-logger')

                    assert logger.isLoggable(Level.SEVERE)
                    assert !logger.isLoggable(Level.WARNING)
                    assert !logger.isLoggable(Level.INFO)
                    assert !logger.isLoggable(Level.CONFIG)
                    assert !logger.isLoggable(Level.FINE)
                    assert !logger.isLoggable(Level.FINER)
                    assert !logger.isLoggable(Level.FINEST)
                }
            }
        """

        when:
        executer.withArgument("--quiet")

        then:
        succeeds('isLoggable')
    }
}
