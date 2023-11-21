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

package org.gradle.integtests.tooling.r84

import groovy.json.JsonSlurper
import org.gradle.api.problems.Severity
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildException
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.problems.ProblemDescriptor
import org.gradle.tooling.events.problems.ProblemEvent

@ToolingApiVersion("=8.5")
@TargetGradleVersion("=8.5")
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

    // TODO move this test to a proper package
    // TODO make a variant for Gradle 8.6+ where structural information is exposed
    def "test failure context"() {
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
        def problems = listener.allProblems.collect {new JsonSlurper().parseText(it.json) }
        problems.size() == 2

        problems[0].label.contains('The RepositoryHandler.jcenter() method has been deprecated.')
        problems[0].severity == Severity.WARNING.name()
        problems[0].where[0].path.endsWith("'$buildFile.absolutePath'")
        problems[0].where[0].line == 5
        problems[1].label.contains("Cannot locate tasks that match ':ba' as task 'ba' is ambiguous in root project")
        problems[1].severity == Severity.ERROR.name()
        problems[1].where[0].path == 'ba'
        problems[1].where[0].line == -1
        problems[1].problemCategory == 'task-selection:no-matches'
    }
}
