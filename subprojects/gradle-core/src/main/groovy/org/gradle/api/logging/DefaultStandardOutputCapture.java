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

package org.gradle.api.logging;

import org.gradle.api.InvalidUserDataException;

/**
 * @author Hans Dockter
 */
public class DefaultStandardOutputCapture implements StandardOutputCapture, LoggingManager {
    private boolean enabled;

    private LogLevel level;

    private StandardOutputState globalState;

    /**
     * Creates and instance with enabled set to false and LogLevel set to null.
     */
    public DefaultStandardOutputCapture() {
        this.enabled = true;
        level = LogLevel.QUIET;
    }

    public DefaultStandardOutputCapture(boolean enabled, LogLevel level) {
        if (level == null) {
            throw new InvalidUserDataException("Log level must not be null");
        }
        this.level = level;
        this.enabled = enabled;
    }

    public DefaultStandardOutputCapture start() {
        globalState = StandardOutputLogging.getStateSnapshot();
        if (enabled) {
            StandardOutputLogging.on(level);
        } else {
            StandardOutputLogging.off();
        }
        return this;
    }

    public DefaultStandardOutputCapture stop() {
        try {
            StandardOutputLogging.flush();
            StandardOutputLogging.restoreState(globalState);
        } finally {
            globalState = null;
        }
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public LogLevel getLevel() {
        return level;
    }

    public DefaultStandardOutputCapture captureStandardOutput(LogLevel level) {
        if (enabled && this.level == level) {
            return this;
        }
        enabled = true;
        this.level = level;
        if (globalState != null) {
            StandardOutputLogging.flush();
            StandardOutputLogging.on(level);
        }
        return this;
    }

    public DefaultStandardOutputCapture disableStandardOutputCapture() {
        if (!enabled) {
            return this;
        }
        enabled = false;
        level = null;
        if (globalState != null) {
            StandardOutputLogging.flush();
            StandardOutputLogging.off();
        }
        return this;
    }
}
