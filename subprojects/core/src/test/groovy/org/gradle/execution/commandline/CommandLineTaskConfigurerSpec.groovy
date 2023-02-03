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
import org.gradle.api.Project
import org.gradle.api.tasks.options.Option
import org.gradle.api.internal.tasks.options.OptionReader
import org.gradle.api.tasks.TaskAction
import org.gradle.execution.DefaultTaskSelector
import org.gradle.internal.typeconversion.TypeConversionException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class CommandLineTaskConfigurerSpec extends Specification {

    Project project = ProjectBuilder.builder().build()
    CommandLineTaskConfigurer configurer = new CommandLineTaskConfigurer(new OptionReader());

    DefaultTaskSelector selector = Mock()
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
        configurer = Spy(CommandLineTaskConfigurer, constructorArgs: [new OptionReader()])

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

    def "configures enum option"() {
        when:
        configurer.configureTasks([task], ['--someEnum', "value1"])
        then:
        task.anEnum == TestEnum.value1

        when:
        configurer.configureTasks([task], ['--someEnum', "unsupportedEnumValue"])
        then:
        def e = thrown(TaskConfigurationException)
        e.message == "Problem configuring option 'someEnum' on task ':someTask' from command line."
        e.cause instanceof TypeConversionException
        e.cause.message == "Cannot convert string value 'unsupportedEnumValue' to an enum value of type 'org.gradle.execution.commandline.CommandLineTaskConfigurerSpec\$TestEnum' (valid case insensitive values: value1, value2)"
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
        def ex = thrown(TaskConfigurationException)
        ex.cause.message.contains('someFlag')
    }

    def "fails if one of the options cannot be applied to one of the tasks"() {
        when:
        configurer.configureTasks([task, otherTask], input)

        then:
        def ex = thrown(TaskConfigurationException)
        ex.cause.message.contains('someFlag2')

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
        when:
        configurer.configureTasks([task, task2], ['--xxx'])

        then:
        def ex = thrown(TaskConfigurationException)
        ex.cause.message.contains('xxx')
    }

    def "fails neatly when short option used"() {
        when:
        configurer.configureTasks([task], ['--someFlag', '-c'])

        then:
        def ex = thrown(TaskConfigurationException)
        ex.cause.message.contains("Unknown command-line option '-c'")
    }

    public static class SomeTask extends DefaultTask {
        String content = 'default content'

        @Option(option = "content", description = "Some content.")
        public void setContent(String content) {
            this.content = content
        }

        boolean someFlag = false

        @Option(option = "someFlag", description = "Some flag.")
        public void setSomeFlag(boolean someFlag) {
            this.someFlag = someFlag
        }

        Boolean someFlag2 = false

        @Option(option = "someFlag2", description = "Some 2nd flag.")
        public void setSomeFlag2(Boolean someFlag2) {
            this.someFlag2 = someFlag2
        }

        @Option(option = "notUsed", description = "Not used.")
        public void setNotUsed(boolean notUsed) {
            throw new RuntimeException("Not used");
        }

        TestEnum anEnum

        @Option(option = "someEnum", description = "some enum value.")
        public void setEnum(TestEnum anEnum) {
            this.anEnum = anEnum;
        }

        @TaskAction
        public void dummy() {}
    }

    public static class SomeOtherTask extends DefaultTask {
        boolean someFlag = false
        String stuff

        @Option(option = "someFlag", description = "Some flag.")
        public void setSomeFlag(boolean someFlag) {
            this.someFlag = someFlag
        }

        @Option(option = "stuff", description = "Some stuff.")
        public void setStuff(String stuff) {
            this.stuff = stuff;
        }
    }

    @SuppressWarnings('FieldName')
    enum TestEnum {
        value1, value2
    }
}
