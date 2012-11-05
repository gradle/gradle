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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.execution.TaskSelector
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import static com.google.common.collect.Sets.newHashSet
import static java.util.Collections.emptyList

/**
 * by Szczepan Faber, created at: 10/8/12
 */
class CommandLineTaskParserSpec extends Specification {

    Project project = new ProjectBuilder().build()
    CommandLineTaskParser parser = new CommandLineTaskParser()
    TaskSelector selector = Mock()
    SomeTask task = project.task('someTask', type: SomeTask)
    SomeTask task2 = project.task('someTask2', type: SomeTask)
    SomeTask task3 = project.task('someTask3', type: SomeTask)

    def setup() {
        parser.taskConfigurer = Mock(CommandLineTaskConfigurer)
        parser.taskConfigurer.configureTasks(_, _) >> { args -> args[1] }
    }

    def "deals with empty input"() {
        expect:
        parser.parseTasks(emptyList(), selector).empty
    }

    def "parses a single task"() {
        given:
        selector.getSelection('foo') >> new TaskSelector.TaskSelection('foo task', [task] as Set)

        when:
        def out = parser.parseTasks(['foo'], selector)

        then:
        out.size() == 1
        out.get('foo task') == [task] as Set
    }

    def "parses single task with multiple matches"() {
        given:
        selector.getSelection('foo') >> new TaskSelector.TaskSelection('foo task', [task, task2] as Set)

        when:
        def out = parser.parseTasks(['foo'], selector)

        then:
        out.size() == 2
        out.get('foo task') == [task, task2] as Set
    }

    def "reports incorrect commandline task configuration"() {
        when:
        parser.parseTasks(['tasks', '-l', '-l'], selector)

        then:
        def ex = thrown(GradleException)
        ex.message.contains('-l')
    }

    def "parses multiple matching tasks"() {
        given:
        selector.getSelection('foo') >> new TaskSelector.TaskSelection('foo task', [task, task2] as Set)
        selector.getSelection('bar') >> new TaskSelector.TaskSelection('bar task', [task3] as Set)

        when:
        def out = parser.parseTasks(['foo', 'bar'], selector)

        then:
        out.size() == 3
        out.get('foo task') == [task, task2] as Set
        out.get('bar task') == [task3] as Set
    }

    def "configures tasks if configuration options specified"() {
        given:
        selector.getSelection('foo') >> new TaskSelector.TaskSelection('foo task', [task, task2] as Set)
        selector.getSelection('bar') >> new TaskSelector.TaskSelection('bar task', [task3] as Set)
        selector.getSelection('lastTask') >> new TaskSelector.TaskSelection('last task', [task3] as Set)

        when:
        def out = parser.parseTasks(['foo', '--all', 'bar', '--include', 'stuff', 'lastTask'], selector)

        then:
        out.size() == 4
        1 * parser.taskConfigurer.configureTasks(newHashSet(task, task2), ['--all', 'bar', '--include', 'stuff', 'lastTask']) >> ['bar', '--include', 'stuff', 'lastTask']
        1 * parser.taskConfigurer.configureTasks(newHashSet(task3), ['--include', 'stuff', 'lastTask']) >> ['lastTask']
        1 * parser.taskConfigurer.configureTasks(newHashSet(task3), []) >> []
        0 * parser.taskConfigurer._
    }

    public static class SomeTask extends DefaultTask {
        @TaskAction public void dummy() {}
    }
}
