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

import org.gradle.api.UncheckedIOException;
import org.gradle.api.logging.LogLevel;
import org.gradle.logging.internal.*;

import java.io.IOException;
import java.util.LinkedList;

public abstract class AbstractProgressLoggingAwareFormatter implements OutputEventListener {
    public static final String EOL = System.getProperty("line.separator");
    private final LinkedList<Operation> pendingOperations = new LinkedList<Operation>();
    private boolean debugOutput;

    public void onOutput(OutputEvent event) {
        try {
            if (event instanceof ProgressStartEvent) {
                ProgressStartEvent progressStartEvent = (ProgressStartEvent) event;
                Operation operation = new Operation();
                operation.description = progressStartEvent.getDescription();
                operation.status = "";
                pendingOperations.addFirst(operation);
                onStart(operation);
            } else if (event instanceof ProgressEvent) {
                assert !pendingOperations.isEmpty();
                ProgressEvent progressEvent = (ProgressEvent) event;
                Operation operation = pendingOperations.getFirst();
                operation.status = progressEvent.getStatus();
                onStatusChange(operation);
            } else if (event instanceof ProgressCompleteEvent) {
                assert !pendingOperations.isEmpty();
                ProgressCompleteEvent progressCompleteEvent = (ProgressCompleteEvent) event;
                Operation operation = pendingOperations.removeFirst();
                operation.status = progressCompleteEvent.getStatus();
                onComplete(operation);
            } else if (event instanceof LogLevelChangeEvent) {
                debugOutput = ((LogLevelChangeEvent) event).getNewLogLevel() == LogLevel.DEBUG;
            } else {
                String message = doLayout((LogEvent) event);
                if (event.getLogLevel() == LogLevel.ERROR) {
                    onErrorMessage(message);
                } else {
                    onInfoMessage(message);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String doLayout(LogEvent event) {
        OutputEventTextOutput writer = new StringWriterBackedOutputEventTextOutput();
        if (debugOutput) {
            writer.text("[");
            writer.text(event.getLogLevel().toString());
            writer.text("] [");
            writer.text(event.getCategory());
            writer.text("] ");
        }
        event.render(writer);
        return writer.toString();
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
