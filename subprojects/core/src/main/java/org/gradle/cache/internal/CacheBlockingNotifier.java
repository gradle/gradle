/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.cache.internal;

import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.BlockingNotifier;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.work.WorkerLeaseService;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bridges the long-lived, {@link Scope.Global Global}-scoped caches (e.g.
 * {@code DefaultCacheFactory}) to the per-session {@link WorkerLeaseService}, which is the
 * real {@link BlockingNotifier} but lives in a narrower {@link Scope.CrossBuildSession}
 * scope. A static dependency is impossible, so the lease service is resolved dynamically at
 * block-time from a registry of the sessions currently active.
 *
 * <p>When a cache must block waiting for in-process ownership it calls {@link #blocking}; this
 * holder picks a registered lease service that owns the current (worker) thread and delegates,
 * letting that service drop and reacquire the thread's worker lease and project locks for the
 * duration of the wait. Outside any session, or on a non-worker thread, it behaves exactly
 * like {@link BlockingNotifier#NO_NOTIFICATION} and just runs the action.
 */
@ServiceScope(Scope.Global.class)
public class CacheBlockingNotifier implements BlockingNotifier {

    /**
     * Active session lease services, in registration order. Iterated back-to-front at
     * block-time so the most-recently-registered service that owns the current thread wins
     * (the relevant one for a nested session). Delegating to a service that does not own the
     * thread's locks is a no-op rather than a corruption, so a wrong pick is still safe.
     */
    private final List<WorkerLeaseService> leaseServices = new CopyOnWriteArrayList<>();

    public void register(WorkerLeaseService leaseService) {
        leaseServices.add(leaseService);
    }

    public void unregister(WorkerLeaseService leaseService) {
        leaseServices.remove(leaseService);
    }

    @Override
    public void blocking(Runnable action) {
        WorkerLeaseService leaseService = leaseServiceForCurrentThread();
        if (leaseService != null) {
            leaseService.blocking(action);
        } else {
            action.run();
        }
    }

    @Override
    public <T extends @Nullable Object> T blocking(Factory<T> action) {
        WorkerLeaseService leaseService = leaseServiceForCurrentThread();
        if (leaseService != null) {
            return leaseService.blocking(action);
        }
        return action.create();
    }

    @Nullable
    private WorkerLeaseService leaseServiceForCurrentThread() {
        List<WorkerLeaseService> services = leaseServices;
        for (int i = services.size() - 1; i >= 0; i--) {
            WorkerLeaseService leaseService = services.get(i);
            if (leaseService.isWorkerThread()) {
                return leaseService;
            }
        }
        return null;
    }
}
