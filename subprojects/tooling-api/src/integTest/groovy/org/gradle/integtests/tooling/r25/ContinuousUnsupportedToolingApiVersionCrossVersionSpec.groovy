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

package org.gradle.integtests.tooling.r25

import org.gradle.integtests.fixtures.executer.GradleVersions
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersions
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.exceptions.UnsupportedBuildArgumentException
import spock.lang.Timeout

class ContinuousUnsupportedToolingApiVersionCrossVersionSpec extends ToolingApiSpecification {
    @Timeout(120)
    @ToolingApiVersion(">=2.0 <2.1")
    @TargetGradleVersion(GradleVersions.SUPPORTS_CONTINUOUS)
    def "client receives appropriate error if continuous build attempted using client that does not support cancellation"() {
        when:
        buildFile.text = "apply plugin: 'java'"
        withConnection {
            newBuild()
                .withArguments("--continuous")
                .forTasks("build")
                .run()
        }

        then:
        caughtGradleConnectionException = thrown(GradleConnectionException)
        caughtGradleConnectionException.message.startsWith("Could not execute build using")
        caughtGradleConnectionException.cause.message == "Continuous build requires Tooling API client version 2.1 or later."
    }

    @Timeout(120)
    @ToolingApiVersion(ToolingApiVersions.SUPPORTS_CANCELLATION)
    @TargetGradleVersion(">=1.2 <2.5")
    def "client receives appropriate error target Gradle version does not support cancellation"() {
        when:
        buildFile.text = "apply plugin: 'java'"
        withConnection {
            newBuild()
                .withArguments("--continuous")
                .forTasks("build")
                .run()
        }

        then:
        def e = thrown(UnsupportedBuildArgumentException)
        e.message.startsWith("Could not execute build using")
        e.cause.message.contains("Unknown command-line option '--continuous'.")
    }

}
