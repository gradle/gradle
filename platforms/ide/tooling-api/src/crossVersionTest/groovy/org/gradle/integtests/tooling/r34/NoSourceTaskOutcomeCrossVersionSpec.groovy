/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.tooling.r34

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.task.TaskSkippedResult

class NoSourceTaskOutcomeCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        buildFile << """
            task noSourceTask(type:Copy) {
                from("notexist")
                into("notexisteither")
            }"""
    }

    @TargetGradleVersion('>=3.4')
    def "tasks with no source is reported as NO-SOURCE"() {
        when:
        def taskEvents = ProgressEvents.create()
        runBuild(taskEvents)
        then:
        skippedTaskResult(taskEvents).skipMessage == "NO-SOURCE"
    }

    private TaskSkippedResult skippedTaskResult(ProgressEvents events) {
        events.operations.size() == 1
        (TaskSkippedResult)events.operations[0].result
    }

    private void runBuild(listener) {
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('noSourceTask').addProgressListener(listener, EnumSet.of(OperationType.TASK)).run()
        }
    }
}
