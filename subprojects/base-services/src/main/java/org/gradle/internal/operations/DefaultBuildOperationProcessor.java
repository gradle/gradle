/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.operations;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.gradle.api.Action;
import org.gradle.internal.concurrent.ThreadFactoryImpl;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DefaultBuildOperationProcessor implements BuildOperationProcessor {

    private final ListeningExecutorService fixedSizePool;

    public DefaultBuildOperationProcessor(int maxThreads) {
        final ExecutorService underlyingExecutor;
        if (maxThreads < 0) {
            underlyingExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new ThreadFactoryImpl("build operations"));
        } else if (maxThreads == 0) {
            underlyingExecutor = MoreExecutors.sameThreadExecutor();
        } else {
            underlyingExecutor = Executors.newFixedThreadPool(maxThreads, new ThreadFactoryImpl("build operations"));
        }
        fixedSizePool = MoreExecutors.listeningDecorator(underlyingExecutor);
    }

    public <T> OperationQueue<T> newQueue(Action<? super T> worker) {
        return new DefaultOperationQueue<T>(fixedSizePool, worker);
    }
}
