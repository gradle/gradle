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
package org.gradle.internal.service.scopes;

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.DefaultExecutionHistoryCacheAccess;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.execution.plan.DefaultPlanExecutor;
import org.gradle.execution.plan.PlanExecutor;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ParallelismConfigurationManager;
import org.gradle.internal.execution.history.ExecutionHistoryCacheAccess;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.OutputFilesRepository;
import org.gradle.internal.execution.history.impl.DefaultExecutionHistoryStore;
import org.gradle.internal.execution.history.impl.DefaultOutputFilesRepository;
import org.gradle.internal.execution.timeout.TimeoutHandler;
import org.gradle.internal.execution.timeout.impl.DefaultTimeoutHandler;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.util.GradleVersion;

import java.util.Collections;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class TaskExecutionServices {
    TimeoutHandler createTaskTimeoutHandler(ExecutorFactory executorFactory) {
        return new DefaultTimeoutHandler(executorFactory.createScheduled("task timeouts", 1));
    }

    ExecutionHistoryCacheAccess createCacheAccess(Gradle gradle, CacheRepository cacheRepository, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        return new DefaultExecutionHistoryCacheAccess(gradle, cacheRepository, inMemoryCacheDecoratorFactory);
    }

    ExecutionHistoryStore createExecutionHistoryStore(ExecutionHistoryCacheAccess executionHistoryCacheAccess, StringInterner stringInterner) {
        return new DefaultExecutionHistoryStore(executionHistoryCacheAccess, stringInterner);
    }

    OutputFilesRepository createTaskOutputFilesRepository(CacheRepository cacheRepository, Gradle gradle, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        PersistentCache cacheAccess = cacheRepository
            .cache(gradle, "buildOutputCleanup")
            .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
            .withDisplayName("Build Output Cleanup Cache")
            .withLockOptions(mode(FileLockManager.LockMode.None))
            .withProperties(Collections.singletonMap("gradle.version", GradleVersion.current().getVersion()))
            .open();
        return new DefaultOutputFilesRepository(cacheAccess, inMemoryCacheDecoratorFactory);
    }

    PlanExecutor createTaskExecutorFactory(
        ParallelismConfigurationManager parallelismConfigurationManager,
        ExecutorFactory executorFactory,
        WorkerLeaseService workerLeaseService,
        BuildCancellationToken cancellationToken,
        ResourceLockCoordinationService coordinationService) {
        int parallelThreads = parallelismConfigurationManager.getParallelismConfiguration().getMaxWorkerCount();
        if (parallelThreads < 1) {
            throw new IllegalStateException(String.format("Cannot create executor for requested number of worker threads: %s.", parallelThreads));
        }

        // TODO: Make task plan executor respond to changes in parallelism configuration
        return new DefaultPlanExecutor(
            parallelismConfigurationManager.getParallelismConfiguration(),
            executorFactory,
            workerLeaseService,
            cancellationToken,
            coordinationService
        );
    }
}
