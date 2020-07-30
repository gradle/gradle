/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.tooling.r65

import org.gradle.integtests.tooling.CancellationSpec
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TestResultHandler
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.eclipse.EclipseProject
import spock.lang.Ignore
import spock.lang.Retry
import spock.lang.Timeout
import spock.util.concurrent.PollingConditions

class ToolingApiShutdownCrossVersionSpec extends CancellationSpec {

    @ToolingApiVersion(">=6.5")
    @TargetGradleVersion(">=6.4")
    def "scenario 1"() {
        setup:
        buildFile.text = """
            task hello {
                doLast {
                    echo "Hello world"
                }
            }
        """.stripIndent()

        when:
        withConnection { c -> c.newBuild().forTasks('hello')
            .withArguments('--debug')
            .setStandardOutput(System.out)
            .setStandardError(System.err)
            .run()
        }

        then:
        true
    }

}
