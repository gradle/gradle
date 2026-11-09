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
import org.gradle.tooling.events.problems.BuildFrame
import org.gradle.tooling.events.problems.Locality
import org.gradle.tooling.events.problems.ProjectFrame
import org.gradle.tooling.events.problems.TaskFrame
import org.gradle.tooling.events.problems.TaskPathLocation

@ToolingApiVersion(">=9.10.0")
@TargetGradleVersion(">=9.10.0")
class DetectionTraceCrossVersionSpec extends AbstractDetectionTraceCrossVersionSpec {

    def "problem reported by a task carries the task, its project and the build, leaf first"() {
        given:
        taskReportsProblem()

        when:
        def problems = runReportProblem()

        then:
        problems.size() == 1
        def frames = problems[0].detectionTrace.frames
        frames*.displayName == ["task :reportProblem", "project :", "build :"]
        frames[0] instanceof TaskFrame
        frames[0].buildTreePath == ":reportProblem"
        frames[1] instanceof ProjectFrame
        frames[1].buildTreePath == ":"
        frames[2] instanceof BuildFrame
        frames[2].buildPath == ":"
        frames*.locality.unique() == [Locality.UNKNOWN]

        and: "the flat list is still filled"
        problems[0].contextualLocations.findAll { it instanceof TaskPathLocation }*.buildTreePath == [":reportProblem"]
    }

    def "problem reported by a task of an included build carries both builds"() {
        given:
        taskOfIncludedBuildReportsProblem()

        when:
        def problems = runReportProblem()

        then:
        problems.size() == 1
        def frames = problems[0].detectionTrace.frames
        frames*.displayName == ["task :lib:core:reportProblem", "project :lib:core", "build :lib", "build :"]
        frames[0] instanceof TaskFrame
        frames[0].buildTreePath == ":lib:core:reportProblem"
        frames[1] instanceof ProjectFrame
        frames[1].buildTreePath == ":lib:core"
        frames[2] instanceof BuildFrame
        frames[2].buildPath == ":lib"
        frames[3] instanceof BuildFrame
        frames[3].buildPath == ":"
    }

    def "problem reported while configuring carries the build alone"() {
        given:
        pluginReportsProblemWhenApplied()

        when:
        def problems = runReportProblem()

        then:
        problems.size() == 1
        def frames = problems[0].detectionTrace.frames
        frames*.displayName == ["build :"]
        frames[0] instanceof BuildFrame
    }
}
