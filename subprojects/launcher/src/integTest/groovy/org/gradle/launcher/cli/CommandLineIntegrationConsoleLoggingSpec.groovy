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
import spock.lang.Unroll

class CommandLineIntegrationConsoleLoggingSpec extends AbstractIntegrationSpec {

    @Unroll
    def "Log level is lifecycle with console:plain by default: #flags"() {
        def message = 'Expected message in the output'
        buildFile << """
            task assertLogging {
                doLast {
                    assert LogLevel.${logLevel.toUpperCase()} == project.gradle.startParameter.logLevel 
                    logger.${logLevel} '${message}'
                    assert logger.${logLevel}Enabled
                    if ('${nextLevel}') {
                        assert !logger.${nextLevel}Enabled
                    }
                }
            }
        """
        expect:
        executer.withLifecycleLoggingDisabled().withArguments(flags)
        succeeds("assertLogging")
        outputContains(message)

        where:
        logLevel    | nextLevel   | flags
        'warn'      | 'lifecycle' | []
        'lifecycle' | 'info'      | ['--console=plain']
        'quiet'     | 'warn'      | ['-q', '--console=plain']
        'quiet'     | 'warn'      | ['-Dorg.gradle.logging.level=quiet', '--console=plain']
    }
}
