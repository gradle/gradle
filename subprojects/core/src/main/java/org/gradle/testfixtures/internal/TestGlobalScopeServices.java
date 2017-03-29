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

import org.gradle.cache.internal.CacheFactory;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.progress.BuildOperationExecutor;
import org.gradle.internal.progress.BuildOperationListener;
import org.gradle.internal.progress.DefaultBuildOperationExecutor;
import org.gradle.internal.service.scopes.GlobalScopeServices;
import org.gradle.internal.time.TimeProvider;

public class TestGlobalScopeServices extends GlobalScopeServices {
    public TestGlobalScopeServices() {
        super(false);
    }

    @Override
    protected CacheFactory createCacheFactory(FileLockManager fileLockManager, ExecutorFactory executorFactory) {
        return new InMemoryCacheFactory();
    }

    BuildOperationExecutor createBuildOperationExecutor(ListenerManager listenerManager, TimeProvider timeProvider, ProgressLoggerFactory progressLoggerFactory) {
        return new ProjectBuilderBuildOperationExecutor(listenerManager.getBroadcaster(BuildOperationListener.class), timeProvider, progressLoggerFactory);
    }

    private static class ProjectBuilderBuildOperationExecutor extends DefaultBuildOperationExecutor {
        ProjectBuilderBuildOperationExecutor(BuildOperationListener listener, TimeProvider timeProvider, ProgressLoggerFactory progressLoggerFactory) {
            super(listener, timeProvider, progressLoggerFactory);
            createRunningRootOperation("ProjectBuilder");
        }
    }
}
