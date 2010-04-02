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
import org.gradle.api.logging.StandardOutputListener;

import java.io.IOException;

public class BasicProgressLoggingAwareFormatter extends AbstractProgressLoggingAwareFormatter {

    private enum State {
        StartOfLine {
            @Override
            public void startNewLine(StandardOutputListener target) throws IOException {
            }
            @Override
            public void addCompletion(StandardOutputListener target, String status) throws IOException {
                if (status.length() == 0) {
                    return;
                }
                target.onOutput(status);
                target.onOutput(EOL);
            }},
        Description {
            @Override
            public void startNewLine(StandardOutputListener target) throws IOException {
                target.onOutput(EOL);
            }
            @Override
            public void addStatus(StandardOutputListener target) throws IOException {
                target.onOutput(" ");
                super.addStatus(target);
            }
            @Override
            public void addCompletion(StandardOutputListener target, String status) throws IOException {
                if (status.length() > 0) {
                    target.onOutput(" ");
                    target.onOutput(status);
                }
                target.onOutput(EOL);
            }},
        Status {
            @Override
            public void startNewLine(StandardOutputListener target) throws IOException {
                target.onOutput(EOL);
            }
            @Override
            public void addCompletion(StandardOutputListener target, String status) throws IOException {
                if (status.length() > 0) {
                    target.onOutput(" ");
                    target.onOutput(status);
                }
                target.onOutput(EOL);
            }};

        public abstract void startNewLine(StandardOutputListener target) throws IOException;

        public void addStatus(StandardOutputListener target) throws IOException {
            target.onOutput(".");
        }

        public abstract void addCompletion(StandardOutputListener target, String status) throws IOException;
    }

    private State state = State.StartOfLine;
    private final StandardOutputListener infoTarget;
    private final StandardOutputListener errorTarget;

    public BasicProgressLoggingAwareFormatter(Context context, StandardOutputListener infoTarget, StandardOutputListener errorTarget) {
        super(context);
        this.infoTarget = infoTarget;
        this.errorTarget = errorTarget;
    }

    @Override
    protected void onStart(Operation operation) throws IOException {
        state.startNewLine(infoTarget);
        infoTarget.onOutput(operation.getDescription());
        state = State.Description;
    }

    @Override
    protected void onStatusChange(Operation operation) throws IOException {
        state.addStatus(infoTarget);
        state = State.Status;
    }

    @Override
    protected void onComplete(Operation operation) throws IOException {
        state.addCompletion(infoTarget, operation.getStatus());
        state = State.StartOfLine;
    }

    @Override
    protected void onInfoMessage(String message) throws IOException {
        state.startNewLine(infoTarget);
        infoTarget.onOutput(message);
        state = State.StartOfLine;
    }

    @Override
    protected void onErrorMessage(String message) throws IOException {
        state.startNewLine(infoTarget);
        errorTarget.onOutput(message);
        state = State.StartOfLine;
    }
}
