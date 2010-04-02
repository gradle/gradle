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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Context;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.logging.Logging;

import java.io.IOException;
import java.util.LinkedList;

public abstract class AbstractProgressLoggingAwareFormatter implements LogEventFormatter {
    public static final String EOL = System.getProperty("line.separator");
    private final PatternLayout layout;
    private final LinkedList<Operation> pendingOperations = new LinkedList<Operation>();

    protected AbstractProgressLoggingAwareFormatter(Context context) {
        this.layout = new PatternLayout();
        layout.setContext(context);
        layout.setPattern("%msg%n%ex");
        layout.start();
    }

    public void format(ILoggingEvent event) {
        try {
            if (event.getMarker() == Logging.PROGRESS_STARTED) {
                Operation operation = new Operation();
                operation.description = event.getFormattedMessage();
                pendingOperations.addFirst(operation);
                onStart(operation);
            } else if (event.getMarker() == Logging.PROGRESS) {
                assert !pendingOperations.isEmpty();
                Operation operation = pendingOperations.getFirst();
                operation.status = event.getFormattedMessage();
                onStatusChange(operation);
            } else if (event.getMarker() == Logging.PROGRESS_COMPLETE) {
                assert !pendingOperations.isEmpty();
                Operation operation = pendingOperations.removeFirst();
                operation.status = event.getFormattedMessage();
                onComplete(operation);
            } else if (event.getLevel() == Level.ERROR) {
                String message = layout.doLayout(event);
                onErrorMessage(message);
            } else {
                String message = layout.doLayout(event);
                onInfoMessage(message);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected abstract void onStart(Operation operation) throws IOException;

    protected abstract void onStatusChange(Operation operation) throws IOException;

    protected abstract void onComplete(Operation operation) throws IOException;

    protected abstract void onInfoMessage(String message) throws IOException;

    protected abstract void onErrorMessage(String message) throws IOException;

    protected class Operation {
        private String description;
        private String status;

        public String getDescription() {
            return description;
        }

        public String getStatus() {
            return status;
        }
    }
}
