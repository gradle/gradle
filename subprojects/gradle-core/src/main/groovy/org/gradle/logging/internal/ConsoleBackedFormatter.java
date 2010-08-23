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

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

public class ConsoleBackedFormatter extends AbstractProgressLoggingAwareFormatter {
    private final Console console;
    private final Set<Operation> currentOperations = new LinkedHashSet<Operation>();
    private final Set<Operation> noHeader = new LinkedHashSet<Operation>();
    private final Label statusBar;

    public ConsoleBackedFormatter(Console console) {
        this.console = console;
        statusBar = console.addStatusBar();
    }

    @Override
    protected void onStart(Operation operation) throws IOException {
        writeHeaders();
        currentOperations.add(operation);
        noHeader.add(operation);
        updateText();
    }

    @Override
    protected void onComplete(Operation operation) throws IOException {
        currentOperations.remove(operation);
        boolean hasCompletionStatus = operation.getStatus().length() > 0;
        boolean hasDescription = operation.getDescription().length() > 0;
        if (noHeader.remove(operation) || hasCompletionStatus) {
            StringBuilder builder = new StringBuilder();
            if (hasDescription) {
                builder.append(operation.getDescription());
            }
            if (hasCompletionStatus) {
                if (hasDescription) {
                    builder.append(' ');
                }
                builder.append(operation.getStatus());
            }
            if (builder.length() > 0) {
                builder.append(EOL);
                console.getMainArea().append(builder.toString());
            }
        }
        updateText();
    }

    @Override
    protected void onStatusChange(Operation operation) throws IOException {
        updateText();
    }

    @Override
    protected void onInfoMessage(String message) throws IOException {
        writeHeaders();
        console.getMainArea().append(message);
    }

    @Override
    protected void onErrorMessage(String message) throws IOException {
        onInfoMessage(message);
    }

    private void updateText() {
        StringBuilder builder = new StringBuilder();
        for (Operation operation : currentOperations) {
            if (operation.getStatus().length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append("> ");
            builder.append(operation.getStatus());
        }
        statusBar.setText(builder.toString());
    }

    private void writeHeaders() {
        for (Operation operation : noHeader) {
            if (operation.getDescription().length() > 0) {
                console.getMainArea().append(operation.getDescription() + EOL);
            }
        }
        noHeader.clear();
    }
}
