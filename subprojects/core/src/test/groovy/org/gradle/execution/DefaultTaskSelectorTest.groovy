/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.execution

import org.gradle.api.Task
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.Path

class DefaultTaskSelectorTest extends AbstractProjectBuilderSpec {
    def buildStateRegistry = Stub(BuildStateRegistry) {
        getIncludedBuilds() >> []
    }
    def projectModel1 = Stub(ProjectInternal)
    def project1 = project(":a", projectModel1)
    def resolver = Mock(TaskNameResolver)
    def projectConfigurer = Mock(ProjectConfigurer)
    def selector = new DefaultTaskSelector(resolver, projectConfigurer)

    def "exclude filter configures target project and selects exact match on task name when subprojects not included"() {
        def excluded = Stub(Task)
        def notExcluded = Stub(Task)
        def selectionResult = Stub(TaskSelectionResult)

        when:
        def filter = selector.getFilter(new TaskSelector.SelectionContext(Path.path(":a:b"), "type"), project1, "b", false)

        then:
        1 * projectConfigurer.configure(projectModel1)
        1 * resolver.selectWithName("b", projectModel1, false) >> selectionResult
        _ * selectionResult.collectTasks(_) >> { it[0] << excluded }
        0 * _

        and:
        !filter.isSatisfiedBy(excluded)
        filter.isSatisfiedBy(notExcluded)
    }

    def "qualified exclude filter configures target project and selects matching tasks from all candidates when subprojects not included"() {
        def excluded = Stub(Task)
        def notExcluded = Stub(Task)
        def selectionResult = Stub(TaskSelectionResult)

        when:
        def filter = selector.getFilter(new TaskSelector.SelectionContext(Path.path(":a:b"), "type"), project1, "b", false)

        then:
        1 * projectConfigurer.configure(projectModel1)
        1 * resolver.selectWithName("b", projectModel1, false) >> null
        1 * resolver.selectAll(projectModel1, false) >> [b1: selectionResult]
        _ * selectionResult.collectTasks(_) >> { it[0] << excluded }
        0 * _

        and:
        !filter.isSatisfiedBy(excluded)
        filter.isSatisfiedBy(notExcluded)
    }

    def "exclude filter configures only the default project when exact match on task name found when subprojects included"() {
        def excluded = Stub(Task)
        _ * excluded.name >> "b"
        _ * excluded.project >> projectModel1
        def notExcluded = Stub(Task)
        _ * notExcluded.name >> "c"

        when:
        def filter = selector.getFilter(new TaskSelector.SelectionContext(Path.path(":a:b"), "type"), project1, "b", true)

        then:
        1 * projectConfigurer.configure(projectModel1)
        1 * resolver.tryFindUnqualifiedTaskCheaply("b", projectModel1) >> true
        0 * _

        and:
        !filter.isSatisfiedBy(excluded)
        filter.isSatisfiedBy(notExcluded)
    }

    def "exclude filter configures all subprojects of the default project when exact match on task name not found when subprojects included"() {
        def excluded = Stub(Task)
        def notExcluded = Stub(Task)
        def selectionResult = Stub(TaskSelectionResult)

        when:
        def filter = selector.getFilter(new TaskSelector.SelectionContext(Path.path(":a:b"), "type"), project1, "b", true)

        then:
        1 * projectConfigurer.configure(projectModel1)
        1 * resolver.tryFindUnqualifiedTaskCheaply("b", projectModel1) >> false
        1 * projectConfigurer.configureHierarchy(projectModel1)
        1 * resolver.selectWithName("b", projectModel1, true) >> selectionResult
        _ * selectionResult.collectTasks(_) >> { it[0] << excluded }
        0 * _

        and:
        !filter.isSatisfiedBy(excluded)
        filter.isSatisfiedBy(notExcluded)
    }

    ProjectState project(String path, ProjectInternal model) {
        def state = Stub(ProjectState)
        state.mutableModel >> model
        return state
    }

    def task(ProjectInternal project, String name) {
        def task = Stub(TaskInternal) {
            getProject() >> project
            getName() >> name
        }
        return task
    }
}
