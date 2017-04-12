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

package org.gradle.execution.taskgraph

import org.gradle.api.internal.changedetection.state.TaskHistoryStore
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.work.WorkerLeaseService
import spock.lang.Specification

public class TaskPlanExecutorFactoryTest extends Specification {
    final TaskHistoryStore cache = Mock()
    final ExecutorFactory executorFactory = Mock()
    final WorkerLeaseService workerLeaseService = Mock()

    def "can create a task plan executor"() {
        when:
        def factory = new TaskPlanExecutorFactory(1, executorFactory, workerLeaseService)

        then:
        factory.create().class == DefaultTaskPlanExecutor

        when:
        factory = new TaskPlanExecutorFactory(3, executorFactory, workerLeaseService)

        then:
        factory.create().class == DefaultTaskPlanExecutor
    }
}
