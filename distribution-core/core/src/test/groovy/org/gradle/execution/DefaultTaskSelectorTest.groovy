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
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.service.ServiceRegistry
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class DefaultTaskSelectorTest extends AbstractProjectBuilderSpec {
    def rootProject = TestUtil.create(temporaryFolder).rootProject()
    def projectA = TestUtil.createChildProject(rootProject, "a")
    def projectB = TestUtil.createChildProject(rootProject, "b")
    def projectBChild = TestUtil.createChildProject(projectB, "child")
    def buildStateRegistry = Stub(BuildStateRegistry) {
        getIncludedBuilds() >> []
    }
    def serviceRegistry = Stub(ServiceRegistry) {
        get(BuildStateRegistry) >> buildStateRegistry
    }
    def gradle = Stub(GradleInternal) {
        getRootProject() >> rootProject
        getDefaultProject() >> projectB
        getServices() >> serviceRegistry
    }
    def resolver = Mock(TaskNameResolver)
    def projectConfigurer = Mock(ProjectConfigurer)
    def selector = new DefaultTaskSelector(gradle, resolver, projectConfigurer)

    def "qualified exclude filter configures target project and selects exact match on task name"() {
        def excluded = Stub(Task)
        def notExcluded = Stub(Task)
        def selectionResult = Stub(TaskSelectionResult)

        when:
        def filter = selector.getFilter(":a:b")

        then:
        1 * projectConfigurer.configure(projectA)
        1 * resolver.selectWithName("b", projectA, false) >> selectionResult
        _ * selectionResult.collectTasks(_) >> { it[0] << excluded }
        0 * _

        and:
        !filter.isSatisfiedBy(excluded)
        filter.isSatisfiedBy(notExcluded)
    }

    def "qualified exclude filter configures target project and selects matching tasks from all candidates"() {
        def excluded = Stub(Task)
        def notExcluded = Stub(Task)
        def selectionResult = Stub(TaskSelectionResult)

        when:
        def filter = selector.getFilter(":a:b")

        then:
        1 * projectConfigurer.configure(projectA)
        1 * resolver.selectWithName("b", projectA, false) >> null
        1 * resolver.selectAll(projectA, false) >> [b1: selectionResult]
        _ * selectionResult.collectTasks(_) >> { it[0] << excluded }
        0 * _

        and:
        !filter.isSatisfiedBy(excluded)
        filter.isSatisfiedBy(notExcluded)
    }

    def "unqualified exclude filter configures default project only and filters tasks by path"() {
        when:
        def filter = selector.getFilter("a")

        then:
        1 * projectConfigurer.configure(projectB)
        1 * resolver.tryFindUnqualifiedTaskCheaply("a", projectB) >> true
        0 * _

        and:
        !filter.isSatisfiedBy(task(projectB, "a"))
        !filter.isSatisfiedBy(task(projectBChild, "a"))
        filter.isSatisfiedBy(task(projectA, "a"))
        filter.isSatisfiedBy(task(rootProject, "a"))
        filter.isSatisfiedBy(task(projectB, "other"))
    }

    def "unqualified exclude filter configures all subprojects of the default project when exact match on task name not found"() {
        def excluded = Stub(Task)
        def notExcluded = Stub(Task)
        def selectionResult = Stub(TaskSelectionResult)

        when:
        def filter = selector.getFilter("a")

        then:
        1 * projectConfigurer.configure(projectB)
        1 * resolver.tryFindUnqualifiedTaskCheaply("a", projectB) >> false
        1 * projectConfigurer.configureHierarchy(projectB)
        1 * resolver.selectWithName("a", projectB, true) >> selectionResult
        _ * selectionResult.collectTasks(_) >> { it[0] << excluded }
        0 * _

        and:
        !filter.isSatisfiedBy(excluded)
        filter.isSatisfiedBy(notExcluded)
    }

    def "unqualified exclude filter selects tasks and filters by instance when no exact match on name found"() {
        def excluded = Stub(Task)
        def notExcluded = Stub(Task)
        def selectionResult = Stub(TaskSelectionResult)

        when:
        def filter = selector.getFilter("a")

        then:
        1 * projectConfigurer.configure(projectB)
        1 * resolver.tryFindUnqualifiedTaskCheaply("a", projectB) >> false
        1 * projectConfigurer.configureHierarchy(projectB)
        1 * resolver.selectWithName("a", projectB, true) >> null
        1 * resolver.selectAll(projectB, true) >> [a1: selectionResult]
        _ * selectionResult.collectTasks(_) >> { it[0] << excluded }
        0 * _

        and:
        !filter.isSatisfiedBy(excluded)
        filter.isSatisfiedBy(notExcluded)
    }

    def task(ProjectInternal project, String name) {
        def task = Stub(TaskInternal) {
            getProject() >> project
            getName() >> name
        }
        return task
    }
}
