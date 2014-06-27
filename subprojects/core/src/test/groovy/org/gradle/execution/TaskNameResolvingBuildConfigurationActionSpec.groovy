/*
 * Copyright 2013 the original author or authors.
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

import com.google.common.collect.Sets
import org.gradle.StartParameter
import org.gradle.TaskParameter
import org.gradle.api.Task
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.tasks.options.OptionReader
import org.gradle.execution.commandline.CommandLineTaskConfigurer
import org.gradle.execution.commandline.CommandLineTaskParser
import org.gradle.internal.DefaultTaskParameter
import spock.lang.Specification

class TaskNameResolvingBuildConfigurationActionSpec extends Specification {
    TaskSelector selector
    GradleInternal gradle
    BuildExecutionContext context
    def TaskNameResolvingBuildConfigurationAction action

    def setup() {
        selector = Mock(TaskSelector)
        gradle = Mock(GradleInternal)
        context = Mock(BuildExecutionContext)
        OptionReader optionReader = new OptionReader();
        CommandLineTaskParser parser = new CommandLineTaskParser(new CommandLineTaskConfigurer(optionReader));
        action = new TaskNameResolvingBuildConfigurationAction(parser, selector)
    }

    def "empty task parameters are no-op action"() {
        given:
        def startParameters = Mock(StartParameter)

        when:
        _ * context.getGradle() >> gradle
        _ * gradle.getStartParameter() >> startParameters
        _ * startParameters.getTaskParameters() >> []

        action.configure(context)

        then:
        1 * context.proceed()
        0 * context._()
        0 * startParameters._()
    }

    def "expand task parameters to tasks"() {
        given:
        def startParameters = Mock(StartParameter)
        def executer = Mock(TaskGraphExecuter)
        TaskParameter taskParameter1 = new DefaultTaskParameter('task1', ':')
        TaskParameter taskParameter2 = new DefaultTaskParameter('task2', ':')
        def selection1 = Mock(TaskSelector.TaskSelection)
        def task1a = Mock(Task)
        def task1b = Mock(Task)
        def selection2 = Mock(TaskSelector.TaskSelection)
        def task2 = Mock(Task)

        when:
        _ * context.getGradle() >> gradle
        _ * gradle.getStartParameter() >> startParameters
        _ * startParameters.getTaskParameters() >> [taskParameter1, taskParameter2]
        _ * selector.getSelection(taskParameter1) >> selection1
        _ * selector.getSelection(taskParameter2) >> selection2
        _ * gradle.taskGraph >> executer

        1 * selection1.tasks >> Sets.newLinkedHashSet([task1a, task1b])
        1 * selection2.tasks >> [task2]

        action.configure(context)

        then:
        0 * startParameters.setTaskNames(_)
        1 * executer.addTasks(Sets.newLinkedHashSet([task1a, task1b]))
        1 * executer.addTasks(Sets.newLinkedHashSet([task2]))
        1 * context.proceed()
        0 * context._()
    }
}
