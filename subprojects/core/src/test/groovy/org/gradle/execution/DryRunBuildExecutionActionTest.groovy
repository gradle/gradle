/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.StartParameter
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.internal.SystemProperties
import org.gradle.internal.logging.text.TestStyledTextOutputFactory
import org.gradle.util.Path
import spock.lang.Specification

import static org.gradle.util.WrapUtil.toList

public class DryRunBuildExecutionActionTest extends Specification {
    private static final String EOL = SystemProperties.instance.lineSeparator
    def executionContext = Mock(BuildExecutionContext.class)
    def gradle = Mock(GradleInternal.class)
    def taskExecuter = Mock(TaskGraphExecuter.class)
    def startParameter = Mock(StartParameter.class)
    def textOutputFactory = new TestStyledTextOutputFactory()
    def action = new DryRunBuildExecutionAction(textOutputFactory)

    def setup() {
        _ * gradle.getStartParameter() >> startParameter
        _ * executionContext.getGradle() >> gradle
        _ * gradle.getTaskGraph() >> taskExecuter
    }

    def "print all selected tasks before proceeding when dry run is enabled"() {
        def task1 = Mock(TaskInternal.class)
        def task2 = Mock(TaskInternal.class)
        def category = DryRunBuildExecutionAction.class.name

        given:
        startParameter.isDryRun() >> true
        taskExecuter.getAllTasks() >> toList(task1, task2)

        when:
        action.execute(executionContext)

        then:
        textOutputFactory.toString() == "{$category}:task1 {progressstatus}SKIPPED$EOL{$category}:task2 {progressstatus}SKIPPED$EOL"
        1 * task1.getIdentityPath() >> Path.path(':task1')
        1 * task2.getIdentityPath() >> Path.path(':task2')
        0 * executionContext.proceed()
    }

    def "proceeds when dry run is not selected"() {
        given:
        startParameter.isDryRun() >> false

        when:
        action.execute(executionContext)

        then:
        1 * executionContext.proceed()
    }
}
