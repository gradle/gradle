/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.logging.services;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.LoggingOutputInternal;
import org.gradle.internal.logging.config.LoggingRouter;
import org.gradle.internal.logging.config.LoggingSourceSystem;
import org.gradle.internal.logging.config.LoggingSystem;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.text.StreamBackedStandardOutputListener;

import java.io.Closeable;
import java.io.OutputStream;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultLoggingManager implements LoggingManagerInternal, Closeable {
    private boolean started;
    private final StartableLoggingSystem slf4jLoggingSystem;
    private final StartableLoggingSystem stdOutLoggingSystem;
    private final StartableLoggingSystem stdErrLoggingSystem;
    private final StartableLoggingSystem javaUtilLoggingSystem;
    private final StartableLoggingRouter loggingRouter;
    private final LoggingOutputInternal loggingOutput;
    private final Set<StandardOutputListener> stdoutListeners = new LinkedHashSet<StandardOutputListener>();
    private final Set<StandardOutputListener> stderrListeners = new LinkedHashSet<StandardOutputListener>();
    private final Set<OutputEventListener> outputEventListeners = new LinkedHashSet<OutputEventListener>();

    public DefaultLoggingManager(LoggingSourceSystem slf4jLoggingSystem, LoggingSourceSystem javaUtilLoggingSystem, LoggingSourceSystem stdOutLoggingSystem,
                                 LoggingSourceSystem stdErrLoggingSystem, LoggingRouter loggingRouter) {
        this.loggingOutput = loggingRouter;
        this.loggingRouter = new StartableLoggingRouter(loggingRouter);
        this.slf4jLoggingSystem = new StartableLoggingSystem(slf4jLoggingSystem, null);
        this.stdOutLoggingSystem = new StartableLoggingSystem(stdOutLoggingSystem, null);
        this.stdErrLoggingSystem = new StartableLoggingSystem(stdErrLoggingSystem, null);
        this.javaUtilLoggingSystem = new StartableLoggingSystem(javaUtilLoggingSystem, null);
    }

    public DefaultLoggingManager start() {
        started = true;
        for (StandardOutputListener stdoutListener : stdoutListeners) {
            loggingOutput.addStandardOutputListener(stdoutListener);
        }
        for (StandardOutputListener stderrListener : stderrListeners) {
            loggingOutput.addStandardErrorListener(stderrListener);
        }
        for (OutputEventListener outputEventListener : outputEventListeners) {
            loggingOutput.addOutputEventListener(outputEventListener);
        }
        loggingRouter.start();

        slf4jLoggingSystem.enableCapture();
        slf4jLoggingSystem.start();

        javaUtilLoggingSystem.start();
        stdOutLoggingSystem.start();
        stdErrLoggingSystem.start();

        return this;
    }

    public DefaultLoggingManager stop() {
        try {
            CompositeStoppable.stoppable(slf4jLoggingSystem, javaUtilLoggingSystem, stdOutLoggingSystem, stdErrLoggingSystem).stop();
            for (StandardOutputListener stdoutListener : stdoutListeners) {
                loggingOutput.removeStandardOutputListener(stdoutListener);
            }
            for (StandardOutputListener stderrListener : stderrListeners) {
                loggingOutput.removeStandardErrorListener(stderrListener);
            }
            for (OutputEventListener listener : outputEventListeners) {
                loggingOutput.removeOutputEventListener(listener);
            }
            loggingRouter.stop();
        } finally {
            started = false;
        }
        return this;
    }

    public void close() {
        stop();
    }

    @Override
    public DefaultLoggingManager setLevelInternal(LogLevel logLevel) {
        slf4jLoggingSystem.setLevel(logLevel);
        javaUtilLoggingSystem.setLevel(logLevel);
        loggingRouter.setLevel(logLevel);
        return this;
    }

    public LogLevel getLevel() {
        return slf4jLoggingSystem.level;
    }

    @Override
    public DefaultLoggingManager captureSystemSources() {
        stdOutLoggingSystem.enableCapture();
        stdErrLoggingSystem.enableCapture();
        javaUtilLoggingSystem.enableCapture();
        return this;
    }

    public LogLevel getStandardOutputCaptureLevel() {
        return stdOutLoggingSystem.level;
    }

    public DefaultLoggingManager captureStandardOutput(LogLevel level) {
        stdOutLoggingSystem.setLevel(level);
        return this;
    }

    public DefaultLoggingManager captureStandardError(LogLevel level) {
        stdErrLoggingSystem.setLevel(level);
        return this;
    }

    public LogLevel getStandardErrorCaptureLevel() {
        return stdErrLoggingSystem.level;
    }

    public void addStandardOutputListener(StandardOutputListener listener) {
        if (stdoutListeners.add(listener) && started) {
            loggingOutput.addStandardOutputListener(listener);
        }
    }

    public void addStandardErrorListener(StandardOutputListener listener) {
        if (stderrListeners.add(listener) && started) {
            loggingOutput.addStandardErrorListener(listener);
        }
    }

    public void addStandardOutputListener(OutputStream outputStream) {
        addStandardOutputListener(new StreamBackedStandardOutputListener(outputStream));
    }

    public void addStandardErrorListener(OutputStream outputStream) {
        addStandardErrorListener(new StreamBackedStandardOutputListener(outputStream));
    }

    public void removeStandardOutputListener(StandardOutputListener listener) {
        if (stdoutListeners.remove(listener) && started) {
            loggingOutput.removeStandardOutputListener(listener);
        }
    }

    public void removeStandardErrorListener(StandardOutputListener listener) {
        if (stderrListeners.remove(listener) && started) {
            loggingOutput.removeStandardErrorListener(listener);
        }
    }

    public void addOutputEventListener(OutputEventListener listener) {
        if (outputEventListeners.add(listener) && started) {
            loggingOutput.addOutputEventListener(listener);
        }
    }

    public void removeOutputEventListener(OutputEventListener listener) {
        if (outputEventListeners.remove(listener) && started) {
            loggingOutput.removeOutputEventListener(listener);
        }
    }

    public void attachProcessConsole(ConsoleOutput consoleOutput) {
        loggingRouter.attachProcessConsole(consoleOutput);
    }

    @Override
    public void attachPlainConsole(StandardOutputListener outputListener, StandardOutputListener errorListener) {
        loggingRouter.attachPlainConsole(outputListener, errorListener);
    }

    public void attachAnsiConsole(OutputStream outputStream) {
        loggingRouter.attachAnsiConsole(outputStream);
    }

    public void attachSystemOutAndErr() {
        loggingOutput.attachSystemOutAndErr();
    }

    private static class StartableLoggingRouter implements Stoppable {
        private final LoggingRouter loggingRouter;
        private LogLevel level;
        private LoggingSystem.Snapshot originalState;
        private Runnable consoleAttachment;

        public StartableLoggingRouter(LoggingRouter loggingRouter) {
            this.loggingRouter = loggingRouter;
        }

        public void start() {
            originalState = loggingRouter.snapshot();
            if (level != null) {
                loggingRouter.configure(level);
            }
            if (consoleAttachment != null) {
                consoleAttachment.run();
            }
        }

        private void addConsoleAttachement(Runnable consoleAttachment) {
            if (consoleAttachment.equals(this.consoleAttachment)) {
                return;
            }
            if (this.consoleAttachment != null) {
                throw new UnsupportedOperationException("Not implemented yet.");
            }

            if (originalState != null) {
                // Already started
                consoleAttachment.run();
            }
            this.consoleAttachment = consoleAttachment;
        }

        public void attachProcessConsole(ConsoleOutput consoleOutput) {
            addConsoleAttachement(new ProcessConsoleAttachement(loggingRouter, consoleOutput));
        }

        public void attachAnsiConsole(OutputStream outputStream) {
            addConsoleAttachement(new AnsiConsoleAttachment(loggingRouter, outputStream));
        }

        public void attachPlainConsole(StandardOutputListener outputListener, StandardOutputListener errorListener) {
            addConsoleAttachement(new PlainConsoleAttachment(loggingRouter, outputListener, errorListener));
        }

        public void setLevel(LogLevel logLevel) {
            if (this.level == logLevel) {
                return;
            }

            if (originalState != null) {
                // Already started
                loggingRouter.configure(logLevel);
            }
            level = logLevel;
        }

        @Override
        public void stop() {
            try {
                if (originalState != null) {
                    loggingRouter.restore(originalState);
                }
            } finally {
                originalState = null;
            }
        }
    }

    private static class StartableLoggingSystem implements Stoppable {
        private final LoggingSourceSystem loggingSystem;
        private boolean enabled;
        private LogLevel level;
        private LoggingSystem.Snapshot originalState;

        private StartableLoggingSystem(LoggingSourceSystem loggingSystem, LogLevel level) {
            this.loggingSystem = loggingSystem;
            this.level = level;
        }

        /**
         * Start this logging system: take a snapshot of the current state and start capturing events if enabled.
         */
        public void start() {
            originalState = loggingSystem.snapshot();
            if (level != null) {
                loggingSystem.setLevel(level);
            }
            if (enabled) {
                loggingSystem.startCapture();
            }
        }

        /**
         * Start capturing events from this logging system. Does not take effect until started.
         */
        public void enableCapture() {
            if (enabled) {
                return;
            }

            enabled = true;
            if (originalState != null) {
                //started, enable
                loggingSystem.startCapture();
            }
        }

        /**
         * Sets the logging level for this log system. Does not take effect until started .
         */
        public void setLevel(LogLevel logLevel) {
            if (this.level == logLevel) {
                return;
            }

            this.level = logLevel;
            if (originalState != null) {
                // started, update the log level
                loggingSystem.setLevel(logLevel);
            }
        }

        /**
         * Stops this logging system. Restores state from when started.
         */
        public void stop() {
            try {
                if (originalState != null) {
                    loggingSystem.restore(originalState);
                }
            } finally {
                enabled = false;
                originalState = null;
            }
        }
    }

    private static class ProcessConsoleAttachement implements Runnable {
        private final LoggingRouter loggingRouter;
        private final ConsoleOutput consoleOutput;

        public ProcessConsoleAttachement(LoggingRouter loggingRouter, ConsoleOutput consoleOutput) {
            this.loggingRouter = loggingRouter;
            this.consoleOutput = consoleOutput;
        }

        @Override
        public void run() {
            loggingRouter.attachProcessConsole(consoleOutput);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ProcessConsoleAttachement that = (ProcessConsoleAttachement) o;

            return consoleOutput == that.consoleOutput;
        }

        @Override
        public int hashCode() {
            return consoleOutput.hashCode();
        }
    }

    private static class AnsiConsoleAttachment implements Runnable {
        private final LoggingRouter loggingRouter;
        private final OutputStream outputStream;

        public AnsiConsoleAttachment(LoggingRouter loggingRouter, OutputStream outputStream) {
            this.loggingRouter = loggingRouter;
            this.outputStream = outputStream;
        }

        @Override
        public void run() {
            loggingRouter.attachAnsiConsole(outputStream);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            AnsiConsoleAttachment that = (AnsiConsoleAttachment) o;

            return outputStream == that.outputStream;
        }

        @Override
        public int hashCode() {
            return outputStream.hashCode();
        }
    }

    private static class PlainConsoleAttachment implements Runnable {
        private final LoggingRouter loggingRouter;
        private final StandardOutputListener outputListener;
        private final StandardOutputListener errorListener;

        public PlainConsoleAttachment(LoggingRouter loggingRouter, StandardOutputListener outputListener, StandardOutputListener errorListener) {
            this.loggingRouter = loggingRouter;
            this.outputListener = outputListener;
            this.errorListener = errorListener;
        }

        @Override
        public void run() {
            loggingRouter.attachPlainConsole(outputListener, errorListener);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            PlainConsoleAttachment that = (PlainConsoleAttachment) o;

            if (outputListener != that.outputListener) {
                return false;
            }
            return errorListener == that.errorListener;
        }

        @Override
        public int hashCode() {
            int result = outputListener.hashCode();
            result = 31 * result + errorListener.hashCode();
            return result;
        }
    }
}
