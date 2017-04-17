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

package org.gradle.api.internal.tasks.execution.statistics

import org.gradle.BuildResult
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.invocation.Gradle
import spock.lang.Specification
import spock.lang.Subject

@Subject(TaskExecutionStatisticsEventAdapter)
class TaskExecutionStatisticsEventAdapterTest extends Specification {
    TaskExecutionStatisticsListener listener = Mock(TaskExecutionStatisticsListener)
    TaskExecutionStatisticsEventAdapter taskStatsEventAdapter = new TaskExecutionStatisticsEventAdapter(listener)

    def gradle = Mock(Gradle)
    def project = Stub(Project) { getGradle() >> gradle }
    def result = Stub(BuildResult) { getGradle() >> gradle }
    def task = Stub(Task) { getProject() >> project }
    def taskState = Stub(TaskStateInternal) { isAvoided() >> true }

    def "delegates to reporter when building root project"() {
        given:
        gradle.getParent() >> null

        when:
        taskStatsEventAdapter.buildStarted(gradle)
        taskStatsEventAdapter.afterExecute(task, taskState)
        taskStatsEventAdapter.buildFinished(result)

        then:
        1 * listener.buildFinished(_)
    }

    def "does not delegate when building nested projects"() {
        given:
        gradle.getParent() >> Stub(Gradle)

        when:
        taskStatsEventAdapter.buildStarted(gradle)
        taskStatsEventAdapter.afterExecute(task, taskState)
        taskStatsEventAdapter.buildFinished(result)

        then:
        0 * listener._
    }
}
