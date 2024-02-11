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



package org.gradle.api.internal.artifacts.configurations

import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class TasksFromDependentProjectsTest extends AbstractProjectBuilderSpec {

    def context = Mock(TaskDependencyResolveContext)
    def root = TestUtil.create(temporaryFolder).rootProject()
    def child1 = TestUtil.createChildProject(root, "child1")
    def child2 = TestUtil.createChildProject(root, "child2")
    def child3 = TestUtil.createChildProject(root, "child3")
    def child4 = TestUtil.createChildProject(root, "child4")

    def checker = Mock(TasksFromDependentProjects.TaskDependencyChecker)

    def "provides all tasks from dependent projects"() {
        def dep = new TasksFromDependentProjects("buildDependents", "testRuntime", checker, TestFiles.taskDependencyFactory())

        [child1, child2, child3].each {
            it.configurations.create "testRuntime"
            it.configurations.create "conf"
            it.tasks.create "buildDependents"
            it.tasks.create "someTask"
        }

        when: dep.visitDependencies(context)

        then:
        4 * context.getTask() >> child1.tasks["buildDependents"]
        1 * checker.isDependent(child1, "testRuntime", child2) >> false
        1 * checker.isDependent(child1, "testRuntime", child3) >> true
        1 * context.add(child3.tasks["buildDependents"])
        0 * _
    }

    def "knows which tasks come from dependent projects with specific configuration"() {
        def checker = new TasksFromDependentProjects.TaskDependencyChecker()

        child2.configurations.create "testRuntime"
        child2.dependencies.add("testRuntime", child1) //good

        child3.configurations.create "conf"
        child3.configurations.create "testRuntime"
        child3.dependencies.add("conf", child1) //different config dependency, no good

        child4.configurations.create "conf"     //different config, no good

        expect:
        checker.isDependent(child1, "testRuntime", child2)
        !checker.isDependent(child1, "testRuntime", child3)
        !checker.isDependent(child1, "testRuntime", child4)
    }
}
