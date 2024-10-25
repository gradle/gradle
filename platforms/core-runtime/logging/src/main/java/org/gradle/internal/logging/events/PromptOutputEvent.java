/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.logging.events;

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.time.Timestamp;

import javax.annotation.Nullable;

/**
 * Requests that the client present the given prompt to the user and return the user's response as a single line of text.
 *
 * The response is delivered to the {@link org.gradle.api.internal.tasks.userinput.UserInputReader} service.
 */
public abstract class PromptOutputEvent extends RenderableOutputEvent implements InteractiveEvent {
    public PromptOutputEvent(Timestamp timestamp) {
        super(timestamp, "prompt", LogLevel.QUIET, null);
    }

    @Override
    public void render(StyledTextOutput output) {
        // Add a newline at the start of each question
        output.println();
        output.text(getPrompt());
    }

    /**
     * Converts the given text into the response object, or returns a new prompt to display to the user.
     */
    public abstract PromptResult<?> convert(String text);

    public static class PromptResult<T> {
        public final T response;
        public final String newPrompt;

        private PromptResult(@Nullable T response, @Nullable String newPrompt) {
            this.response = response;
            this.newPrompt = newPrompt;
        }

        public static <T> PromptResult<T> response(T response) {
            return new PromptResult<T>(response, null);
        }

        public static <T> PromptResult<T> newPrompt(String newPrompt) {
            return new PromptResult<T>(null, newPrompt);
        }
    }

    public abstract String getPrompt();

    @Override
    public String toString() {
        return "[" + getLogLevel() + "] [" + getCategory() + "] '" + getPrompt() + "'";
    }

    @Override
    public RenderableOutputEvent withBuildOperationId(OperationIdentifier buildOperationId) {
        throw new UnsupportedOperationException();
    }
}
