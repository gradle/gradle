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

import org.gradle.internal.Factory;
import org.gradle.internal.resources.ProjectLeaseRegistry;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Collection;

@ServiceScope(Scopes.BuildSession.class)
public interface WorkerLeaseService extends WorkerLeaseRegistry, ProjectLeaseRegistry, WorkerThreadRegistry {
    /**
     * Returns the maximum number of worker leases that this service will grant at any given time. Note that the actual limit may vary over time but will never _exceed_ the value returned by this method.
     */
    int getMaxWorkerCount();

    /**
     * Runs a given {@link Factory} while the specified locks are being held, releasing
     * the locks upon completion.  Blocks until the specified locks can be obtained.
     */
    <T> T withLocks(Collection<? extends ResourceLock> locks, Factory<T> factory);

    /**
     * Runs a given {@link Runnable} while the specified locks are being held, releasing
     * the locks upon completion.  Blocks until the specified locks can be obtained.
     */
    void withLocks(Collection<? extends ResourceLock> locks, Runnable runnable);

    /**
     * Runs the given {@link Factory} while the specified locks are released and the given new lock is acquired. On completion,
     * the new lock is released and the old locks reacquired.
     * If a lock cannot be immediately (re)acquired, the current worker lease will be released and the method will block until the locks are (re)acquired.
     */
    <T> T withReplacedLocks(Collection<? extends ResourceLock> currentLocks, ResourceLock newLock, Factory<T> factory);

    /**
     * Runs a given {@link Factory} while the specified locks are released and then reacquire the locks
     * upon completion.  If the locks cannot be immediately reacquired, the current worker lease will be released
     * and the method will block until the locks are reacquired.
     */
    <T> T withoutLocks(Collection<? extends ResourceLock> locks, Factory<T> factory);

    /**
     * Runs a given {@link Runnable} while the specified locks are released and then reacquire the locks
     * upon completion.  If the locks cannot be immediately reacquired, the current worker lease will be released
     * and the method will block until the locks are reacquired.
     */
    void withoutLocks(Collection<? extends ResourceLock> locks, Runnable runnable);

    /**
     * Runs a given {@link Runnable} while the specified lock is released and then reacquire the lock
     * upon completion.  If the lock cannot be immediately reacquired, the current worker lease will be released
     * and the method will block until the locks are reacquired.
     */
    void withoutLock(ResourceLock lock, Runnable runnable);

    Synchronizer newResource();
}
