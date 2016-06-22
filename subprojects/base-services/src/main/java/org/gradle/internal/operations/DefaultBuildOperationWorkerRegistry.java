/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.operations;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.Stoppable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Note that this class currently does not consider the build operations being run by {@link DefaultBuildOperationProcessor}.
 */
public class DefaultBuildOperationWorkerRegistry implements BuildOperationWorkerRegistry, Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBuildOperationWorkerRegistry.class);
    private final int maxWorkerCount;
    private final Object lock = new Object();
    private int counter;
    private final List<Completion> workers = new ArrayList<Completion>();

    public DefaultBuildOperationWorkerRegistry(int maxWorkerCount) {
        this.maxWorkerCount = maxWorkerCount;
        LOGGER.debug("Using {} worker leases.", maxWorkerCount);
    }

    @Override
    public Completion workerStart() {

        synchronized (lock) {
            int workerId = counter++;
            while (workers.size() >= maxWorkerCount) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Worker {} waiting for a lease. Currently {} in use", workerId, workers.size());
                }
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
            Completion completion = new DefaultCompletion(workerId);
            workers.add(completion);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Worker { } started.", workerId);
            }
            return completion;
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            if (!workers.isEmpty()) {
                throw new IllegalStateException("Some build operations have not been marked as completed.");
            }
        }
    }

    private class DefaultCompletion implements Completion {
        private final int workerId;

        DefaultCompletion(int workerId) {
            this.workerId = workerId;
        }

        @Override
        public void workerCompleted() {
            synchronized (lock) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Worker {} completed", workerId);
                }
                workers.remove(this);
                lock.notifyAll();
            }
        }
    }
}
