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

package org.gradle.integtests.tooling.r112

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.GradleTask
import org.gradle.tooling.model.Launchable
import org.gradle.tooling.model.Task
import org.gradle.tooling.model.TaskSelector
import org.gradle.tooling.model.gradle.BuildInvocations

class BuildInvocationsCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        settingsFile << '''
            include 'a'
            include 'b'
            include 'b:c'
            rootProject.name = 'test'
        '''

        file("a/build.gradle") << ""

        file("b/build.gradle") << '''
            task t3 {
                def projectName = project.name
                doLast {
                    println "t3 in $projectName"
                }
            }
            task t2 {
                def projectName = project.name
                doLast {
                    println "t2 in $projectName"
                }
            }
        '''

        file("b/c/build.gradle") << '''
            task t1 {
                def projectName = project.name
                doLast {
                    println "t1 in $projectName"
                }
            }
            task t2 {
                def projectName = project.name
                doLast {
                    println "t2 in $projectName"
                }
            }
        '''

        buildFile << '''
            task t1 {
                def projectName = project.name
                doLast {
                    println "t1 in $projectName"
                }
            }
        '''
    }

    def "can request task selectors in action"() {
        when:
        Map<String, Set<String>> result = withConnection { connection ->
            connection.action(new FetchAllTaskSelectorsBuildAction()).run() }

        then:
        result != null
        result.keySet() == ['test', 'a', 'b', 'c'] as Set
        result['test'] == rootProjectImplicitSelectors + ['t1', 't2', 't3'] as Set
        result['b'] == implicitSelectors + ['t1', 't2', 't3'] as Set
        result['c'] == implicitSelectors + ['t1', 't2'] as Set
        result['a'] == implicitSelectors
    }

    def "can run build using task selectors from action"() {
        when:
        BuildInvocations projectSelectors = withConnection { connection ->
            connection.action(new FetchTaskSelectorsBuildAction('b')).run() }
        TaskSelector selector = projectSelectors.taskSelectors.find { it -> it.name == 't1'}
        withBuild { BuildLauncher it ->
            it.forLaunchables(selector)
        }

        then:
        result.assertTasksExecuted(':b:c:t1')
    }

    def "can run build using task selectors from connection"() {
        when:
        BuildInvocations model = withConnection { connection ->
            connection.getModel(BuildInvocations)
        }
        TaskSelector selector = model.taskSelectors.find { TaskSelector it ->
            it.name == 't1'
        }
        withBuild { BuildLauncher it ->
            it.forLaunchables(selector)
        }

        then:
        result.assertTasksExecuted(':t1', ':b:c:t1')
    }

    def "build task selectors from connection in specified order"() {
        when:
        BuildInvocations model = withConnection { connection ->
            connection.getModel(BuildInvocations)
        }
        TaskSelector selectorT1 = model.taskSelectors.find {  it.name == 't1' }
        TaskSelector selectorT2 = model.taskSelectors.find { it.name == 't2' }
        withBuild { BuildLauncher it ->
            it.forLaunchables(selectorT1, selectorT2)
        }
        then:
        result.assertTasksExecutedInOrder(':t1', ':b:c:t1', ':b:t2', ':b:c:t2')

        when:
        withBuild { BuildLauncher it ->
            it.forLaunchables(selectorT2, selectorT1)
        }
        then:
        result.assertTasksExecutedInOrder(':b:t2', ':b:c:t2', ':t1', ':b:c:t1')
    }

    def "can fetch task selectors for root project from connection"() {
        when:
        BuildInvocations model = withConnection { connection ->
            connection.getModel(BuildInvocations)
        }

        then:
        model.taskSelectors*.name as Set == rootProjectImplicitSelectors + ['t1', 't2', 't3'] as Set
    }

    def "can fetch tasks including implicit for project using action"() {
        def projectBExpectedTasks = (['t2', 't3'] + implicitTasks) as Set
        def rootProjectExpectedTasks = (['t1'] + rootProjectImplicitTasks) as Set

        when:
        List<Task> tasks = withConnection { connection ->
            connection.action(new FetchTasksBuildAction(':b')).run()
        }

        then:
        tasks.size() == projectBExpectedTasks.size()
        tasks*.name as Set == projectBExpectedTasks

        when:
        tasks = withConnection { connection ->
            connection.action(new FetchTasksBuildAction(':')).run()
        }

        then:
        tasks.size() == rootProjectExpectedTasks.size()
        tasks*.name as Set == rootProjectExpectedTasks
    }

    def "build tasks from BuildInvocations model as Launchable"() {
        when:
        List<Task> tasks = withConnection { connection ->
            connection.action(new FetchTasksBuildAction(':b')).run()
        }
        Launchable task = tasks.find { it -> it.name == 't2'}
        withBuild { BuildLauncher it ->
            it.forLaunchables(task)
        }

        then:
        result.assertTasksExecuted(':b:t2')
    }

    def "build task from connection as Launchable"() {
        when:
        BuildInvocations model = withConnection { connection ->
            connection.getModel(BuildInvocations)
        }
        Task task = model.tasks.find { Task it ->
            it.name == 't1'
        }
        withBuild { BuildLauncher it ->
            it.forLaunchables(task)
        }

        then:
        result.assertTasksExecuted(':t1')
    }

    def "build tasks Launchables in order"() {
        when:
        GradleProject model = withConnection { connection ->
            connection.getModel(GradleProject)
        }
        GradleTask taskT1 = model.tasks.find { it.name == 't1' }
        GradleTask taskBT2 = model.findByPath(':b').tasks.find { it.name == 't2' }
        GradleTask taskBCT1 = model.findByPath(':b:c').tasks.find { it.name == 't1' }
        withBuild { BuildLauncher it ->
            it.forLaunchables(taskT1, taskBT2, taskBCT1)
        }

        then:
        result.assertTasksExecutedInOrder(':t1', ':b:t2', ':b:c:t1')

        when:
        withBuild { BuildLauncher it ->
            it.forLaunchables(taskBCT1, taskBT2, taskT1)
        }
        then:
        result.assertTasksExecutedInOrder(':b:c:t1', ':b:t2', ':t1')
    }

    def "build tasks and selectors in order"() {
        when:
        GradleProject model = withConnection { connection ->
            connection.getModel(GradleProject)
        }
        GradleTask taskT1 = model.tasks.find { it.name == 't1' }
        BuildInvocations bSelectors = withConnection { connection ->
            connection.action(new FetchTaskSelectorsBuildAction('b')).run()
        }
        TaskSelector selectorBT1 = bSelectors.taskSelectors.find { it.name == 't1' }
        TaskSelector selectorBT3 = bSelectors.taskSelectors.find { it.name == 't3' }
        withBuild { BuildLauncher it ->
            it.forLaunchables(selectorBT1, selectorBT3, taskT1)
        }
        then:
        result.assertTasksExecutedInOrder(':b:c:t1', ':b:t3', ':t1')
    }

    def "build tasks and selectors in order cross version"() {
        when:
        GradleProject model = withConnection { connection ->
            connection.getModel(GradleProject)
        }
        GradleTask taskT1 = model.tasks.find { it.name == 't1' }
        GradleTask taskBT2 = model.findByPath(':b').tasks.find { it.name == 't2' }
        BuildInvocations rootSelectors = withConnection { connection ->
            connection.getModel(BuildInvocations)
        }
        TaskSelector selectorT1 = rootSelectors.taskSelectors.find { it.name == 't1' }
        TaskSelector selectorT2 = rootSelectors.taskSelectors.find { it.name == 't2' }
        withBuild { BuildLauncher it ->
            it.forLaunchables(taskT1, selectorT1, selectorT2, taskBT2)
        }
        then:
        result.assertTasksExecutedInOrder(':t1', ':b:c:t1', ':b:t2', ':b:c:t2')
    }

    def "builds selectors from different projects"() {
        when:
        BuildInvocations rootSelectors = withConnection { connection ->
            connection.action(new FetchTaskSelectorsBuildAction('test')).run()
        }
        BuildInvocations bSelectors = withConnection { connection ->
            connection.action(new FetchTaskSelectorsBuildAction('b')).run()
        }
        TaskSelector selectorT1 = rootSelectors.taskSelectors.find { it.name == 't1' }
        TaskSelector selectorBT1 = bSelectors.taskSelectors.find { it.name == 't1' }
        TaskSelector selectorBT3 = bSelectors.taskSelectors.find { it.name == 't3' }
        withBuild { BuildLauncher it ->
            it.forLaunchables(selectorBT1, selectorBT3, selectorT1)
        }
        then:
        result.assertTasksExecuted(':b:c:t1', ':b:t3', ':t1')
    }

    def "can request tasks for root project"() {
        // TODO make sure it is for root project if default project is different

        given:
        BuildInvocations model = withConnection { connection ->
            connection.getModel(BuildInvocations)
        }

        when:
        def task = model.tasks.find { !rootProjectImplicitTasks.contains(it.name) }

        then:
        task != null
        task.name == 't1'
        task.path == ':t1'
    }
}
