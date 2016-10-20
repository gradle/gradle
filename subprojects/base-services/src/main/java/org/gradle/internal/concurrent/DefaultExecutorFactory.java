/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.internal.concurrent;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DefaultExecutorFactory implements ExecutorFactory, Stoppable {
    private final Set<StoppableExecutor> executors = new CopyOnWriteArraySet<StoppableExecutor>();

    public void stop() {
        try {
            CompositeStoppable.stoppable(executors).stop();
        } finally {
            executors.clear();
        }
    }

    public StoppableExecutor create(String displayName) {
        StoppableExecutor executor = new TrackedStoppableExecutor(createExecutor(displayName), new ExecutorPolicy.CatchAndRecordFailures());
        executors.add(executor);
        return executor;
    }

    protected ExecutorService createExecutor(String displayName) {
        return Executors.newCachedThreadPool(new ThreadFactoryImpl(displayName));
    }

    public StoppableExecutor create(String displayName, int fixedSize) {
        StoppableExecutor executor = new TrackedStoppableExecutor(createExecutor(displayName, fixedSize), new ExecutorPolicy.CatchAndRecordFailures());
        executors.add(executor);
        return executor;
    }

    protected ExecutorService createExecutor(String displayName, int fixedSize) {
        return Executors.newFixedThreadPool(fixedSize, new ThreadFactoryImpl(displayName));
    }

    private class TrackedStoppableExecutor extends StoppableExecutorImpl {
        public TrackedStoppableExecutor(ExecutorService executor, ExecutorPolicy executorPolicy) {
            super(executor, executorPolicy);
        }

        public void stop(int timeoutValue, TimeUnit timeoutUnits) throws IllegalStateException {
            try {
                super.stop(timeoutValue, timeoutUnits);
            } finally {
                executors.remove(this);
            }
        }
    }
}
