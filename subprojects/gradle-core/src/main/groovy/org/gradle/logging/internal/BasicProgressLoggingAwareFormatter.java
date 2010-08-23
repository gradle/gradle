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

import org.gradle.api.logging.StandardOutputListener;

import java.io.IOException;

public class BasicProgressLoggingAwareFormatter extends AbstractProgressLoggingAwareFormatter {

    private enum State {
        StartOfLine {
            @Override
            public State startNewLine(StandardOutputListener target) throws IOException {
                return this;
            }
            @Override
            public State addCompletion(StandardOutputListener target, String status) throws IOException {
                if (status.length() == 0) {
                    return this;
                }
                target.onOutput(status);
                target.onOutput(EOL);
                return this;
            }},
        Description {
            @Override
            public State startNewLine(StandardOutputListener target) throws IOException {
                target.onOutput(EOL);
                return StartOfLine;
            }
            @Override
            public State addCompletion(StandardOutputListener target, String status) throws IOException {
                if (status.length() > 0) {
                    target.onOutput(" ");
                    target.onOutput(status);
                }
                target.onOutput(EOL);
                return StartOfLine;
            }};

        public abstract State startNewLine(StandardOutputListener target) throws IOException;

        public abstract State addCompletion(StandardOutputListener target, String status) throws IOException;
    }

    private State state = State.StartOfLine;
    private final StandardOutputListener infoTarget;
    private final StandardOutputListener errorTarget;

    public BasicProgressLoggingAwareFormatter(StandardOutputListener infoTarget, StandardOutputListener errorTarget) {
        this.infoTarget = infoTarget;
        this.errorTarget = errorTarget;
    }

    @Override
    protected void onStart(Operation operation) throws IOException {
        if (operation.getDescription().length() > 0) {
            state.startNewLine(infoTarget);
            infoTarget.onOutput(operation.getDescription());
            state = State.Description;
        }
    }

    @Override
    protected void onStatusChange(Operation operation) throws IOException {
    }

    @Override
    protected void onComplete(Operation operation) throws IOException {
        state = state.addCompletion(infoTarget, operation.getStatus());
    }

    @Override
    protected void onInfoMessage(String message) throws IOException {
        state = state.startNewLine(infoTarget);
        infoTarget.onOutput(message);
    }

    @Override
    protected void onErrorMessage(String message) throws IOException {
        state = state.startNewLine(infoTarget);
        errorTarget.onOutput(message);
    }
}
