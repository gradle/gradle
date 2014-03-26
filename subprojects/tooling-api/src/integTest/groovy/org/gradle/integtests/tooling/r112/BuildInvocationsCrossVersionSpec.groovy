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

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.UnknownModelException
import org.gradle.tooling.exceptions.UnsupportedBuildArgumentException
import org.gradle.tooling.model.Launchable
import org.gradle.tooling.model.Task
import org.gradle.tooling.model.TaskSelector
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.gradle.BuildInvocations

@ToolingApiVersion(">=1.12")
class BuildInvocationsCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        settingsFile << '''
include 'a'
include 'b'
include 'b:c'
rootProject.name = 'test'
'''
        buildFile << '''
task t1 << {
    println "t1 in $project.name"
}

project(':b') {
    task t3 << {
        println "t3 in $project.name"
    }
    task t2 << {
        println "t2 in $project.name"
    }
}

project(':b:c') {
    task t1 << {
        println "t1 in $project.name"
    }
    task t2 << {
        println "t2 in $project.name"
    }
}'''
    }

    @TargetGradleVersion(">=1.8 <=1.11")
    def "no task selectors when running action in older container"() {
        when:
        withConnection { connection -> connection.action(new FetchAllTaskSelectorsBuildAction()).run() }

        then:
        Exception e = thrown()
        e.cause.message.startsWith('No model of type \'BuildInvocations\' is available in this build.')
    }

    @TargetGradleVersion(">=1.12")
    def "can request task selectors in action"() {
        when:
        Map<String, Set<String>> result = withConnection { connection ->
            connection.action(new FetchAllTaskSelectorsBuildAction()).run() }

        then:
        result != null
        result.keySet() == ['test', 'a', 'b', 'c'] as Set
        result['test'] == ['t1', 't2', 't3'] as Set
        result['b'] == ['t1', 't2', 't3'] as Set
        result['c'] == ['t1', 't2'] as Set
        result['a'].isEmpty()
    }

    @TargetGradleVersion(">=1.12")
    def "build task selectors from action"() {
        given:
        toolingApi.isEmbedded = false // to load launchables using correct classloader in integTest
        when:
        BuildInvocations projectSelectors = withConnection { connection ->
            connection.action(new FetchTaskSelectorsBuildAction('b')).run() }
        TaskSelector selector = projectSelectors.taskSelectors.find { it -> it.name == 't1'}
        def result = withBuild { BuildLauncher it ->
            it.forLaunchables(selector)
        }

        then:
        result.result.assertTasksExecuted(':b:c:t1')
        result.result.assertTaskNotExecuted(':t1')

        when:
        BuildInvocations rootProjectSelectors = withConnection { connection ->
            connection.action(new FetchTaskSelectorsBuildAction('test')).run() }
        TaskSelector rootSelector = rootProjectSelectors.taskSelectors.find { it -> it.name == 't1'}
        result = withBuild { BuildLauncher it ->
            it.forLaunchables(selector, rootSelector)
        }

        then:
        UnsupportedBuildArgumentException e = thrown()
        e.message.contains('Problem with provided launchable arguments')
    }

    @TargetGradleVersion(">=1.0-milestone-5")
    def "build task selectors from connection"() {
        when:
        toolingApi.isEmbedded = false // to load launchables using correct classloader in integTest
        BuildInvocations model = withConnection { connection ->
            connection.getModel(BuildInvocations)
        }
        TaskSelector selector = model.taskSelectors.find { TaskSelector it ->
            it.name == 't1' && it.description.startsWith(':b:') && !it.description.startsWith(':b:c:') }
        def result = withBuild { BuildLauncher it ->
            it.forLaunchables(selector)
        }

        then:
        result.result.assertTasksExecuted(':b:c:t1')
    }

    @TargetGradleVersion(">=1.0-milestone-5")
    def "can request task selectors for project"() {
        given:
        BuildInvocations model = withConnection { connection ->
            connection.getModel(BuildInvocations)
        }

        when:
        def selectors = model.taskSelectors.findAll { TaskSelector it ->
            !it.description.startsWith(':') && it.name != 'setupBuild' // synthetic task in 1.6
        }
        then:
        selectors*.name as Set == ['t1', 't2', 't3'] as Set

        when:
        selectors = model.taskSelectors.findAll { TaskSelector it ->
            it.description.startsWith(':b:') && !it.description.startsWith(':b:c:')
        }
        then:
        selectors*.name as Set == ['t1', 't2', 't3'] as Set

        when:
        selectors = model.taskSelectors.findAll { TaskSelector it ->
            it.description.startsWith(':b:c:')
        }
        then:
        selectors*.name as Set == ['t1', 't2'] as Set
    }

    @TargetGradleVersion("<1.0-milestone-5")
    def "cannot request BuildInvocations for old project"() {
        when:
        withConnection { connection ->
            connection.getModel(BuildInvocations)
        }

        then:
        UnknownModelException e = thrown()
        e.message.contains('does not support building a model of type \'' + BuildInvocations.simpleName + '\'')
    }

    @TargetGradleVersion(">=1.12")
    def "get tasks for projects"() {
        when:
        List<Task> tasks = withConnection { connection ->
            connection.action(new FetchTasksBuildAction(':b')).run()
        }

        then:
        tasks.size() == 2
        tasks*.name as Set == ['t2', 't3'] as Set

        when:
        tasks[0].project
        then:
        UnsupportedMethodException e = thrown()
        e != null
    }

    @TargetGradleVersion(">=1.12")
    def "build tasks from BuildInvocations model as Launchable"() {
        when:
        toolingApi.isEmbedded = false // to load launchables using correct classloader in integTest
        List<Task> tasks = withConnection { connection ->
            connection.action(new FetchTasksBuildAction(':b')).run()
        }
        Launchable task = tasks.find { it -> it.name == 't2'}
        def result = withBuild { BuildLauncher it ->
            it.forLaunchables(task)
        }

        then:
        result.result.assertTasksExecuted(':b:t2')
        result.result.assertTaskNotExecuted(':b:c:t2')
    }

    @TargetGradleVersion(">=1.0-milestone-5")
    def "build task from connection as Launchable"() {
        when:
        toolingApi.isEmbedded = false // to load launchables using correct classloader in integTest
        BuildInvocations model = withConnection { connection ->
            connection.getModel(BuildInvocations)
        }
        Task task = model.tasks.find { Task it ->
            it.name == 't2' && it.path == ':b:t2' }
        def result = withBuild { BuildLauncher it ->
            it.forLaunchables(task)
        }

        then:
        result.result.assertTasksExecuted(':b:t2')
    }

    @TargetGradleVersion(">=1.0-milestone-5")
    def "can request tasks for project"() {
        given:
        BuildInvocations model = withConnection { connection ->
            connection.getModel(BuildInvocations)
        }

        expect:
        model.tasks.count { it.name != 'setupBuild' } == 5

        when:
        def tasks = model.tasks.findAll { Task it ->
            it.path.startsWith(':b:') && !it.path.startsWith(':b:c:')
        }
        then:
        tasks*.name as Set == ['t2', 't3'] as Set

        when:
        tasks = model.tasks.findAll { Task it ->
            it.path.startsWith(':b:c:')
        }
        then:
        tasks*.name as Set == ['t1', 't2'] as Set
    }
}
