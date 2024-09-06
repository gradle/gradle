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
import org.gradle.execution.plan.FinalizedExecutionPlan
import org.gradle.execution.plan.Node
import org.gradle.execution.plan.QueryableExecutionPlan
import org.gradle.internal.operations.TestBuildOperationRunner
import spock.lang.Specification

import java.util.function.BiConsumer

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
        def node1 = Mock(Node.class)
        def node2 = Mock(Node.class)
        def contents = Mock(QueryableExecutionPlan)
        def scheduledNodes = Mock(QueryableExecutionPlan.ScheduledNodes)

        given:
        startParameter.isDryRun() >> true
        executionPlan.contents >> contents

        when:
        action.execute(gradle, executionPlan)

        then:
        1 * contents.scheduledNodes >> scheduledNodes
        1 * scheduledNodes.visitNodes(_) >> { BiConsumer<List<Node>, Set<Node>> consumer ->
            consumer.accept([node1, node2], [] as Set<Node>)
        }
        1 * node1.dryRun(buildOperationRunner)
        1 * node2.dryRun(buildOperationRunner)

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
}
