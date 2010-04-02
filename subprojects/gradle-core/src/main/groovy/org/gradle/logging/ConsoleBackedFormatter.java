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

import ch.qos.logback.core.Context;

import java.io.IOException;
import java.util.*;

public class ConsoleBackedFormatter extends AbstractProgressLoggingAwareFormatter {
    private final Console console;
    private Map<Operation, Label> currentOperations = new HashMap<Operation, Label>();
    private Set<Operation> noHeader = new LinkedHashSet<Operation>();

    public ConsoleBackedFormatter(Context context, Console console) {
        super(context);
        this.console = console;
    }

    @Override
    protected void onStart(Operation operation) throws IOException {
        writeHeaders();
        Label label = console.addStatusBar();
        label.setText(operation.getDescription());
        currentOperations.put(operation, label);
        noHeader.add(operation);
    }

    @Override
    protected void onComplete(Operation operation) throws IOException {
        Label label = currentOperations.remove(operation);
        label.close();
        boolean hasCompletionStatus = operation.getStatus().length() > 0;
        if (noHeader.remove(operation) || hasCompletionStatus) {
            String status = hasCompletionStatus ? " " + operation.getStatus() : "";
            console.getMainArea().append(operation.getDescription() + status + EOL);
        }
    }

    @Override
    protected void onStatusChange(Operation operation) throws IOException {
        Label label = currentOperations.get(operation);
        label.setText(operation.getDescription() + ' ' + operation.getStatus());
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

    private void writeHeaders() {
        for (Operation operation : noHeader) {
            console.getMainArea().append(operation.getDescription() + EOL);
        }
        noHeader.clear();
    }
}
