/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.testfixtures.internal;

import org.gradle.cache.FileLockManager;
import org.gradle.cache.internal.CacheFactory;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ParallelismConfigurationManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.DefaultBuildOperationIdFactory;
import org.gradle.internal.operations.DefaultBuildOperationQueueFactory;
import org.gradle.internal.progress.BuildOperationListener;
import org.gradle.internal.progress.DefaultBuildOperationExecutor;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.service.scopes.GlobalScopeServices;
import org.gradle.internal.time.Clock;
import org.gradle.internal.work.WorkerLeaseService;

public class TestGlobalScopeServices extends GlobalScopeServices {
    public TestGlobalScopeServices() {
        super(false);
    }

    @Override
    protected CacheFactory createCacheFactory(FileLockManager fileLockManager, ExecutorFactory executorFactory) {
        return new InMemoryCacheFactory();
    }

    BuildOperationExecutor createBuildOperationExecutor(ListenerManager listenerManager, Clock clock, WorkerLeaseService workerLeaseService, ProgressLoggerFactory progressLoggerFactory, ExecutorFactory executorFactory, ResourceLockCoordinationService resourceLockCoordinationService, ParallelismConfigurationManager parallelismConfigurationManager) {
        return new ProjectBuilderBuildOperationExecutor(listenerManager.getBroadcaster(BuildOperationListener.class), clock, progressLoggerFactory, new DefaultBuildOperationQueueFactory(workerLeaseService), executorFactory, resourceLockCoordinationService, parallelismConfigurationManager);
    }

    LoggingManagerInternal createLoggingManager(Factory<LoggingManagerInternal> loggingManagerFactory) {
        return loggingManagerFactory.create();
    }

    private static class ProjectBuilderBuildOperationExecutor extends DefaultBuildOperationExecutor {

        public ProjectBuilderBuildOperationExecutor(BuildOperationListener broadcaster, Clock clock, ProgressLoggerFactory progressLoggerFactory, DefaultBuildOperationQueueFactory defaultBuildOperationQueueFactory, ExecutorFactory executorFactory, ResourceLockCoordinationService resourceLockCoordinationService, ParallelismConfigurationManager parallelismConfigurationManager) {
            super(broadcaster, clock, progressLoggerFactory, defaultBuildOperationQueueFactory, executorFactory, resourceLockCoordinationService, parallelismConfigurationManager, new DefaultBuildOperationIdFactory());
            createRunningRootOperation("ProjectBuilder");
        }
    }
}
