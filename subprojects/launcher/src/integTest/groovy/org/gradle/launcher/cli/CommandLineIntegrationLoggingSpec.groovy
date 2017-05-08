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

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import spock.lang.Ignore
import spock.lang.Unroll

class CommandLineIntegrationLoggingSpec extends DaemonIntegrationSpec {

    @Unroll
    def "Set logging level using org.gradle.logging.level=#logLevel"() {
        def message = 'Expected message in the output'
        buildFile << """
            task assertLogging {
                doLast {
                    logger.${logLevel} '${message}'
                    assert logger.${logLevel}Enabled
                    if ('${nextLevel}') {
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

    @Unroll
    def "Set log level using org.gradle.logging.level in GRADLE_OPTS to #logLevel"() {
        def message = 'Expected message in the output'
        buildFile << """
            task assertLogging {
                doLast {
                    assert System.getProperty("org.gradle.logging.level") == "${logLevel}"
                    logger.${logLevel} '${message}'
                    assert logger.${logLevel}Enabled
                    if ('${nextLevel}') {
                        assert !logger.${nextLevel}Enabled
                    }
                }
            }
        """
        expect:
        executer.withBuildJvmOpts(flags)
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

    def "Demonstrate withBuildJvmOpts() is broken"() {
        buildFile << """
            task verify {
                doLast {
                    assert System.getProperty("foo.bar") == "baz"
                    assert System.getProperty("user.country") == "CA"
                    assert System.getProperty("user.language") == "fr"
                    assert "fr_CA" == Locale.getDefault().toString()
                }
            }
        """

        expect:
//        executer.withArguments("-Duser.country=CA", "-Duser.language=fr", "-Dfoo.bar=baz")
//        executer.withCommandLineGradleOpts("-Duser.country=CA", "-Duser.language=fr", "-Dfoo.bar=baz") and
        executer.useOnlyRequestedJvmOpts().withBuildJvmOpts("-Duser.country=CA", "-Duser.language=fr", "-Dfoo.bar=baz")
        succeeds("verify")

    }

    def "other client JVM system properties are carried over to daemon JVM"() {
        given:
        file("build.gradle") << """
        task verify {
            doLast {
                assert System.getProperty("foo.bar") == "baz"
            }
        }
        """

        expect:
        executer.withBuildJvmOpts("-Dfoo.bar=baz", "-Dsome.other.property=value").withTasks("verify").run()
    }


    @Ignore
    @Unroll
    def "Set log level using org.gradle.logging.level in combination with other logging"() {
        def message = 'Expected message in the output'
        buildFile << """
            task assertLogging {
                doLast {
                    logger.${logLevel} '${message}'
                    assert logger.${logLevel}Enabled
                    if ('${nextLevel}') {
                        assert !logger.${nextLevel}Enabled
                    }
                }
            }
        """
        expect:
        executer.withArguments(flags).withCommandLineGradleOpts(gradleOpts)
        succeeds("assertLogging")
        outputContains(message)

        where:
        logLevel | nextLevel | flags | gradleOpts
        'quiet'  | 'warn'    | []    | '-Dorg.gradle.logging.level=quiet'
//        'quiet'     | 'warn'        | ['-Dorg.gradle.logging.level=quiet']                  | '-Dorg.gradle.logging.level=quiet'
//        'info'      | 'debug'       | []                                                    | '-Dorg.gradle.logging.level=info'
//        'warn'      | 'lifecycle'   | ['-Dorg.gradle.logging.level=warn']                   | ''
//        'lifecycle' | 'info'        | ['-Dorg.gradle.logging.level=LifeCycle']              | ''
//        'info'      | 'debug'       | ['-Dorg.gradle.logging.level=Info']                   | ''
//        'debug'     | ''            | ['-Dorg.gradle.logging.level=DEBUG']                  | ''
//        'info'      | 'debug'       | ['--info', '-Dorg.gradle.logging.level=quiet']        | ''
//        'info'      | 'debug'       | ['-Dorg.gradle.logging.level=quiet', '--warn', '-i']  | '-Dorg.gradle.logging.level=lifecycle'
//        'info'      | 'debug'       | ['-Dorg.gradle.logging.level=info']                   | '-Dorg.gradle.logging.level=quiet'
    }
}
