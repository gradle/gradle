/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MockExecutor implements ScheduledExecutorService {
    private final List<Runnable> singleScheduledActions = new CopyOnWriteArrayList<Runnable>();
    private final List<Runnable> fixedScheduledActions = new CopyOnWriteArrayList<Runnable>();
    private boolean shutdownInitiated;

    public void runSingleScheduledActionsNow() {
        runNow(singleScheduledActions);
    }

    public void runFixedScheduledActionsNow() {
        runNow(fixedScheduledActions);
    }

    private void runNow(List<Runnable> actions) {
        while (!actions.isEmpty()) {
            actions.remove(0).run();
        }
    }

    @Override
    public void execute(Runnable command) {
        singleScheduledActions.add(command);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        singleScheduledActions.add(command);
        return null;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        fixedScheduledActions.add(command);
        return null;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        fixedScheduledActions.add(command);
        return null;
    }

    @Override
    public void shutdown() {
        shutdownInitiated = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdownInitiated = true;
        List<Runnable> allActions = new ArrayList<Runnable>();
        allActions.addAll(singleScheduledActions);
        allActions.addAll(fixedScheduledActions);
        return allActions;
    }

    @Override
    public boolean isShutdown() {
        return shutdownInitiated;
    }

    @Override
    public boolean isTerminated() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<?> submit(Runnable task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        throw new UnsupportedOperationException();
    }
}
