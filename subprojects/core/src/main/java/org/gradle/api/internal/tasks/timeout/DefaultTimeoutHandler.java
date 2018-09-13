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

package org.gradle.api.internal.tasks.timeout;

import org.gradle.internal.concurrent.ManagedScheduledExecutor;
import org.gradle.internal.concurrent.Stoppable;

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
        long timeoutMillis = timeout.toMillis();
        long limit = System.currentTimeMillis() + timeoutMillis;
        InterruptOnTimeout interrupter = new InterruptOnTimeout(taskExecutionThread, limit);
        ScheduledFuture<?> periodicCheck = executor.scheduleAtFixedRate(interrupter, timeoutMillis, 50, TimeUnit.MILLISECONDS);
        return new DefaultTimeout(interrupter, periodicCheck);
    }

    @Override
    public void stop() {
        executor.stop();
    }

    private static final class DefaultTimeout implements Timeout {

        private final InterruptOnTimeout interrupter;
        private final ScheduledFuture<?> periodicCheck;

        private DefaultTimeout(InterruptOnTimeout interrupter, ScheduledFuture<?> periodicCheck) {
            this.interrupter = interrupter;
            this.periodicCheck = periodicCheck;
        }

        @Override
        public void stop() {
            periodicCheck.cancel(true);
        }

        @Override
        public boolean timedOut() {
            return interrupter.timedOut;
        }
    }

    private static class InterruptOnTimeout implements Runnable {
        private final Thread thread;
        private final long limit;

        private volatile boolean timedOut;

        private InterruptOnTimeout(Thread thread, long limit) {
            this.thread = thread;
            this.limit = limit;
        }

        @Override
        public void run() {
            if (timedOut) {
                return;
            }
            if (System.currentTimeMillis() > limit) {
                timedOut = true;
                thread.interrupt();
            }
        }
    }
}
