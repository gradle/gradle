/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.execution.commandline

import org.gradle.api.Task
import org.gradle.execution.TaskSelection
import org.gradle.execution.TaskSelectionResult
import org.gradle.execution.selection.BuildTaskSelector
import org.gradle.internal.DefaultTaskExecutionRequest
import org.gradle.internal.build.BuildState
import spock.lang.Specification

import static com.google.common.collect.Sets.newHashSet

class CommandLineTaskParserSpec extends Specification {
    def selector = Stub(BuildTaskSelector)
    def defaultBuild = Stub(BuildState)
    def taskConfigurer = Mock(CommandLineTaskConfigurer)
    def task = Mock(Task)
    def task2 = Mock(Task)
    def task3 = Mock(Task)
    def parser = new CommandLineTaskParser(taskConfigurer, selector, defaultBuild)

    def setup() {
        taskConfigurer.configureTasks(_, _) >> { args -> args[1] }
    }

    def "parses a single task selector"() {
        given:
        def request = new DefaultTaskExecutionRequest(['foo'], 'project', null)
        def selection = new TaskSelection(':project', ':foo', asTaskSelectionResults(task))

        selector.resolveTaskName(null, 'project', defaultBuild, 'foo') >> selection

        when:
        def out = parser.parseTasks(request)

        then:
        out == [selection]
    }

    def "parses multiple tasks selectors"() {
        given:
        def request = new DefaultTaskExecutionRequest(['foo', 'bar'])
        def selection1 = new TaskSelection(':project', ':foo', asTaskSelectionResults(task, task2))
        def selection2 = new TaskSelection(':project', ':bar', asTaskSelectionResults(task3))

        selector.resolveTaskName(null, null, defaultBuild, 'foo') >> selection1
        selector.resolveTaskName(null, null, defaultBuild, 'bar') >> selection2

        when:
        def out = parser.parseTasks(request)

        then:
        out == [selection1, selection2]
    }

    def "configures tasks if configuration options specified"() {
        given:
        def request = new DefaultTaskExecutionRequest(['foo', '--all', 'bar', '--include', 'stuff', 'lastTask'])
        selector.resolveTaskName(null, null, defaultBuild, 'foo') >> new TaskSelection(':project', 'foo task', asTaskSelectionResults(task, task2))
        selector.resolveTaskName(null, null, defaultBuild, 'bar') >> new TaskSelection(':project', 'bar task', asTaskSelectionResults(task3))
        selector.resolveTaskName(null, null, defaultBuild, 'lastTask') >> new TaskSelection(':project', 'last task', asTaskSelectionResults(task3))

        when:
        def out = parser.parseTasks(request)

        then:
        out.size() == 3
        1 * taskConfigurer.configureTasks(newHashSet(task, task2), ['--all', 'bar', '--include', 'stuff', 'lastTask']) >> ['bar', '--include', 'stuff', 'lastTask']
        1 * taskConfigurer.configureTasks(newHashSet(task3), ['--include', 'stuff', 'lastTask']) >> ['lastTask']
        1 * taskConfigurer.configureTasks(newHashSet(task3), []) >> []
        0 * taskConfigurer._
    }

    TaskSelectionResult asTaskSelectionResults(Task... someTasks) {
        TaskSelectionResult mock = Mock(TaskSelectionResult)
        _ * mock.collectTasks(_) >> { Object args -> args[0].addAll(someTasks) }
        mock
    }
}
