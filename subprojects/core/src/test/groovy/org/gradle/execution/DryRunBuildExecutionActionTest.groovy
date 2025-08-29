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

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.execution.plan.FinalizedExecutionPlan
import org.gradle.execution.plan.QueryableExecutionPlan
import org.gradle.internal.logging.text.TestStyledTextOutputFactory
import org.gradle.util.Path
import spock.lang.Specification

import static org.gradle.util.internal.WrapUtil.toList

class DryRunBuildExecutionActionTest extends Specification {

    def delegate = Mock(BuildWorkExecutor)
    def executionPlan = Mock(FinalizedExecutionPlan)
    def gradle = Mock(GradleInternal)
    def startParameter = Mock(StartParameterInternal)
    def textOutputFactory = new TestStyledTextOutputFactory()
    def configurationTimeBarrier = Mock(ConfigurationTimeBarrier)
    def action = new DryRunBuildExecutionAction(delegate, textOutputFactory, configurationTimeBarrier)

    def setup() {
        _ * gradle.getStartParameter() >> startParameter
    }

    def "print all selected tasks before proceeding when dry run is enabled"() {
        def task1 = Mock(TaskInternal.class)
        def task2 = Mock(TaskInternal.class)
        def contents = Mock(QueryableExecutionPlan)

        given:
        startParameter.isDryRun() >> true
        configurationTimeBarrier.isAtConfigurationTime() >> false
        executionPlan.contents >> contents
        contents.tasks >> toList(task1, task2)

        when:
        action.execute(gradle, executionPlan)

        then:
        textOutputFactory.category == DryRunBuildExecutionAction.canonicalName
        textOutputFactory.output == """:task1 {progressstatus}SKIPPED
:task2 {progressstatus}SKIPPED
"""
        1 * task1.getIdentityPath() >> Path.path(':task1')
        1 * task2.getIdentityPath() >> Path.path(':task2')
    }
}
