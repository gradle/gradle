/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests.tooling.r83


import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildException
import org.gradle.tooling.Problem
import org.gradle.tooling.events.FailureResult
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationResult
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener

@ToolingApiVersion(">=8.3")
@TargetGradleVersion(">=8.3")
class ProblemProgressEventCrossVersionTest extends ToolingApiSpecification {

    class MyProgressListener implements  ProgressListener {
        List<Problem> context

        @Override
        void statusChanged(ProgressEvent event) {
            if (event instanceof FinishEvent) {
                OperationResult result = ((FinishEvent) event).getResult();

                if (result instanceof FailureResult) {
                    def context = ((FailureResult) result).getProblems()
                    if (this.context == null) {
                        this.context = context
                    }
                }
            }
        }
    }

    def "Test failure context"() {
        setup:
        buildFile << """
            plugins {
              id 'java-library'
            }
            repositories.jcenter()
            task bar {}
            task baz {}
        """


        when:
        def listener = new MyProgressListener()
        withConnection { connection ->
            connection.newBuild()
                .forTasks(":ba")
                .addProgressListener(listener)
                .setStandardError(System.err)
                .setStandardOutput(System.out)
                .addArguments("--info")
                .run()
        }

        then:
        thrown(BuildException)
        List<Problem> problems = listener.context
        (problems[0].rawAttributes['message'] as String).contains('The RepositoryHandler.jcenter() method has been deprecated.')
        (problems[0].rawAttributes['severity'] as String).contains('WARNING')
        (problems[1].rawAttributes['message'] as String).contains('Should not happen')
        (problems[1].rawAttributes['severity'] as String).contains('ERROR')
    }

    def "Test line number"() {
        setup:
        buildFile << """
            plugins {
                repositories.mavenCentral()
            }
        """


        when:
        def listener = new MyProgressListener()
        withConnection { connection ->
            connection.newBuild().forTasks(":help").addProgressListener(listener).run()
        }

        then:
        thrown(BuildException)
        List<Problem> problems = listener.context
        problems[0].rawAttributes['message'].contains('Could not compile build file')
        problems[0].rawAttributes['severity'] == 'ERROR'
        problems[0].rawAttributes['file'].endsWith('build.gradle')
        problems[0].rawAttributes['line'] == "3"
        problems[0].rawAttributes['column'] == "17"
    }
}
