/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.execution.timeout.impl;

import org.gradle.internal.concurrent.ManagedScheduledExecutor;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.execution.timeout.Timeout;
import org.gradle.internal.execution.timeout.TimeoutHandler;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DefaultTimeoutHandler implements TimeoutHandler, Stoppable {
    private final ManagedScheduledExecutor executor;

    public DefaultTimeoutHandler(ManagedScheduledExecutor executor) {
        this.executor = executor;
    }

    @Override
    public Timeout start(Thread taskExecutionThread, Duration timeout) {
        InterruptOnTimeout interrupter = new InterruptOnTimeout(taskExecutionThread);
        ScheduledFuture<?> timeoutTask = executor.schedule(interrupter, timeout.toMillis(), TimeUnit.MILLISECONDS);
        return new DefaultTimeout(timeoutTask, interrupter);
    }

    @Override
    public void stop() {
        executor.stop();
    }

    private static final class DefaultTimeout implements Timeout {
        private final ScheduledFuture<?> timeoutTask;
        private final InterruptOnTimeout interrupter;

        private DefaultTimeout(ScheduledFuture<?> timeoutTask, InterruptOnTimeout interrupter) {
            this.timeoutTask = timeoutTask;
            this.interrupter = interrupter;
        }

        @Override
        public boolean stop() {
            timeoutTask.cancel(true);
            return interrupter.interrupted;
        }
    }

    private static class InterruptOnTimeout implements Runnable {
        private final Thread thread;
        private volatile boolean interrupted;

        private InterruptOnTimeout(Thread thread) {
            this.thread = thread;
        }

        @Override
        public void run() {
            interrupted = true;
            thread.interrupt();
        }
    }
}
