/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.logging.console.taskgrouping.verbose

import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.internal.SystemProperties
import org.gradle.internal.logging.console.taskgrouping.AbstractConsoleVerboseRenderingFunctionalTest

import static org.gradle.api.logging.configuration.ConsoleOutput.Verbose

class VerboseConsoleVerboseRenderingFunctionalTest extends AbstractConsoleVerboseRenderingFunctionalTest {
    ConsoleOutput consoleType = Verbose

    @SuppressWarnings("IntegrationTestFixtures")
    def 'verbose task header has no blank line above it'() {
        given:
        buildFile << '''
task upToDate{
    outputs.upToDateWhen {true}
    doLast {}
}
'''

        when:
        succeeds('upToDate')
        succeeds('upToDate')

        then:
        result.normalizedOutput.contains("> Task :upToDate")
        !result.normalizedOutput.contains("${SystemProperties.instance.lineSeparator}> Task :upToDate")
    }
}
