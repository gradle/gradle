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

public class BasicProgressLoggingAwareFormatter extends AbstractProgressLoggingAwareFormatter {

    private enum State {
        StartOfLine {
            @Override
            public void addCompletion(Appendable target, String status) throws IOException {
                if (status.length() == 0) {
                    return;
                }
                super.addCompletion(target, status);
            }},
        Description {
            @Override
            public void startNewLine(Appendable target) throws IOException {
                target.append(EOL);
            }
            @Override
            public void addStatus(Appendable target) throws IOException {
                target.append(" ");
                super.addStatus(target);
            }
            @Override
            public void addCompletion(Appendable target, String status) throws IOException {
                if (status.length() > 0) {
                    target.append(' ');
                }
                super.addCompletion(target, status);
            }},
        Status {
            @Override
            public void startNewLine(Appendable target) throws IOException {
                target.append(EOL);
            }
            @Override
            public void addCompletion(Appendable target, String status) throws IOException {
                if (status.length() > 0) {
                    target.append(' ');
                }
                super.addCompletion(target, status);
            }};

        public void startNewLine(Appendable target) throws IOException {
        }

        public void addStatus(Appendable target) throws IOException {
            target.append('.');
        }

        public void addCompletion(Appendable target, String status) throws IOException {
            target.append(status);
            target.append(EOL);
        }
    }

    private State state = State.StartOfLine;
    private final Appendable target;

    public BasicProgressLoggingAwareFormatter(Context context, Appendable target) {
        super(context);
        this.target = target;
    }

    @Override
    protected void onStart(Operation operation) throws IOException {
        state.startNewLine(target);
        target.append(operation.getDescription());
        state = State.Description;
    }

    @Override
    protected void onStatusChange(Operation operation) throws IOException {
        state.addStatus(target);
        state = State.Status;
    }

    @Override
    protected void onComplete(Operation operation) throws IOException {
        state.addCompletion(target, operation.getStatus());
        state = State.StartOfLine;
    }

    @Override
    protected void onMessage(String message) throws IOException {
        state.startNewLine(target);
        target.append(message);
        state = State.StartOfLine;
    }
}
