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

/**
 * Used to obtain and release leases to act as a build operation worker. There are a limited number of worker leases available and this service is used to allocate these to worker threads.
 *
 * Used where the operation cannot be packaged as a unit of work, for example when the operation is started and completed in response to separate
 * events.
 *
 * Where possible, use {@link BuildOperationQueueFactory} instead of this type.
 */
public interface BuildOperationWorkerRegistry {
    /**
     * Marks the start of a build operation, reserving a worker lease. Blocks until a lease is available. The caller must call {@link Completion#workerCompleted()} to release the lease for other threads.
     */
    Completion workerStart();

    interface Completion {
        /**
         * Marks the completion of a build operation, releasing the worker lease.
         */
        void workerCompleted();
    }
}
