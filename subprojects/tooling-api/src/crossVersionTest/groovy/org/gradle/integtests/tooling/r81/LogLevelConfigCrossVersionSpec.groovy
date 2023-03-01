/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests.tooling.r81

import org.gradle.api.logging.LogLevel
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion

import static org.gradle.integtests.tooling.fixture.ToolingApiTestCommon.LOG_LEVEL_TEST_SCRIPT
import static org.gradle.integtests.tooling.fixture.ToolingApiTestCommon.runLogScript
import static org.gradle.integtests.tooling.fixture.ToolingApiTestCommon.validateLogs

class LogLevelConfigCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        propertiesFile << "org.gradle.logging.level=quiet"
        buildFile << LOG_LEVEL_TEST_SCRIPT
    }

    @ToolingApiVersion('>=8.1')
    @TargetGradleVersion('>=3.0 <=8.0')
    def "tooling api uses log level set in arguments over gradle properties TAPI >= 8.1"() {
        when:
        def stdOut = runLogScript(toolingApi, arguments)

        then:
        validateLogs(stdOut, expectedLevel)

        where:
        expectedLevel  | arguments
        LogLevel.LIFECYCLE | []
        LogLevel.INFO  | ["--info"]
        LogLevel.LIFECYCLE  | ["-Dorg.gradle.logging.level=info"]
    }

    @ToolingApiVersion(">=7.0 <8.1")
    @TargetGradleVersion('>=8.1')
    def "tooling api uses log level set in arguments over gradle properties TAPI < 8.1"() {
        when:
        def stdOut = runLogScript(toolingApi, arguments)

        then:
        validateLogs(stdOut, expectedLevel)

        where:
        expectedLevel  | arguments
        LogLevel.QUIET | []
        LogLevel.INFO  | ["--info"]
        LogLevel.INFO  | ["-Dorg.gradle.logging.level=info"]
    }
}
