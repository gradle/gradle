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

import org.gradle.util.GUtil;

import java.util.LinkedList;

public class ConsoleBackedProgressRenderer implements OutputEventListener {
    private final OutputEventListener listener;
    private final Console console;
    private final LinkedList<Operation> operations = new LinkedList<Operation>();
    private Label statusBar;

    public ConsoleBackedProgressRenderer(OutputEventListener listener, Console console) {
        this.listener = listener;
        this.console = console;
    }

    public void onOutput(OutputEvent event) {
        if (event instanceof ProgressStartEvent) {
            ProgressStartEvent startEvent = (ProgressStartEvent) event;
            operations.addLast(new Operation(startEvent.getShortDescription(), startEvent.getStatus()));
            updateText();
        } else if (event instanceof ProgressCompleteEvent) {
            operations.removeLast();
            updateText();
        } else if (event instanceof ProgressEvent) {
            ProgressEvent progressEvent = (ProgressEvent) event;
            operations.getLast().status = progressEvent.getStatus();
            updateText();
        }
        listener.onOutput(event);
    }

    private void updateText() {
        StringBuilder builder = new StringBuilder();
        for (Operation operation : operations) {
            String message = operation.getMessage();
            if (message == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append("> ");
            builder.append(message);
        }
        if (statusBar == null) {
            statusBar = console.getStatusBar();
        }
        statusBar.setText(builder.toString());
    }

    private static class Operation {
        private final String shortDescription;
        private String status;

        private Operation(String shortDescription, String status) {
            this.shortDescription = shortDescription;
            this.status = status;
        }

        String getMessage() {
            if (GUtil.isTrue(status)) {
                return status;
            }
            if (GUtil.isTrue(shortDescription)) {
                return shortDescription;
            }
            return null;
        }
    }

}
