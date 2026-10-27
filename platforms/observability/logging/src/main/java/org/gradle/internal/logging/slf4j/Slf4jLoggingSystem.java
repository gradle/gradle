/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.logging.slf4j;

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.config.LoggingSourceSystem;
import org.gradle.internal.logging.events.OutputEventListener;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

/**
 * Configures Gradle's SLF4J binding to route logging events to a provided {@link OutputEventListener}.
 */
@NullMarked
public class Slf4jLoggingSystem implements LoggingSourceSystem {

    private final OutputEventListener outputEventListener;
    private LogLevel logLevel = LogLevel.LIFECYCLE;

    private boolean enabled;

    public Slf4jLoggingSystem(OutputEventListener outputEventListener) {
        this.outputEventListener = outputEventListener;
    }

    @Override
    public Snapshot snapshot() {
        OutputEventListenerBackedLoggerContext context = getContext();
        if (context == null) {
            return new Slf4jSnapshot(enabled, logLevel, null, null);
        }
        return new Slf4jSnapshot(enabled, logLevel, context.getOutputEventListener(), context.getLevel());
    }

    @Override
    public Snapshot setLevel(LogLevel logLevel) {
        Snapshot snapshot = snapshot();
        if (this.logLevel != logLevel) {
            this.logLevel = logLevel;
            if (enabled) {
                OutputEventListenerBackedLoggerContext context = getContext();
                if (context != null) {
                    context.setLevel(logLevel);
                }
            }
        }
        return snapshot;
    }

    @Override
    public Snapshot startCapture() {
        Snapshot snapshot = snapshot();
        if (!enabled) {
            this.enabled = true;
            OutputEventListenerBackedLoggerContext context = getContext();
            if (context != null) {
                context.setOutputEventListener(outputEventListener);
                context.setLevel(logLevel);
            }
        }
        return snapshot;
    }

    @Override
    public void restore(Snapshot state) {
        Slf4jSnapshot snapshot = (Slf4jSnapshot) state;
        this.logLevel = snapshot.level;
        this.enabled = snapshot.enabled;
        OutputEventListenerBackedLoggerContext context = getContext();
        if (context != null) {
            if (snapshot.contextListener != null) {
                context.setOutputEventListener(snapshot.contextListener);
            }
            if (snapshot.contextLevel != null) {
                context.setLevel(snapshot.contextLevel);
            }
        }
    }

    private static @Nullable OutputEventListenerBackedLoggerContext getContext() {
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        if (!(loggerFactory instanceof OutputEventListenerBackedLoggerContext)) {
            // Cannot configure the SLF4J logger. This will happen if:
            // - Tests are executed with a custom classloader (e.g using `java.system.class.loader`)
            // - Tests are run with `--module-path`, effectively hiding Gradle classes
            return null;
        }
        return (OutputEventListenerBackedLoggerContext) loggerFactory;
    }

    private static class Slf4jSnapshot implements Snapshot {

        private final boolean enabled;
        private final LogLevel level;
        private final @Nullable OutputEventListener contextListener;
        private final @Nullable LogLevel contextLevel;

        Slf4jSnapshot(
            boolean enabled,
            LogLevel level,
            @Nullable OutputEventListener contextListener,
            @Nullable LogLevel contextLevel
        ) {
            this.enabled = enabled;
            this.level = level;
            this.contextListener = contextListener;
            this.contextLevel = contextLevel;
        }

    }

}
