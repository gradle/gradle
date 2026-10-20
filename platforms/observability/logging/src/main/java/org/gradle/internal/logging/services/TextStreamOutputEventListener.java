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
import org.gradle.internal.logging.events.LogLevelChangeEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.StyledTextOutputEvent;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link OutputEventListener} implementation which assigns log levels to text output
 * events that have no associated log level. This implementation is thread-safe.
 */
public class TextStreamOutputEventListener implements OutputEventListener {
    private final OutputEventListener listener;
    private AtomicReference<LogLevel> logLevel = new AtomicReference<LogLevel>(LogLevel.LIFECYCLE);

    public TextStreamOutputEventListener(OutputEventListener listener) {
        this.listener = listener;
    }

    @Override
    public void onOutput(OutputEvent event) {
        if (event instanceof StyledTextOutputEvent) {
            onTextEvent((StyledTextOutputEvent) event);
        } else if (event instanceof LogLevelChangeEvent) {
            onLogLevelChange((LogLevelChangeEvent) event);
        } else {
            throw new IllegalArgumentException();
        }
    }

    private void onLogLevelChange(LogLevelChangeEvent changeEvent) {
        logLevel.set(changeEvent.getNewLogLevel());
    }

    private void onTextEvent(StyledTextOutputEvent textOutputEvent) {
        if (textOutputEvent.getLogLevel() != null) {
            listener.onOutput(textOutputEvent);
        } else {
            listener.onOutput(textOutputEvent.withLogLevel(logLevel.get()));
        }
    }
}
