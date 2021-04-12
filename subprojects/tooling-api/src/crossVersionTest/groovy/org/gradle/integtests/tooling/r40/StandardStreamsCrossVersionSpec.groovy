/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.tooling.r40

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TestOutputStream
import org.gradle.integtests.tooling.fixture.ToolingApiLoggingSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection
import org.gradle.util.internal.RedirectStdOutAndErr
import org.junit.Rule

@ToolingApiVersion(">=4.0")
@TargetGradleVersion(">=4.0")
class StandardStreamsCrossVersionSpec extends ToolingApiLoggingSpecification {
    @Rule RedirectStdOutAndErr stdOutAndErr = new RedirectStdOutAndErr()
    def escapeHeader = "\u001b["

    def "allows colored output with updated logging"() {
        file("build.gradle") << """
task log {
    doLast { logger.quiet("Log message") }
}
"""

        when:
        def output = new TestOutputStream()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            build.standardOutput = output
            build.colorOutput = true
            build.forTasks("log")
            build.run()
        }

        then:
        output.toString().contains(escapeHeader)

        and:
        !stdOutAndErr.stdOut.contains(escapeHeader)
        !stdOutAndErr.stdErr.contains(escapeHeader)
    }
}
