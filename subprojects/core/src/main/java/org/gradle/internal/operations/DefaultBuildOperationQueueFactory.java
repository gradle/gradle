/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.work.WorkerLeaseService;

public class DefaultBuildOperationQueueFactory implements BuildOperationQueueFactory {
    private final WorkerLeaseService workerLeaseService;

    public DefaultBuildOperationQueueFactory(WorkerLeaseService workerLeaseService) {
        this.workerLeaseService = workerLeaseService;
    }

    @Override
    public <T extends BuildOperation> BuildOperationQueue<T>
    create(ManagedExecutor executor, boolean allowAccessToProjectState, BuildOperationQueue.QueueWorker<T> worker) {
        // Assert that the current thread is a worker
        workerLeaseService.getCurrentWorkerLease();
        return new DefaultBuildOperationQueue<>(allowAccessToProjectState, workerLeaseService, executor, worker);
    }
}
