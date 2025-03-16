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
import org.gradle.internal.instrumentation.agent.AgentStatus;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.NoOpBuildOperationProgressEventEmitter;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.scopes.GlobalScopeServices;
import org.gradle.internal.time.Clock;

public class TestGlobalScopeServices extends GlobalScopeServices {
    public TestGlobalScopeServices() {
        super(false, AgentStatus.disabled());
    }

    @Provides
    @Override
    protected CacheFactory createCacheFactory(FileLockManager fileLockManager, ExecutorFactory executorFactory, BuildOperationRunner buildOperationRunner) {
        return new TestInMemoryCacheFactory();
    }

    @Provides
    LoggingManagerInternal createLoggingManager(Factory<LoggingManagerInternal> loggingManagerFactory) {
        return loggingManagerFactory.create();
    }

    @Provides
    @Override
    protected BuildOperationProgressEventEmitter createBuildOperationProgressEventEmitter(
        Clock clock,
        CurrentBuildOperationRef currentBuildOperationRef,
        BuildOperationListenerManager listenerManager
    ) {
        return new NoOpBuildOperationProgressEventEmitter();
    }
}
