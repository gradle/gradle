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
 * Used to obtain and release leases to run a build operation. There are a limited number of leases available and this service is used to allocate these to worker threads.
 *
 * Used where the operation cannot be packaged as a unit of work, for example when the operation is started and completed in response to separate
 * events.
 *
 * Where possible, use {@link BuildOperationProcessor} instead of this type.
 */
public interface BuildOperationWorkerRegistry {
    /**
     * Marks the start of a build operation, reserving a lease. Blocks until a lease is available.
     *
     * <p>Note that the caller must call {@link Completion#operationFinish()} to mark the completion of the operation and to release the lease for other threads to use.
     */
    Completion operationStart();

    /**
     * Returns the build operation associated with the current thread. Allows child operations to be created for this operation. Fails when there is no operation associated with this thread.
     */
    Operation getCurrent();

    interface Operation {
        /**
         * Starts a child operation of the current worker. Marks the start of the build operation, reserving a lease. Blocks until a lease is available.
         * Allows one child operation to proceed without a lease, so that the child effectively borrows the parent's lease, on the assumption that the parent is not doing any real work while children are running.
         *
         * <p>Note that the caller must call {@link Completion#operationFinish()} to mark the completion of the operation and to release the lease for other threads to use.
         */
        Completion operationStart();
    }

    interface Completion {
        /**
         * Marks the completion of a build operation, releasing the lease.
         */
        void operationFinish();
    }
}
