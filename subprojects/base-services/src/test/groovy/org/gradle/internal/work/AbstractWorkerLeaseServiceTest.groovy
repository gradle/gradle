/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.work

import org.gradle.concurrent.ParallelismConfiguration
import org.gradle.internal.concurrent.DefaultParallelismConfiguration
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.resources.ResourceLockContainer
import org.gradle.internal.resources.TestTrackedResourceLock
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

abstract class AbstractWorkerLeaseServiceTest extends ConcurrentSpec {
    final coordinationService = new DefaultResourceLockCoordinationService()

    WorkerLeaseService workerLeaseService(int maxWorkers = 1) {
        def configuration = parallel(maxWorkers)
        return workerLeaseService(configuration)
    }

    WorkerLeaseService workerLeaseService(ParallelismConfiguration configuration) {
        def service = new DefaultWorkerLeaseService(coordinationService, configuration)
        service.startProjectExecution(configuration.parallelProjectExecutionEnabled)
        return service
    }

    ParallelismConfiguration parallel(boolean parallelEnabled, int maxWorkers = 1) {
        return new DefaultParallelismConfiguration(parallelEnabled, maxWorkers)
    }

    ParallelismConfiguration notParallel() {
        return parallel(false)
    }

    ParallelismConfiguration parallel(int maxWorkers = 1) {
        return parallel(true, maxWorkers)
    }

    TestTrackedResourceLock resourceLock(String displayName, boolean locked, boolean hasLock = false) {
        return new TestTrackedResourceLock(displayName, coordinationService, Mock(ResourceLockContainer), locked, hasLock)
    }

    TestTrackedResourceLock resourceLock(String displayName) {
        return resourceLock(displayName, false)
    }
}
