/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.execution.workgraph

import org.gradle.internal.resources.ResourceLock
import org.gradle.internal.scheduler.DefaultScheduler
import org.gradle.internal.scheduler.Event
import org.gradle.internal.scheduler.Node
import org.gradle.internal.scheduler.NodeExecutionWorker
import org.gradle.internal.scheduler.NodeExecutionWorkerService
import org.gradle.internal.scheduler.NodeExecutor
import org.gradle.internal.scheduler.Scheduler

import javax.annotation.Nullable
import java.util.concurrent.BlockingQueue

import static org.gradle.internal.scheduler.NodeExecutionWorker.NodeSchedulingResult.STARTED

class DefaultSchedulerTest extends AbstractSchedulerTest {

    Scheduler scheduler = new DefaultScheduler(cancellationHandler, concurrentNodeExecutionCoordinator, new TestNodeExecutionWorkerService(1))

}
