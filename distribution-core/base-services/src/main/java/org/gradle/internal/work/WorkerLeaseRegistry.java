/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.work;

import org.gradle.internal.resources.ResourceLock;

/**
 * Used to obtain and release worker leases to run work. There are a limited number of leases available and this service is used to allocate these to worker threads.
 *
 * Used where the operation cannot be packaged as a unit of work, for example when the operation is started and completed in response to separate
 * events.
 */
public interface WorkerLeaseRegistry {
    /**
     * Returns the worker lease associated with the current thread. Allows child leases to be created for this lease. Fails when there is no lease associated with this thread.
     */
    WorkerLease getCurrentWorkerLease();

    /**
     * Gets a {@link ResourceLock} that can be used to reserve a worker lease.  Note that this does not actually reserve a lease,
     * it simply creates a {@link ResourceLock} representing the worker lease.  The worker lease can be reserved only when
     * {@link ResourceLock#tryLock()} is called from a {@link org.gradle.internal.resources.ResourceLockCoordinationService#withStateLock(org.gradle.api.Transformer)}
     * transform.
     */
    WorkerLease getWorkerLease();

    /**
     * For the given action, update the worker lease registry to associate the current thread with the worker lease.
     * Note that this does not actually reserve the worker lease.
     *
     * @param sharedLease Lease to associate as shared
     * @param action action to execute
     */
    void withSharedLease(WorkerLease sharedLease, Runnable action);

    interface WorkerLease extends ResourceLock {
        /**
         * Creates a child lease of the current worker lease, but does not acquire the lease.  For use with {@link org.gradle.internal.resources.ResourceLockCoordinationService#withStateLock(org.gradle.api.Transformer)}
         * to coordinate the locking of multiple resources.
         */
        WorkerLease createChild();

        /**
         * Starts a child lease of the current worker lease. Marks the reservation of a lease. Blocks until a lease is available.
         * Allows one child lease to proceed without a lease, so that the child effectively borrows the parent's lease, on the assumption that the parent is not doing any real work while children are running.
         *
         * <p>Note that the caller must call {@link WorkerLeaseCompletion#leaseFinish()} to mark the completion of the lease and to release the lease for other threads to use.
         */
        WorkerLeaseCompletion startChild();
    }

    interface WorkerLeaseCompletion {
        /**
         * Marks the completion of a worker lease, releasing the lease.
         */
        void leaseFinish();
    }
}
