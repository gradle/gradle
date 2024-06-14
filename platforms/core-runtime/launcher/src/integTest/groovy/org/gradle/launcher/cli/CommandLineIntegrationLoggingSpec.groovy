/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.launcher.cli

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CommandLineIntegrationLoggingSpec extends AbstractIntegrationSpec {

    def "Set logging level using org.gradle.logging.level=#logLevel"() {
        def message = 'Expected message in the output'
        buildFile << """
            task assertLogging {
                def logLevel = project.gradle.startParameter.logLevel
                doLast {
                    assert LogLevel.${logLevel.toUpperCase()} == logLevel
                    logger.${logLevel} '${message}'
                    assert logger.${logLevel}Enabled
                    if ('${nextLevel}') { // check that there is a next level (there isn't in DEBUG)
                        assert !logger.${nextLevel}Enabled
                    }
                }
            }
        """
        expect:
        executer.withArguments(flags)
        succeeds("assertLogging")
        outputContains(message)

        where:
        logLevel    | nextLevel   | flags
        'quiet'     | 'warn'      | ['-Dorg.gradle.logging.level=quiet']
        'warn'      | 'lifecycle' | ['-Dorg.gradle.logging.level=warn']
        'lifecycle' | 'info'      | ['-Dorg.gradle.logging.level=lifecycle']
        'info'      | 'debug'     | ['-Dorg.gradle.logging.level=info']
        'debug'     | ''          | ['-Dorg.gradle.logging.level=debug']
    }

    def "Set log level using org.gradle.logging.level in GRADLE_OPTS to #logLevel"() {
        setup:
        executer.requireIsolatedDaemons()

        def message = 'Expected message in the output'
        buildFile << """
            task assertLogging {
                def logLevel = project.gradle.startParameter.logLevel
                doLast {
                    assert System.getProperty("org.gradle.logging.level") == "${logLevel}"
                    assert LogLevel.${logLevel.toUpperCase()} == logLevel
                    logger.${logLevel} '${message}'
                    assert logger.${logLevel}Enabled
                    if ('${nextLevel}') {
                        assert !logger.${nextLevel}Enabled
                    }
                }
            }
        """
        expect:
        executer.withCommandLineGradleOpts(flags).requireDaemon().requireIsolatedDaemons()
        succeeds("assertLogging")
        outputContains(message)

        where:
        logLevel    | nextLevel   | flags
        'quiet'     | 'warn'      | ['-Dorg.gradle.logging.level=quiet']
        'warn'      | 'lifecycle' | ['-Dorg.gradle.logging.level=warn']
        'lifecycle' | 'info'      | ['-Dorg.gradle.logging.level=lifecycle']
        'info'      | 'debug'     | ['-Dorg.gradle.logging.level=info']
        'debug'     | ''          | ['-Dorg.gradle.logging.level=debug']
    }


    def "Command line switches override properly: #flags #options"() {
        setup:
        executer.requireIsolatedDaemons()

        def nextLevel
        if (logLevel == 'quiet') {
            nextLevel = 'warn'
        } else if (logLevel == 'info') {
            nextLevel = 'debug'
        } else {
            throw new RuntimeException("Unexpected log level: ${logLevel}")
        }

        def message = 'Expected message in the output'
        buildFile << """
            task assertLogging {
                def logLevel = project.gradle.startParameter.logLevel
                doLast {
                    assert LogLevel.${logLevel.toUpperCase()} == logLevel
                    logger.${logLevel} '${message}'
                    assert logger.${logLevel}Enabled
                    if ('${nextLevel}') { // check that there is a next level (there isn't in DEBUG)
                        assert !logger.${nextLevel}Enabled
                    }
                }
            }
        """
        expect:
        executer.withArguments(flags).withCommandLineGradleOpts(options).requireDaemon().requireIsolatedDaemons()
        succeeds("assertLogging")
        outputContains(message)

        where:
        logLevel | options                                                                  | flags
        // Flags always win over options
        'info'   | ['-Dorg.gradle.logging.level=quiet']                                     | ['--info']
        'info'   | ['-Dorg.gradle.logging.level=quiet']                                     | ['-Dorg.gradle.logging.level=info']
        'quiet'  | ['-Dorg.gradle.logging.level=info']                                      | ['-q']
        'quiet'  | ['-Dorg.gradle.logging.level=info']                                      | ['-Dorg.gradle.logging.level=quiet']

        // The later system prop always wins
        'info'   | []                                                                       | ['-Dorg.gradle.logging.level=quiet', '-Dorg.gradle.logging.level=info']
        'quiet'  | []                                                                       | ['-Dorg.gradle.logging.level=info', '-Dorg.gradle.logging.level=quiet']
        'info'   | ['-Dorg.gradle.logging.level=quiet', '-Dorg.gradle.logging.level=info']  | []
        'quiet'  | ['-Dorg.gradle.logging.level=info', '-Dorg.gradle.logging.level=quiet']  | []

        // The later shorthand always wins
        'quiet'  | []                                                                       | ['--info', '-q']
        'info'   | []                                                                       | ['-q', '--info']

        // Shorthand always win over system props within flags
        'quiet'  | []                                                                       | ['-q', '-Dorg.gradle.logging.level=debug']
        'quiet'  | []                                                                       | ['-Dorg.gradle.logging.level=debug', '-q']
        'info'   | []                                                                       | ['-Dorg.gradle.logging.level=quiet', '--info']
        'info'   | []                                                                       | ['--info', '-Dorg.gradle.logging.level=quiet']
    }
}
