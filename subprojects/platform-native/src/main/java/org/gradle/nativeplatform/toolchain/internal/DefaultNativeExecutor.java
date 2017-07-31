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

package org.gradle.nativeplatform.toolchain.internal;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.gradle.api.tasks.WorkResult;
import org.gradle.concurrent.ParallelismConfiguration;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.concurrent.ParallelismConfigurationListener;
import org.gradle.internal.concurrent.ParallelismConfigurationManager;
import org.gradle.internal.concurrent.Stoppable;

import java.util.concurrent.Callable;

public class DefaultNativeExecutor implements NativeExecutor, ParallelismConfigurationListener, Stoppable {
    private final ParallelismConfigurationManager parallelismConfigurationManager;
    private final ListeningExecutorService executorService;
    private final ManagedExecutor managedExecutor;

    public DefaultNativeExecutor(ExecutorFactory executorFactory, ParallelismConfigurationManager parallelismConfigurationManager) {
        this.managedExecutor = executorFactory.create("Native executor", parallelismConfigurationManager.getParallelismConfiguration().getMaxWorkerCount());
        this.executorService = MoreExecutors.listeningDecorator(managedExecutor);
        this.parallelismConfigurationManager = parallelismConfigurationManager;
        parallelismConfigurationManager.addListener(this);
    }

    @Override
    public void onParallelismConfigurationChange(ParallelismConfiguration parallelismConfiguration) {
        managedExecutor.setFixedPoolSize(parallelismConfiguration.getMaxWorkerCount());
    }

    @Override
    public void stop() {
        parallelismConfigurationManager.removeListener(this);
        executorService.shutdown();
    }

    @Override
    public <T extends WorkResult> ListenableFuture<T> submit(Callable<T> callable) {
        return executorService.submit(callable);
    }
}
