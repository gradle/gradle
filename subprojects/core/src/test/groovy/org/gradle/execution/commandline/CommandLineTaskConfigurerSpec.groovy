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
import org.gradle.api.internal.tasks.CommandLineOption
import org.gradle.api.tasks.TaskAction
import org.gradle.execution.TaskSelector
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 10/8/12
 */
class CommandLineTaskConfigurerSpec extends Specification {

    Project project = new ProjectBuilder().build()
    CommandLineTaskConfigurer configurer = new CommandLineTaskConfigurer()

    TaskSelector selector = Mock()
    SomeTask task = project.task('someTask', type: SomeTask)
    SomeTask task2 = project.task('someTask2', type: SomeTask)
    SomeOtherTask otherTask = project.task('otherTask', type: SomeOtherTask)
    DefaultTask defaultTask = project.task('defaultTask')

    def "does not configure if option doesn't match"() {
        when:
        configurer.configureTasks([task, task2], ['foo'])
        then:
        task.content == 'default content'
        task2.content == 'default content'
        !task.someFlag
        !task2.someFlag
        !task2.someFlag2
        !task2.someFlag2
    }

    def "does not attempt configure if no options"() {
        configurer = Spy(CommandLineTaskConfigurer)

        when:
        def out = configurer.configureTasks([task, task2], ['foo'])

        then:
        out == ['foo']
        0 * configurer.configureTasksNow(_, _)
    }

    def "configures string option on all tasks"() {
        when:
        configurer.configureTasks([task, task2], ['--content', 'Hey!', 'foo'])
        then:
        task.content == 'Hey!'
        task2.content == 'Hey!'
    }

    def "configures boolean option"() {
        when:
        configurer.configureTasks([task], ['--someFlag'])
        then:
        task.someFlag
    }

    def "configures options on all types that can accommodate the setting"() {
        when:
        configurer.configureTasks([task, otherTask], ['--someFlag'])
        then:
        task.someFlag
        otherTask.someFlag
    }

    def "fails if some of the types cannot accommodate the setting"() {
        when:
        configurer.configureTasks([task, defaultTask], ['--someFlag'])
        then:
        def ex = thrown(GradleException)
        ex.message.contains('someFlag')
    }

    def "fails if one of the options cannot be applied to one of the tasks"() {
        when:
        configurer.configureTasks([task, otherTask], input)
        then:
        def ex = thrown(GradleException)
        ex.message.contains('someFlag2')

        where:
        input << [['--someFlag', '--someFlag2'], ['--someFlag2', '--someFlag']]
    }

    def "configures the Boolean option"() {
        when:
        configurer.configureTasks([task], ['--someFlag2'])
        then:
        task.someFlag2
    }

    def "configures mixed options"() {
        when:
        configurer.configureTasks([task, task2], ['--someFlag', '--content', 'Hey!'])
        then:
        task.someFlag
        task2.someFlag
        task.content == 'Hey!'
        task2.content == 'Hey!'
    }

    def "configures options and returns unused arguments"() {
        def args = ['--someFlag', '--content', 'Hey!', 'foo', '--baz', '--someFlag']
        when:
        def out = configurer.configureTasks([task, task2], args)
        then:
        out == ['foo', '--baz', '--someFlag']
    }

    def "fails on unknown option"() {
        def args = ['--xxx']
        when:
        configurer.configureTasks([task, task2], args)

        then:
        def ex = thrown(GradleException)
        ex.message.contains('xxx')
    }

    def "fails neatly when short option used"() {
        def args = ['--someFlag', '-c']
        when:
        configurer.configureTasks([task], args)

        then:
        def ex = thrown(GradleException)
        ex.message.contains('-c')
        ex.message.contains('must have long format')
    }

    public static class SomeTask extends DefaultTask {
        String content = 'default content'
        @CommandLineOption(options="content", description="Some content.") public void setContent(String content) {
            this.content = content
        }

        boolean someFlag = false
        @CommandLineOption(options="someFlag", description="Some flag.") public void setSomeFlag(boolean someFlag) {
            this.someFlag = someFlag
        }

        Boolean someFlag2 = false
        @CommandLineOption(options="someFlag2", description="Some 2nd flag.") public void setSomeFlag2(Boolean someFlag2) {
            this.someFlag2 = someFlag2
        }

        @CommandLineOption(options="notUsed", description="Not used.") public void setNotUsed(boolean notUsed) {
            throw new RuntimeException("Not used");
        }

        @TaskAction public void dummy() {}
    }

    public static class SomeOtherTask extends DefaultTask {
        boolean someFlag = false
        String stuff
        @CommandLineOption(options="someFlag", description="Some flag.") public void setSomeFlag(boolean someFlag) {
            this.someFlag = someFlag
        }
        @CommandLineOption(options="stuff", description="Some stuff.") public void setStuff(String stuff) {
            this.stuff = stuff;
        }
    }
}
