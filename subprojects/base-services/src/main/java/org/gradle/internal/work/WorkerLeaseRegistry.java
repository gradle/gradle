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
     * Returns the worker lease associated with the current thread.
     *
     * Fails when there is no lease associated with this thread.
     */
    WorkerLease getCurrentWorkerLease();

    /**
     * Creates a new {@link ResourceLock} that can be used to reserve a worker lease.  Note that this does not actually reserve a lease,
     * it simply creates a {@link ResourceLock} representing the worker lease.  The worker lease can be reserved only when
     * {@link ResourceLock#tryLock()} is called from a {@link org.gradle.internal.resources.ResourceLockCoordinationService#withStateLock(org.gradle.api.Transformer)}
     * transform.
     */
    WorkerLease newWorkerLease();

    interface WorkerLease extends ResourceLock {
    }

    interface WorkerLeaseCompletion {
        /**
         * Marks the completion of a worker lease, releasing the lease.
         */
        void leaseFinish();
    }
}
