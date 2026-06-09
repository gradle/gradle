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

package org.gradle.internal.work;

import org.gradle.cache.internal.CacheBlockingNotifier;
import org.gradle.internal.resources.ResourceLockCoordinationService;

/**
 * The session {@link WorkerLeaseService} that also publishes itself to the Global-scoped
 * {@link CacheBlockingNotifier}, so that long-lived caches can compensate for a
 * blocked worker by dropping this session's worker lease and project locks (see
 * {@link CacheBlockingNotifier}). Registration is tied to this service's own lifecycle:
 * registered on construction, unregistered when the registry stops it — including when
 * {@link #stop()} would otherwise throw because leases remain open — so the Global holder never
 * retains a stale, stopped lease service.
 */
public class CacheNotifyingWorkerLeaseService extends DefaultWorkerLeaseService {

    private final CacheBlockingNotifier cacheBlockingNotifier;

    public CacheNotifyingWorkerLeaseService(
        ResourceLockCoordinationService coordinationService,
        WorkerLimits workerLimits,
        ResourceLockStatistics resourceLockStatistics,
        CacheBlockingNotifier cacheBlockingNotifier
    ) {
        super(coordinationService, workerLimits, resourceLockStatistics);
        this.cacheBlockingNotifier = cacheBlockingNotifier;
        // Safe this-escape: blocking() only reads super state initialized above and the field
        // set on the previous line. Keep it that way if adding state read from blocking().
        cacheBlockingNotifier.register(this);
    }

    @Override
    public void stop() {
        try {
            super.stop();
        } finally {
            cacheBlockingNotifier.unregister(this);
        }
    }
}
