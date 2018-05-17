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
import org.gradle.internal.resources.ResourceLock
import org.gradle.internal.scheduler.Event
import org.gradle.internal.scheduler.Node
import org.gradle.internal.scheduler.NodeExecutionWorker
import org.gradle.internal.scheduler.NodeExecutionWorkerService
import org.gradle.internal.scheduler.NodeExecutor
import org.gradle.internal.scheduler.NodeFailedEvent
import org.gradle.internal.scheduler.NodeFinishedEvent

import javax.annotation.Nullable
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TestNodeExecutionWorkerService implements NodeExecutionWorkerService {
    private final BlockingQueue<Worker> workers;
    private final ExecutorService executor
    private NodeExecutor nodeExecutor
    private BlockingQueue<Event> eventQueue

    TestNodeExecutionWorkerService(int threads) {
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
        NodeSchedulingResult schedule(Node node, @Nullable ResourceLock resourceLock) throws InterruptedException {
            def result = Queues.<NodeSchedulingResult>newArrayBlockingQueue(1)
            executor.execute {
                if (resourceLock && !resourceLock.tryLock()) {
                    result.add(NodeSchedulingResult.NO_RESOURCE_LOCK)
                    return
                } else {
                    result.add(NodeSchedulingResult.STARTED)
                }
                try {
                    def failure = nodeExecutor.execute(node)
                    if (failure) {
                        eventQueue.add(new NodeFailedEvent(node, failure))
                    } else {
                        eventQueue.add(new NodeFinishedEvent(node))
                    }
                } finally {
                    resourceLock?.unlock()
                }
                workers.add(Worker.this)
            }
            return result.take()
        }
    }
}
