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

package org.gradle.test.fixtures.work

import org.gradle.internal.resources.ResourceLock
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.internal.work.WorkerLeaseService

import java.util.concurrent.Callable


class TestWorkerLeaseService implements WorkerLeaseService {
    @Override
    ResourceLock getProjectLock(String gradlePath, String projectPath) {
        return null
    }

    @Override
    void stop() {
    }

    @Override
    int getMaxWorkerCount() {
        return 0
    }

    @Override
    WorkerLeaseRegistry.WorkerLease getCurrentWorkerLease() {
        return workerLease()
    }

    @Override
    void withSharedLease(WorkerLeaseRegistry.WorkerLease sharedLease, Runnable action) {
        throw new UnsupportedOperationException("not implemented yet")
    }

    @Override
    void withoutProjectLock(Runnable runnable) {
        runnable.run()
    }

    @Override
    def <T> T withoutProjectLock(Callable<T> action) {
        return action.call()
    }

    @Override
    WorkerLeaseRegistry.WorkerLease getWorkerLease() {
        return workerLease()
    }

    @Override
    def <T> T withLocks(Iterable<? extends ResourceLock> locks, Callable<T> action) {
        return action.call()
    }

    @Override
    void withLocks(Iterable<? extends ResourceLock> locks, Runnable action) {
        action.run()
    }

    @Override
    def <T> T withoutLocks(Iterable<? extends ResourceLock> locks, Callable<T> action) {
        return action.call()
    }

    @Override
    void withoutLocks(Iterable<? extends ResourceLock> locks, Runnable action) {
        action.run()
    }

    private WorkerLeaseRegistry.WorkerLease workerLease() {
        return new WorkerLeaseRegistry.WorkerLease() {
            @Override
            WorkerLeaseRegistry.WorkerLease createChild() {
                return null
            }

            @Override
            WorkerLeaseRegistry.WorkerLeaseCompletion startChild() {
                return null
            }

            @Override
            boolean isLocked() {
                return false
            }

            @Override
            boolean isLockedByCurrentThread() {
                return false
            }

            @Override
            boolean tryLock() {
                return false
            }

            @Override
            void unlock() {

            }

            @Override
            String getDisplayName() {
                return null
            }
        }
    }
}
