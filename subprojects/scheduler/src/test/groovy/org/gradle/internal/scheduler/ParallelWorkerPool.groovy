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

package org.gradle.internal.scheduler

import com.google.common.collect.Queues

import java.util.concurrent.BlockingQueue

class ParallelWorkerPool implements WorkerPool {
    private static final MAX_WAIT_TIME = 100
    private final BlockingQueue<Worker> availableWorkers = Queues.newLinkedBlockingQueue()
    private final List<Thread> threads

    ParallelWorkerPool(int count) {
        threads = []
        count.times { index ->
            def worker = new Worker(index)
            def thread = new Thread(worker)
            thread.setName("Worker #${index + 1}")
            availableWorkers.add(worker)
            threads.add(thread)
            thread.start()
        }
    }

    @Override
    boolean tryRunWithAnAllocatedWorker(Runnable work) {
        def worker = availableWorkers.poll()
        if (worker) {
            worker.submit(work)
            println "> Work submitted to $worker"
            return true
        } else {
            println "< Couldn't find available worker"
            return false
        }
    }

    private class Worker implements Runnable {
        private final int index
        private final BlockingQueue<Runnable> queue = Queues.newArrayBlockingQueue(1);

        Worker(int index) {
            this.index = index
        }

        void submit(Runnable work) {
            queue.add(work)
        }

        @Override
        void run() {
            while (true) {
                try {
                    def work = queue.take()
                    def sleepTime = (long) ((0.5 + Math.random() / 2) * MAX_WAIT_TIME)
                    println ">> Running on $this after $sleepTime ms of sleep time"
                    Thread.sleep(sleepTime)
                    work.run()
                    availableWorkers.add(this)
                    println "<< $this is finished"
                } catch (InterruptedException ignored) {
                    break
                }
            }
        }

        @Override
        String toString() {
            return "Worker #${index + 1}"
        }
    }

    @Override
    void close() {
        threads.each { thread ->
            thread.interrupt()
        }
    }
}
