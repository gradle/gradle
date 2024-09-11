/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.userinput;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Receives interactive responses from the user.
 */
@ServiceScope(Scope.Global.class)
public interface UserInputReader {
    /**
     * Called when the current process starts receiving user input from the client.
     */
    void startInput();

    void putInput(UserInput input);

    /**
     * Returns a {@link TextResponse} containing text supplied by the user, or {@link #END_OF_INPUT} if interrupted.
     */
    UserInput readInput();

    abstract class UserInput {
        abstract String getText();
    }

    UserInput END_OF_INPUT = new UserInput() {
        @Override
        String getText() {
            throw new IllegalStateException("No response available.");
        }
    };

    class TextResponse extends UserInput {
        private final String text;

        public TextResponse(String text) {
            this.text = text;
        }

        @Override
        public String getText() {
            return text;
        }
    }
}
