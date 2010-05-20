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

package org.gradle.logging;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.LoggingManager;
import org.gradle.api.logging.LoggingOutput;
import org.gradle.api.logging.StandardOutputListener;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultLoggingManager implements LoggingManager {
    private LogLevel stdOutCaptureLevel;
    private LogLevel level;
    private LoggingSystem.Snapshot originalStdOutState;
    private LoggingSystem.Snapshot originalLoggingState;
    private boolean started;
    private final LoggingSystem loggingSystem;
    private final LoggingSystem stdOutLoggingSystem;
    private final LoggingOutput loggingOutput;
    private final Set<StandardOutputListener> stdoutListeners = new LinkedHashSet<StandardOutputListener>();
    private final Set<StandardOutputListener> stderrListeners = new LinkedHashSet<StandardOutputListener>();

    public DefaultLoggingManager(LoggingSystem loggingSystem, LoggingSystem stdOutLoggingSystem, LoggingOutput loggingOutput) {
        this.loggingSystem = loggingSystem;
        this.stdOutLoggingSystem = stdOutLoggingSystem;
        this.loggingOutput = loggingOutput;
        stdOutCaptureLevel = LogLevel.QUIET;
    }

    public DefaultLoggingManager start() {
        started = true;
        for (StandardOutputListener stdoutListener : stdoutListeners) {
            loggingOutput.addStandardOutputListener(stdoutListener);
        }
        for (StandardOutputListener stderrListener : stderrListeners) {
            loggingOutput.addStandardErrorListener(stderrListener);
        }
        if (level != null) {
            originalLoggingState = loggingSystem.on(level);
        } else {
            originalLoggingState = loggingSystem.snapshot();
        }
        if (stdOutCaptureLevel != null) {
            originalStdOutState = stdOutLoggingSystem.on(stdOutCaptureLevel);
        } else {
            originalStdOutState = stdOutLoggingSystem.off();
        }

        return this;
    }

    public DefaultLoggingManager stop() {
        try {
            if (originalStdOutState != null) {
                stdOutLoggingSystem.restore(originalStdOutState);
            }
            if (originalLoggingState != null) {
                loggingSystem.restore(originalLoggingState);
            }
            for (StandardOutputListener stdoutListener : stdoutListeners) {
                loggingOutput.removeStandardOutputListener(stdoutListener);
            }
            for (StandardOutputListener stderrListener : stderrListeners) {
                loggingOutput.removeStandardErrorListener(stderrListener);
            }
        } finally {
            originalStdOutState = null;
            originalLoggingState = null;
            started = false;
        }
        return this;
    }

    public DefaultLoggingManager setLevel(LogLevel logLevel) {
        if (this.level != logLevel) {
            this.level = logLevel;
            if (started) {
                loggingSystem.on(logLevel);
            }
        }
        return this;
    }

    public LogLevel getLevel() {
        return level;
    }

    public LogLevel getStandardOutputCaptureLevel() {
        return stdOutCaptureLevel;
    }

    public boolean isStandardOutputCaptureEnabled() {
        return stdOutCaptureLevel != null;
    }

    public DefaultLoggingManager captureStandardOutput(LogLevel level) {
        if (this.stdOutCaptureLevel != level) {
            this.stdOutCaptureLevel = level;
            if (started) {
                stdOutLoggingSystem.on(level);
            }
        }
        return this;
    }

    public DefaultLoggingManager disableStandardOutputCapture() {
        if (stdOutCaptureLevel != null) {
            stdOutCaptureLevel = null;
            if (started) {
                stdOutLoggingSystem.off();
            }
        }
        return this;
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
}
