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
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.problems.ProblemDescriptor
import org.gradle.tooling.events.problems.ProblemEvent

@ToolingApiVersion(">=8.3")
@TargetGradleVersion(">=8.3")
class ProblemProgressEventCrossVersionTest extends ToolingApiSpecification {

    class MyProgressListener implements ProgressListener {
        List<ProblemDescriptor> allProblems = []

        @Override
        void statusChanged(ProgressEvent event) {
            if (event instanceof ProblemEvent) {
                this.allProblems.addAll(event.getDescriptor())
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
        def problems = listener.allProblems
        problems.size() == 2
        (problems[0].rawAttributes['message'] as String).contains('The RepositoryHandler.jcenter() method has been deprecated.')
//        (problems[0].rawAttributes['doc'] as String).contains('https://docs.gradle.org/')
        (problems[0].rawAttributes['severity'] as String).contains('WARNING')
        (problems[1].rawAttributes['message'] as String).contains("Cannot locate tasks that match ':ba' as task 'ba' is ambiguous in root project")
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
        def problems = listener.allProblems
        problems[0].rawAttributes['message'].contains('Could not compile build file')
        problems[0].rawAttributes['severity'] == 'ERROR'
        problems[0].rawAttributes['path'].endsWith('build.gradle')
        problems[0].rawAttributes['line'] == "3"
    }
}
