/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.watch.registry;

import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;

public enum WatchMode {
    ENABLED(true, "enabled") {
        @Override
        public Logger loggerForWarnings(Logger currentLogger) {
            return currentLogger;
        }
    },
    DEFAULT(true, "enabled if available") {
        @Override
        public Logger loggerForWarnings(Logger currentLogger) {
            return currentLogger.isInfoEnabled() ? currentLogger : NOPLogger.NOP_LOGGER;
        }
    },
    DISABLED(false, "disabled") {
        @Override
        public Logger loggerForWarnings(Logger currentLogger) {
            return NOPLogger.NOP_LOGGER;
        }
    };

    private final boolean enabled;
    private final String description;

    WatchMode(boolean enabled, String description) {
        this.enabled = enabled;
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public abstract Logger loggerForWarnings(Logger currentLogger);

    public String getDescription() {
        return description;
    }
}
