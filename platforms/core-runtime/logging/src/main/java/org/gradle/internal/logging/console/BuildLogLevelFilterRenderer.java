/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.logging.console;

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.events.LogLevelChangeEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;

public class BuildLogLevelFilterRenderer implements OutputEventListener {
    private final OutputEventListener listener;
    private LogLevel logLevel = LogLevel.LIFECYCLE;

    public BuildLogLevelFilterRenderer(OutputEventListener listener) {
        this.listener = listener;
    }

    @Override
    public void onOutput(OutputEvent event) {
        if (event.getLogLevel() != null && event.getLogLevel().compareTo(logLevel) < 0) {
            return;
        }
        if (event instanceof LogLevelChangeEvent) {
            LogLevelChangeEvent changeEvent = (LogLevelChangeEvent) event;
            logLevel = changeEvent.getNewLogLevel();
        }
        listener.onOutput(event);
    }
}
