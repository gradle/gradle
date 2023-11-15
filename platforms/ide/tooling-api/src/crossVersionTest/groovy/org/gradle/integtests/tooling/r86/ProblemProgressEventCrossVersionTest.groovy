/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.tooling.r86


import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.problems.ProblemDescriptor
import org.gradle.tooling.events.problems.ProblemEvent
import org.gradle.tooling.events.problems.Severity

@ToolingApiVersion(">=8.6")
@TargetGradleVersion(">=8.6")
class ProblemProgressEventCrossVersionTest extends ToolingApiSpecification {

    // TODO add test coverage for events coming from older Gradle versions

    def "Problems expose details via Tooling API events"() {
        given:
        buildFile << """
            import org.gradle.api.problems.Problem
            import org.gradle.api.problems.Severity
            import org.gradle.internal.deprecation.Documentation

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    problems.create {
                        it.label("shortProblemMessage")
                        .undocumented()
                        .noLocation()
                        .category("main", "sub", "id")
                        .details("long message")
                        .additionalData("aKey", "aValue")
                        .severity(Severity.WARNING)
                    }.report()
                }
            }

            tasks.register("reportProblem", ProblemReportingTask)
        """
        ProblemProgressListener listener = new ProblemProgressListener()

        when:
        withConnection { connection ->
            connection.newBuild().forTasks('reportProblem')
                .addProgressListener(listener)
                .run()
        }
        def problems = listener.problems

        then:
        problems.size() == 1
        problems[0].category.namespace == 'main' // TODO this is a bug; namespace should be a string representing Gradle core
        problems[0].category.category == 'main'
        problems[0].category.subCategories == ['sub','id']
        problems[0].additionalData.asMap == ['aKey' : 'aValue']
        problems[0].label.label == 'shortProblemMessage'
        problems[0].details.details == 'long message' // TODO test coverage for null value
        problems[0].severity == Severity.WARNING
    }

    class ProblemProgressListener implements ProgressListener {

        List<ProblemDescriptor> problems = []

        @Override
        void statusChanged(ProgressEvent event) {
            if (event instanceof ProblemEvent) {
                this.problems.addAll(event.getDescriptor())
            }
        }
    }
}
