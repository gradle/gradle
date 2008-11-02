/*
 * Copyright 2007-2008 the original author or authors.
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
public class DefaultStandardOutputCapture implements StandardOutputCapture {
    private boolean enabled;

    private LogLevel level;

    private StandardOutputState globalState;

    /**
     * Creates and instance with enabled set to false and LogLevel set to null.
     */
    public DefaultStandardOutputCapture() {
        this.enabled = false;
        level = null;
    }

    public DefaultStandardOutputCapture(boolean enabled, LogLevel level) {
        if (level == null) {
            throw new InvalidUserDataException("Log level must not be null");
        }
        this.level = level;
        this.enabled = enabled;
    }

    /**
     * @see StandardOutputCapture#start()
     */
    public DefaultStandardOutputCapture start() {
        globalState = StandardOutputLogging.getStateSnapshot();
        if (enabled) {
            StandardOutputLogging.on(level);
        } else {
            StandardOutputLogging.off();
        }
        return this;
    }

    /**
     * @see org.gradle.api.logging.StandardOutputCapture#stop() ()
     */
    public DefaultStandardOutputCapture stop() {
        StandardOutputLogging.flush();
        StandardOutputLogging.restoreState(globalState);
        return this;
    }

    /**
     * @see org.gradle.api.logging.StandardOutputCapture#isEnabled() ()
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @see org.gradle.api.logging.StandardOutputCapture#getLevel() ()
     */
    public LogLevel getLevel() {
        return level;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultStandardOutputCapture that = (DefaultStandardOutputCapture) o;

        if (enabled != that.enabled) return false;
        if (level != that.level) return false;

        return true;
    }

    public int hashCode() {
        int result;
        result = (enabled ? 1 : 0);
        result = 31 * result + (level != null ? level.hashCode() : 0);
        result = 31 * result + (globalState != null ? globalState.hashCode() : 0);
        return result;
    }
}
