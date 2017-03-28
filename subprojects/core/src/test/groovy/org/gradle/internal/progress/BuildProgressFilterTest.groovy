/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.internal.progress

import org.gradle.BuildResult
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import spock.lang.Specification
import spock.lang.Subject

class BuildProgressFilterTest extends Specification {

    BuildProgressLogger logger = Mock()
    @Subject f = new BuildProgressFilter(logger)

    def graph = Stub(TaskExecutionGraph) { getAllTasks() >> [Mock(Task), Mock(Task), Mock(Task)]} //3 tasks
    def gradle = Stub(Gradle) {
        getRootProject() >> Stub(Project) { getAllprojects() >> [Mock(Project), Mock(Project)] } //2 projects
        getTaskGraph() >> graph
    }
    def settings = Stub(Settings) { getGradle() >> gradle }
    def project = Stub(Project) {
        getGradle() >> gradle
        getPath() >> ":foo:bar"
    }
    def result = Stub(BuildResult) { getGradle() >> gradle }
    def task = Stub(Task) { getProject() >> project }

    def "delegates to logger when building root project"() {
        gradle.getParent() >> null

        when:
        f.buildStarted(gradle)
        f.settingsEvaluated(settings)
        f.projectsLoaded(gradle)
        f.beforeEvaluate(project)
        f.afterEvaluate(project, null)
        f.graphPopulated(graph)
        f.afterExecute(task, null)
        f.buildFinished(result)

        then: 1 * logger.buildStarted()
        then: 1 * logger.settingsEvaluated()
        then: 1 * logger.projectsLoaded(2)
        then: 1 * logger.beforeEvaluate(":foo:bar")
        then: 1 * logger.afterEvaluate(":foo:bar")
        then: 1 * logger.graphPopulated(3)
        then: 1 * logger.afterExecute()
        then: 1 * logger.buildFinished()
        then: 0 * logger._
    }

    def "does not delegate when building nested projects"() {
        gradle.getParent() >> Stub(Gradle)

        when:
        f.buildStarted(gradle)
        f.settingsEvaluated(settings)
        f.projectsLoaded(gradle)
        f.beforeEvaluate(project)
        f.afterEvaluate(project, null)
        f.graphPopulated(graph)
        f.afterExecute(task, null)
        f.buildFinished(result)

        then:
        0 * logger._
    }
}
