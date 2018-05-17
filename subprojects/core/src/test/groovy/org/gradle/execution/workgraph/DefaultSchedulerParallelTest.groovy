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

import com.google.common.collect.Queues
import com.google.common.collect.Sets
import org.gradle.api.internal.TaskInternal
import org.gradle.internal.resources.ResourceLock
import org.gradle.internal.scheduler.DefaultScheduler
import org.gradle.internal.scheduler.Event
import org.gradle.internal.scheduler.Node
import org.gradle.internal.scheduler.NodeExecutionWorker
import org.gradle.internal.scheduler.NodeExecutionWorkerService
import org.gradle.internal.scheduler.NodeExecutor
import org.gradle.internal.scheduler.NodeSuspendedEvent
import org.gradle.internal.scheduler.Scheduler

import javax.annotation.Nullable
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DefaultSchedulerParallelTest extends AbstractSchedulerTest {

    Scheduler scheduler = new DefaultScheduler(cancellationHandler, concurrentNodeExecutionCoordinator, new ParallelWorkerService(4))

    private static class ParallelWorkerService implements NodeExecutionWorkerService {
        private final BlockingQueue<Worker> workers;
        private final ExecutorService executor
        private NodeExecutor nodeExecutor
        private BlockingQueue<Event> eventQueue

        ParallelWorkerService(int threads) {
            workers = Queues.newArrayBlockingQueue(threads)
            executor = Executors.newFixedThreadPool(threads)
            threads.times { index ->
                workers.add(new Worker())
            }
        }

        @Override
        NodeExecutionWorker getNextAvailableWorker() {
            return workers.poll()
        }

        @Override
        void start(NodeExecutor nodeExecutor, BlockingQueue<Event> eventQueue) {
            this.nodeExecutor = nodeExecutor
            this.eventQueue = eventQueue
        }

        @Override
        void close() {
            executor.shutdownNow()
        }

        private class Worker implements NodeExecutionWorker {
            @Override
            void execute(Node node, @Nullable ResourceLock resourceLock) {
                executor.execute {
                    if (resourceLock && !resourceLock.tryLock()) {
                        eventQueue.add(new NodeSuspendedEvent(node))
                        return
                    }
                    try {
                        executeNode(node, nodeExecutor, eventQueue)
                    } finally {
                        resourceLock?.unlock()
                    }
                    workers.add(Worker.this)
                }
            }
        }
    }

    def "schedules many tasks at once"() {
        given:
        def a = task(project: "a", "task")
        def b = task(project: "b", "task")
        def c = task(project: "c", "task")
        def d = task(project: "d", "task")
        def e = task(project: "e", "task")
        def f = task(project: "f", "task")
        def g = task(project: "g", "task")
        def h = task(project: "h", "task")
        determineExecutionPlan()

        expect:
        executesBatches([a, b, c, d, e, f, g, h])
    }

    def "schedules many tasks in dependency order"() {
        given:
        def a = task(project: "a", "task")
        def b = task(project: "b", "task")
        def c = task(project: "c", "task")
        def d = task(project: "d", "task")
        def e = task(project: "e", "task", dependsOn: [a, b, c, d])
        def f = task(project: "f", "task", dependsOn: [a, b, c, d])
        def g = task(project: "g", "task", dependsOn: [a, b, c, d])
        def h = task(project: "h", "task", dependsOn: [a, b, c, d])
        determineExecutionPlan()

        expect:
        executesBatches([a, b, c, d], [e, f, g, h])
    }

    void executesBatches(List<TaskInternal>... batches) {
        def actualNodes = new ArrayDeque<TaskInternal>(results.executedNodes*.task)
        def expectedBatches = new ArrayDeque<List<TaskInternal>>(batches as List)
        while (!expectedBatches.isEmpty()) {
            def expectedBatch = expectedBatches.remove()
            assert actualNodes.size() >= expectedBatch.size()
            def actualBatch = Sets.newLinkedHashSet()
            expectedBatch.size().times {
                actualBatch.add(actualNodes.remove())
            }
            assert actualBatch == (expectedBatch as Set)
        }
        assert actualNodes.isEmpty()
    }
}
