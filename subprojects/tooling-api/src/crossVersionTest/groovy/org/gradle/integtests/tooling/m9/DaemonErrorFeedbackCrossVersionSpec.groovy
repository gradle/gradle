/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.tooling.m9

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.GradleConnectionException
import spock.lang.Issue
import spock.lang.Timeout

class DaemonErrorFeedbackCrossVersionSpec extends ToolingApiSpecification {

    @Issue("GRADLE-1799")
    @Timeout(60)
    def "promptly discovers rubbish jvm arguments"() {
        //jvm arguments cannot be set for an existing process
        //so we must not run in embedded mode
        toolingApi.requireDaemons()

        when:
        withConnection {
            it.newBuild()
                    .setJvmArguments("-Xasdf")
                    .run()
        }

        then:
        GradleConnectionException ex = thrown()
        ex.cause.message.contains "-Xasdf"
        ex.cause.message.contains "Unable to start the daemon"
    }
}
