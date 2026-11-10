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
import org.gradle.tooling.events.problems.FilePosition
import org.gradle.tooling.events.problems.LineInFileLocation
import org.gradle.tooling.events.problems.Locality

@ToolingApiVersion(">=9.10.0")
@TargetGradleVersion(">=9.10.0")
class OriginTraceCrossVersionSpec extends AbstractDetectionTraceCrossVersionSpec {

    def "each file location of a problem is an origin trace with a position"() {
        given:
        buildFile """
            import org.gradle.api.problems.Problems
            import javax.inject.Inject

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                abstract Problems getProblems()

                @TaskAction
                void run() {
                    getProblems().getReporter().report(getProblems().getGroups().getOthers().group("Generic").problemId("$PROBLEM_ID")) {
                        it.lineInFileLocation("src/main/java/Foo.java", 12, 5)
                        it.fileLocation("gradle.properties")
                    }
                }
            }

            tasks.register("reportProblem", ProblemReportingTask)
        """

        when:
        def problems = runReportProblem()

        then:
        problems.size() == 1
        def traces = problems[0].originTraces
        traces*.frames*.displayName == [["src/main/java/Foo.java:12:5"], ["gradle.properties"]]
        with(traces[0].frames[0].position as FilePosition) {
            path == "src/main/java/Foo.java"
            line == 12
            column == 5
            offset == null
            length == null
        }
        traces[1].frames[0].position instanceof FilePosition
        (traces[1].frames[0].position as FilePosition).line == null

        and: "a relative path can't be placed in the build, so its locality is unknown"
        traces*.frames*.locality.flatten().unique() == [Locality.UNKNOWN]

        and: "the flat list is still filled"
        problems[0].originLocations.findAll { it instanceof LineInFileLocation }*.line == [12]
    }

    def "a problem without file locations has no origin trace, and its detection trace stays"() {
        given:
        taskReportsProblem()

        when:
        def problems = runReportProblem()

        then:
        problems.size() == 1
        problems[0].originTraces.empty
        problems[0].detectionTrace != null
    }
}
