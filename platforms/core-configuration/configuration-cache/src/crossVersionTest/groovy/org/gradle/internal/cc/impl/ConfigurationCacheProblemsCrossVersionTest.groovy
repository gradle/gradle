/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.impl


import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildException
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.problems.LineInFileLocation
import org.gradle.tooling.events.problems.Severity
import org.gradle.tooling.events.problems.SingleProblemEvent
import org.gradle.tooling.events.problems.internal.GeneralData

@ToolingApiVersion(">=8.10 <8.12")
@TargetGradleVersion(">=8.10")
class ConfigurationCacheProblemsCrossVersionTest extends ToolingApiSpecification {

    class ProblemProgressListener implements ProgressListener {

        List<SingleProblemEvent> problems = []

        @Override
        void statusChanged(ProgressEvent event) {
            if (event instanceof SingleProblemEvent) {
                def singleProblem = event as SingleProblemEvent

                // Ignore problems caused by the minimum JVM version deprecation.
                // These are emitted intermittently depending on the version of Java used to run the test.
                if (singleProblem.definition.id.name == "executing-gradle-on-jvm-versions-and-lower") {
                    return
                }

                this.problems.add(event)
            }
        }
    }

    def "failing executions produce problems"() {
        setup:
        buildFile """
            gradle.buildFinished { }

            task run
        """


        when:
        def listener = new ProblemProgressListener()
        withConnection { connection ->
            connection.newBuild()
                .forTasks(":run")
                .addProgressListener(listener)
                .setStandardError(System.err)
                .setStandardOutput(System.out)
                .addArguments("--info", "--configuration-cache")
                .run()
        }

        then:
        thrown(BuildException)
        listener.problems.size() == 1
        verifyAll(listener.problems[0]) {
            definition.id.displayName == "registration of listener on 'Gradle.buildFinished' is unsupported"
            definition.id.group.displayName == "configuration cache validation"
            definition.id.group.name == "configuration-cache"
            definition.severity == Severity.ERROR
            (locations[0] as LineInFileLocation).path == "build file 'build.gradle'" // FIXME: the path should not contain a prefix nor extra quotes
            (locations[1] as LineInFileLocation).path == "build file '$buildFile.path'"
            additionalData instanceof GeneralData
            additionalData.asMap.isEmpty()
        }
    }
}
