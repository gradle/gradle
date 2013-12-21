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

package org.gradle.logging.internal;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.logging.LoggingManagerInternal;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultLoggingManager implements LoggingManagerInternal {
    private boolean started;
    private final StartableLoggingSystem loggingSystem;
    private final StartableLoggingSystem stdOutLoggingSystem;
    private final StartableLoggingSystem stdErrLoggingSystem;
    private final LoggingOutputInternal loggingOutput;
    private final Set<StandardOutputListener> stdoutListeners = new LinkedHashSet<StandardOutputListener>();
    private final Set<StandardOutputListener> stderrListeners = new LinkedHashSet<StandardOutputListener>();
    private final Set<OutputEventListener> outputEventListeners = new LinkedHashSet<OutputEventListener>();

    public DefaultLoggingManager(LoggingSystem loggingSystem, LoggingSystem stdOutLoggingSystem,
                                 LoggingSystem stdErrLoggingSystem, LoggingOutputInternal loggingOutput) {
        this.loggingOutput = loggingOutput;
        this.loggingSystem = new StartableLoggingSystem(loggingSystem, null);
        this.stdOutLoggingSystem = new StartableLoggingSystem(stdOutLoggingSystem, LogLevel.QUIET);
        this.stdErrLoggingSystem = new StartableLoggingSystem(stdErrLoggingSystem, LogLevel.ERROR);
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
        stdOutLoggingSystem.start();
        stdErrLoggingSystem.start();

        return this;
    }

    public DefaultLoggingManager stop() {
        try {
            CompositeStoppable.stoppable(loggingSystem, stdOutLoggingSystem, stdErrLoggingSystem).stop();
            for (StandardOutputListener stdoutListener : stdoutListeners) {
                loggingOutput.removeStandardOutputListener(stdoutListener);
            }
            for (StandardOutputListener stderrListener : stderrListeners) {
                loggingOutput.removeStandardErrorListener(stderrListener);
            }
            for (OutputEventListener listener : outputEventListeners) {
                loggingOutput.removeOutputEventListener(listener);
            }
        } finally {
            started = false;
        }
        return this;
    }

    public DefaultLoggingManager setLevel(LogLevel logLevel) {
        loggingSystem.setLevel(logLevel);
        return this;
    }

    public LogLevel getLevel() {
        return loggingSystem.level;
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

    public void attachConsole(boolean colorOutput) {
        loggingOutput.attachConsole(colorOutput);
    }

    public void addStandardOutputAndError() {
        loggingOutput.addStandardOutputAndError();
    }

    private static class StartableLoggingSystem implements Stoppable {
        private final LoggingSystem loggingSystem;
        private LogLevel level;
        private boolean disable;
        private LoggingSystem.Snapshot originalState;

        private StartableLoggingSystem(LoggingSystem loggingSystem, LogLevel level) {
            this.loggingSystem = loggingSystem;
            this.level = level;
        }

        public void start() {
            if (disable) {
                originalState = loggingSystem.off();
            } else if (level != null) {
                originalState = loggingSystem.on(level);
            } else {
                originalState = loggingSystem.snapshot();
            }
        }

        public void setLevel(LogLevel logLevel) {
            if (this.level == logLevel) {
                return;
            }

            this.level = logLevel;
            disable = false;

            if (originalState == null) {
                return;
            }
            loggingSystem.on(logLevel);
        }

        public void disable() {
            level = null;
            disable = true;
            if (originalState != null) {
                loggingSystem.off();
            }
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
