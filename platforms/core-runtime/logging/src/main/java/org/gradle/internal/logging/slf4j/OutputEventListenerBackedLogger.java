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

package org.gradle.internal.logging.slf4j;

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.time.Clock;

public class OutputEventListenerBackedLogger extends BuildOperationAwareLogger {

    private final String name;
    private final OutputEventListenerBackedLoggerContext context;
    private final Clock clock;

    public OutputEventListenerBackedLogger(String name, OutputEventListenerBackedLoggerContext context, Clock clock) {
        this.name = name;
        this.context = context;
        this.clock = clock;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    boolean isLevelAtMost(LogLevel levelLimit) {
        return levelLimit.compareTo(context.getLevel()) >= 0;
    }

    @Override
    void log(LogLevel logLevel, Throwable throwable, String message, OperationIdentifier operationIdentifier) {
        LogEvent logEvent = new LogEvent(clock.getTimestamp(), name, logLevel, message, throwable, operationIdentifier);
        OutputEventListener outputEventListener = context.getOutputEventListener();
        try {
            outputEventListener.onOutput(logEvent);
        } catch (Throwable e) {
            // fall back to standard out
            e.printStackTrace(System.out);
        }
    }
}
