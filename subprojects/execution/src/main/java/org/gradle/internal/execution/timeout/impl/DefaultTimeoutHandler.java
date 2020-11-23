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

import org.gradle.api.Describable;
import org.gradle.internal.concurrent.ManagedScheduledExecutor;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.execution.timeout.Timeout;
import org.gradle.internal.execution.timeout.TimeoutHandler;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.time.TimeFormatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DefaultTimeoutHandler implements TimeoutHandler, Stoppable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTimeoutHandler.class);

    // Only intended to be used for integration testing
    public static final String SLOW_STOP_CHECK_FREQUENCY_PROPERTY = DefaultTimeoutHandler.class.getName() + ".slowStopCheckFrequency";

    private final ManagedScheduledExecutor executor;

    private final CurrentBuildOperationRef currentBuildOperationRef;
    private final Duration slowStopCheckFrequency;

    public DefaultTimeoutHandler(ManagedScheduledExecutor executor, CurrentBuildOperationRef currentBuildOperationRef) {
        this(executor, currentBuildOperationRef, determineSlowStopCheckFrequency());
    }

    DefaultTimeoutHandler(ManagedScheduledExecutor executor, CurrentBuildOperationRef currentBuildOperationRef, Duration slowStopCheckFrequency) {
        this.executor = executor;
        this.currentBuildOperationRef = currentBuildOperationRef;
        this.slowStopCheckFrequency = slowStopCheckFrequency;
    }

    private static Duration determineSlowStopCheckFrequency() {
        return Duration.ofMillis(Integer.parseInt(System.getProperty(SLOW_STOP_CHECK_FREQUENCY_PROPERTY, "3000")));
    }

    @Override
    public Timeout start(Thread taskExecutionThread, Duration timeout, Describable workUnitDescription, @Nullable BuildOperationRef buildOperationRef) {
        return new DefaultTimeout(taskExecutionThread, timeout, workUnitDescription, buildOperationRef);
    }

    @Override
    public void stop() {
        executor.stop();
    }

    private final class DefaultTimeout implements Timeout {

        private final Thread thread;
        private final Duration timeout;
        private final Describable workUnitDescription;

        @Nullable
        private final BuildOperationRef buildOperationRef;

        private volatile boolean slowStop;
        private volatile boolean stopped;
        private volatile boolean interrupted;

        private volatile ScheduledFuture<?> scheduledFuture;

        private DefaultTimeout(Thread thread, Duration timeout, Describable workUnitDescription, @Nullable BuildOperationRef buildOperationRef) {
            this.thread = thread;
            this.timeout = timeout;
            this.workUnitDescription = workUnitDescription;
            this.buildOperationRef = buildOperationRef;

            scheduledFuture = executor.schedule(this::interrupt, timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        private void interrupt() {
            if (!stopped) {
                interrupted = true;
                withSetCurrentBuildOperationRef(() -> LOGGER.warn("Requesting stop of " + workUnitDescription.getDisplayName() + " as it has exceeded its configured timeout of " + TimeFormatting.formatDurationTerse(timeout.toMillis()) + "."));
                thread.interrupt();
                scheduledFuture = executor.schedule(this::warnIfNotStopped, slowStopCheckFrequency.toMillis(), TimeUnit.MILLISECONDS);
            }
        }

        private void withSetCurrentBuildOperationRef(Runnable runnable) {
            BuildOperationRef previousBuildOperationRef = currentBuildOperationRef.get();
            try {
                currentBuildOperationRef.set(this.buildOperationRef);
                runnable.run();
            } finally {
                currentBuildOperationRef.set(previousBuildOperationRef);
            }
        }

        private void warnIfNotStopped() {
            if (!stopped) {
                slowStop = true;
                withSetCurrentBuildOperationRef(() -> LOGGER.warn("Timed out " + workUnitDescription.getDisplayName() + " has not yet stopped."));
                scheduledFuture = executor.schedule(this::warnIfNotStopped, slowStopCheckFrequency.toMillis(), TimeUnit.MILLISECONDS);
            }
        }

        @Override
        public boolean stop() {
            stopped = true;
            scheduledFuture.cancel(true);
            if (slowStop) {
                LOGGER.warn("Timed out " + workUnitDescription.getDisplayName() + " has stopped.");
            }
            return interrupted;
        }
    }
}
