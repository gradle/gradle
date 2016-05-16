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
import org.gradle.api.logging.LoggingManager;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.LoggingOutputInternal;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.config.LoggingSourceSystem;
import org.gradle.internal.logging.config.LoggingSystem;
import org.gradle.internal.logging.text.StreamBackedStandardOutputListener;
import org.gradle.util.SingleMessageLogger;

import java.io.Closeable;
import java.io.OutputStream;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultLoggingManager implements LoggingManagerInternal, Closeable {
    private boolean started;
    private final StartableLoggingSystem loggingSystem;
    private final StartableLoggingSystem stdOutLoggingSystem;
    private final StartableLoggingSystem stdErrLoggingSystem;
    private final StartableLoggingSystem javaUtilLoggingSystem;
    private final LoggingOutputInternal loggingOutput;
    private final Set<StandardOutputListener> stdoutListeners = new LinkedHashSet<StandardOutputListener>();
    private final Set<StandardOutputListener> stderrListeners = new LinkedHashSet<StandardOutputListener>();
    private final Set<OutputEventListener> outputEventListeners = new LinkedHashSet<OutputEventListener>();
    private boolean hasConsole;

    public DefaultLoggingManager(LoggingSourceSystem loggingSystem, LoggingSourceSystem javaUtilLoggingSystem, LoggingSourceSystem stdOutLoggingSystem,
                                 LoggingSourceSystem stdErrLoggingSystem, LoggingOutputInternal loggingOutput) {
        this.loggingOutput = loggingOutput;
        this.loggingSystem = new StartableLoggingSystem(loggingSystem, null);
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
        loggingSystem.start();
        javaUtilLoggingSystem.start();
        stdOutLoggingSystem.start();
        stdErrLoggingSystem.start();

        return this;
    }

    public DefaultLoggingManager stop() {
        try {
            CompositeStoppable.stoppable(loggingSystem, javaUtilLoggingSystem, stdOutLoggingSystem, stdErrLoggingSystem).stop();
            for (StandardOutputListener stdoutListener : stdoutListeners) {
                loggingOutput.removeStandardOutputListener(stdoutListener);
            }
            for (StandardOutputListener stderrListener : stderrListeners) {
                loggingOutput.removeStandardErrorListener(stderrListener);
            }
            for (OutputEventListener listener : outputEventListeners) {
                loggingOutput.removeOutputEventListener(listener);
            }
            if (hasConsole) {
                loggingOutput.flush();
            }
        } finally {
            started = false;
        }
        return this;
    }

    public void close() {
        stop();
    }

    @Override
    public LoggingManager setLevel(LogLevel logLevel) {
        SingleMessageLogger.nagUserOfDeprecated("LoggingManager.setLevel(LogLevel)", "If you are using this method to expose Ant logging messages, please use AntBuilder.setLifecycleLogLevel() instead");
        return setLevelInternal(logLevel);
    }

    @Override
    public DefaultLoggingManager setLevelInternal(LogLevel logLevel) {
        loggingSystem.setLevel(logLevel);
        return this;
    }

    public LogLevel getLevel() {
        return loggingSystem.level;
    }

    @Override
    public DefaultLoggingManager captureSystemSources() {
        stdOutLoggingSystem.setLevel(LogLevel.QUIET);
        stdErrLoggingSystem.setLevel(LogLevel.ERROR);
        javaUtilLoggingSystem.setLevel(LogLevel.DEBUG);
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
        hasConsole = true;
        loggingOutput.attachProcessConsole(consoleOutput);
    }

    public void attachAnsiConsole(OutputStream outputStream) {
        hasConsole = true;
        loggingOutput.attachAnsiConsole(outputStream);
    }

    @Override
    public void flush() {
        throw new UnsupportedOperationException();
    }

    public void attachSystemOutAndErr() {
        loggingOutput.attachSystemOutAndErr();
    }

    private static class StartableLoggingSystem implements Stoppable {
        private final LoggingSourceSystem loggingSystem;
        private LogLevel level;
        private LoggingSystem.Snapshot originalState;

        private StartableLoggingSystem(LoggingSourceSystem loggingSystem, LogLevel level) {
            this.loggingSystem = loggingSystem;
            this.level = level;
        }

        public void start() {
            if (level != null) {
                originalState = loggingSystem.on(level, level);
            } else {
                originalState = loggingSystem.snapshot();
            }
        }

        public void setLevel(LogLevel logLevel) {
            if (this.level == logLevel) {
                return;
            }

            this.level = logLevel;
            if (originalState == null) {
                // Not started, don't apply the changes
                return;
            }
            loggingSystem.on(logLevel, logLevel);
        }

        public void stop() {
            try {
                if (originalState != null) {
                    loggingSystem.restore(originalState);
                }
            } finally {
                originalState = null;
            }
        }
    }
}
