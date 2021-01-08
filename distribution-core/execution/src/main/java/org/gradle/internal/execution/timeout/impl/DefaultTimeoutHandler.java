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

import com.google.common.io.CharStreams;
import org.gradle.api.Describable;
import org.gradle.internal.concurrent.ManagedScheduledExecutor;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.execution.timeout.Timeout;
import org.gradle.internal.execution.timeout.TimeoutHandler;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.time.CountdownTimer;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.TimeFormatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DefaultTimeoutHandler implements TimeoutHandler, Stoppable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTimeoutHandler.class);

    // Only intended to be used for integration testing
    public static final String POST_TIMEOUT_CHECK_FREQUENCY_PROPERTY = DefaultTimeoutHandler.class.getName() + ".postTimeoutCheckFrequency";
    public static final String SLOW_STOP_LOG_STACKTRACE_FREQUENCY_PROPERTY = DefaultTimeoutHandler.class.getName() + ".slowStopLogStacktraceFrequency";

    private final ManagedScheduledExecutor executor;
    private final CurrentBuildOperationRef currentBuildOperationRef;

    public DefaultTimeoutHandler(ManagedScheduledExecutor executor, CurrentBuildOperationRef currentBuildOperationRef) {
        this.executor = executor;
        this.currentBuildOperationRef = currentBuildOperationRef;
    }

    @Override
    public Timeout start(Thread taskExecutionThread, Duration timeout, Describable workUnitDescription, @Nullable BuildOperationRef buildOperationRef) {
        return new DefaultTimeout(taskExecutionThread, timeout, workUnitDescription, buildOperationRef);
    }

    @Override
    public void stop() {
        executor.stop();
    }

    // Value is queried “dynamically” to support testing
    private static long postTimeoutCheckFrequency() {
        return Integer.parseInt(System.getProperty(POST_TIMEOUT_CHECK_FREQUENCY_PROPERTY, "3000"));
    }

    // Value is queried “dynamically” to support testing
    private static long slowStopLogStacktraceFrequency() {
        return Integer.parseInt(System.getProperty(SLOW_STOP_LOG_STACKTRACE_FREQUENCY_PROPERTY, "10000"));
    }

    private final class DefaultTimeout implements Timeout {

        private final Thread thread;
        private final Duration timeout;
        private final Describable workUnitDescription;

        @Nullable
        private final BuildOperationRef buildOperationRef;

        private final Object lock = new Object();

        private boolean slowStop;
        private boolean stopped;
        private boolean interrupted;

        private StackTraceElement[] lastStacktrace;
        private CountdownTimer logStacktraceTimer;

        private ScheduledFuture<?> scheduledFuture;

        private DefaultTimeout(Thread thread, Duration timeout, Describable workUnitDescription, @Nullable BuildOperationRef buildOperationRef) {
            this.thread = thread;
            this.timeout = timeout;
            this.workUnitDescription = workUnitDescription;
            this.buildOperationRef = buildOperationRef;
            this.scheduledFuture = executor.schedule(this::onTimeout, timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        private void onTimeout() {
            synchronized (lock) {
                if (!stopped) {
                    interrupted = true;
                    doAsPartOfBuildOperation(() -> LOGGER.warn("Requesting stop of {} as it has exceeded its configured timeout of {}.", workUnitDescription.getDisplayName(), TimeFormatting.formatDurationTerse(timeout.toMillis())));
                    thread.interrupt();
                    logStacktraceTimer = Time.startCountdownTimer(slowStopLogStacktraceFrequency());
                    scheduledFuture = executor.schedule(this::onAfterTimeoutCheck, postTimeoutCheckFrequency(), TimeUnit.MILLISECONDS);
                }
            }
        }

        private void onAfterTimeoutCheck() {
            synchronized (lock) {
                if (!stopped) {
                    slowStop = true;
                    doAsPartOfBuildOperation(() -> {
                        LOGGER.warn("Timed out {} has not yet stopped.", workUnitDescription.getDisplayName());

                        if (logStacktraceTimer.hasExpired()) {
                            StackTraceElement[] currentStackTrace = thread.getStackTrace();
                            if (currentStackTrace.length > 0 && !Arrays.equals(lastStacktrace, currentStackTrace)) {
                                lastStacktrace = currentStackTrace;
                                logStacktrace(currentStackTrace);
                            }
                            logStacktraceTimer.reset();
                        }
                    });

                    thread.interrupt(); // interrupt again in case the work unit cleared the interrupt.

                    scheduledFuture = executor.schedule(this::onAfterTimeoutCheck, postTimeoutCheckFrequency(), TimeUnit.MILLISECONDS);
                }
            }
        }

        private void logStacktrace(StackTraceElement[] currentStackTrace) {
            if (LOGGER.isWarnEnabled()) {
                // Assemble string this way so it is logged atomically, and with platform line endings
                StringBuilder logMessageBuilder = new StringBuilder();
                try (PrintWriter logMessageWriter = new PrintWriter(CharStreams.asWriter(logMessageBuilder))) {
                    logMessageWriter.print("Current stacktrace of timed out but not yet stopped ");
                    logMessageWriter.print(workUnitDescription.getDisplayName());
                    logMessageWriter.println(":");
                    for (StackTraceElement traceElement : currentStackTrace) {
                        logMessageWriter.println("  at " + traceElement);
                    }
                }
                LOGGER.warn(logMessageBuilder.toString());
            }
        }

        private void doAsPartOfBuildOperation(Runnable runnable) {
            BuildOperationRef previousBuildOperationRef = currentBuildOperationRef.get();
            try {
                currentBuildOperationRef.set(this.buildOperationRef);
                runnable.run();
            } finally {
                currentBuildOperationRef.set(previousBuildOperationRef);
            }
        }

        @Override
        public boolean stop() {
            synchronized (lock) {
                scheduledFuture.cancel(true);
                stopped = true;
                if (slowStop) {
                    LOGGER.warn("Timed out {} has stopped.", workUnitDescription.getDisplayName());
                }
                return interrupted;
            }
        }
    }
}
