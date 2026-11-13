/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.tooling.r910

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.events.problems.TaskPathLocation

/**
 * A client from before detection traces existed receives a problem whose payload holds trace classes
 * and protocol interfaces that are missing from the client's own Tooling API jar.
 */
@ToolingApiVersion(">=8.13 <9.10.0")
@TargetGradleVersion(">=9.10.0")
class DetectionTraceOlderClientCrossVersionSpec extends AbstractDetectionTraceCrossVersionSpec {

    def "older client still receives a problem that carries a detection trace"() {
        given:
        taskReportsProblem()

        when:
        def problems = runReportProblem()

        then:
        problems.size() == 1
        problems[0].contextualLocations.findAll { it instanceof TaskPathLocation }*.buildTreePath == [":reportProblem"]
    }

    def "older client still receives a problem from a task of an included build"() {
        given:
        taskOfIncludedBuildReportsProblem()

        when:
        def problems = runReportProblem()

        then:
        problems.size() == 1
        problems[0].contextualLocations.findAll { it instanceof TaskPathLocation }*.buildTreePath == [":lib:core:reportProblem"]
    }
}
