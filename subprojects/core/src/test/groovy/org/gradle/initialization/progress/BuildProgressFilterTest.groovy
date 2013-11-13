package org.gradle.initialization.progress

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
