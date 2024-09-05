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

import org.gradle.api.DefaultTask
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.taskfactory.TaskIdentity
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.execution.plan.FinalizedExecutionPlan
import org.gradle.execution.plan.QueryableExecutionPlan
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.util.Path
import spock.lang.Specification

import static org.gradle.util.internal.WrapUtil.toList

class DryRunBuildWorkExecutorTest extends Specification {
    def delegate = Mock(BuildWorkExecutor)
    def executionPlan = Mock(FinalizedExecutionPlan)
    def gradle = Mock(GradleInternal)
    def startParameter = Mock(StartParameterInternal)
    def buildOperationRunner = new TestBuildOperationRunner()
    def action = new DryRunBuildWorkExecutor(buildOperationRunner, delegate)

    def setup() {
        _ * gradle.getStartParameter() >> startParameter
    }

    def "print all selected tasks before proceeding when dry run is enabled"() {
        def task1 = Mock(TaskInternal.class)
        def task2 = Mock(TaskInternal.class)
        def contents = Mock(QueryableExecutionPlan)

        given:
        startParameter.isDryRun() >> true
        executionPlan.contents >> contents
        contents.tasks >> toList(task1, task2)

        when:
        action.execute(gradle, executionPlan)

        then:
        def operations = buildOperationRunner.log.all(ExecuteTaskBuildOperationType)
        operations.size() == 2
        with(operations.descriptor) {
            it.name == [':project1:task1', ':project2:task2']
            it.displayName == ['Task :project1:task1', 'Task :project2:task2']
            it.metadata == [BuildOperationCategory.TASK, BuildOperationCategory.TASK]
        }
        with(operations.details) {
            it.buildPath == [':', ':build']
            it.taskPath == [':project1:task1', ':project2:task2']
            it.taskId == [1L, 2L]
            it.taskClass == [Task1, Task2]
        }
        operations.result.each {with(it) {
            skipReasonMessage == "Dry run"
            skipMessage == "SKIPPED"

            originBuildInvocationId == null
            originExecutionTime == null
            originBuildCacheKeyBytes == null

            cachingDisabledReasonMessage == "Cacheability was not determined"
            cachingDisabledReasonCategory == "UNKNOWN"
            upToDateMessages == null
            !incremental
        }}
        operations.result.actionable == [true, false]

        _ * task1.getIdentityPath() >> Path.path(':project1:task1')
        _ * task2.getIdentityPath() >> Path.path(':project2:task2')
        _ * task1.getTaskIdentity() >> new TaskIdentity(Task1, 'task1', Path.path(':project1:task1'), Path.path(':project1:task1'), Path.path(':'), 1)
        _ * task2.getTaskIdentity() >> new TaskIdentity(Task2, 'task2', Path.path(':project2:task2'), Path.path(':build:project2:task2'), Path.path(':build'), 2)
        _ * task1.hasTaskActions() >> true
        _ * task2.hasTaskActions() >> false

        0 * delegate.execute(_, _)
    }

    def "proceeds when dry run is not selected"() {
        given:
        startParameter.isDryRun() >> false

        when:
        action.execute(gradle, executionPlan)

        then:
        1 * delegate.execute(gradle, executionPlan)
    }

    static class Task1 extends DefaultTask {}
    static class Task2 extends DefaultTask {}
}
