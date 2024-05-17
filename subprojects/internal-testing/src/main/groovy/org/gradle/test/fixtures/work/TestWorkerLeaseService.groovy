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

import org.gradle.internal.Factory
import org.gradle.internal.resources.ResourceLock
import org.gradle.internal.work.Synchronizer
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.util.Path

class TestWorkerLeaseService implements WorkerLeaseService {
    @Override
    ResourceLock getProjectLock(Path buildIdentityPath, Path projectPath) {
        return null
    }

    @Override
    ResourceLock getTaskExecutionLock(Path buildIdentityPath, Path projectIdentityPath) {
        return null
    }

    @Override
    WorkerLeaseCompletion startWorker() {
        return new WorkerLeaseCompletion() {
            @Override
            void leaseFinish() {
            }
        }
    }

    @Override
    WorkerLeaseCompletion maybeStartWorker() {
        throw new UnsupportedOperationException()
    }

    @Override
    ResourceLock getAllProjectsLock(Path buildIdentityPath) {
        throw new UnsupportedOperationException()
    }

    @Override
    Collection<? extends ResourceLock> getCurrentProjectLocks() {
        throw new UnsupportedOperationException()
    }

    @Override
    void runAsIsolatedTask() {
    }

    @Override
    boolean getAllowsParallelExecution() {
        return false
    }

    @Override
    int getMaxWorkerCount() {
        return 0
    }

    @Override
    WorkerLease getCurrentWorkerLease() {
        return workerLease()
    }

    @Override
    <T> T runAsWorkerThread(Factory<T> action) {
        return action.create()
    }

    @Override
    void runAsWorkerThread(Runnable action) {
        action.run()
    }

    @Override
    void runAsUnmanagedWorkerThread(Runnable action) {
        action.run()
    }

    @Override
    Synchronizer newResource() {
        return new Synchronizer() {
            @Override
            void withLock(Runnable action) {
                action.run()
            }

            @Override
            <T> T withLock(Factory<T> action) {
                return action.create()
            }
        }
    }

    @Override
    boolean isWorkerThread() {
        return true
    }

    @Override
    void runAsIsolatedTask(Runnable runnable) {
        runnable.run()
    }

    @Override
    <T> T runAsIsolatedTask(Factory<T> action) {
        return action.create()
    }

    @Override
    WorkerLease newWorkerLease() {
        return workerLease()
    }

    @Override
    <T> T withLocks(Collection<? extends ResourceLock> locks, Factory<T> factory) {
        return factory.create()
    }

    @Override
    void withLocks(Collection<? extends ResourceLock> locks, Runnable action) {
        action.run()
    }

    @Override
    <T> T withoutLocks(Collection<? extends ResourceLock> locks, Factory<T> factory) {
        return factory.create()
    }

    @Override
    void withoutLocks(Collection<? extends ResourceLock> locks, Runnable action) {
        action.run()
    }

    @Override
    void withoutLock(ResourceLock lock, Runnable action) {
        action.run()
    }

    @Override
    <T> T withReplacedLocks(Collection<? extends ResourceLock> currentLocks, ResourceLock newLock, Factory<T> factory) {
        return factory.create()
    }

    @Override
    <T> T whileDisallowingProjectLockChanges(Factory<T> action) {
        return action.create()
    }

    @Override
    void blocking(Runnable action) {
        action.run()
    }

    @Override
    <T> T allowUncontrolledAccessToAnyProject(Factory<T> factory) {
        return factory.create()
    }

    @Override
    boolean isAllowedUncontrolledAccessToAnyProject() {
        return false
    }

    private WorkerLease workerLease() {
        return new WorkerLease() {
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
