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

package org.gradle.api.internal.tasks.userinput;

import org.gradle.internal.scan.UsedByScanPlugin;

import javax.annotation.Nullable;
import java.util.Collection;

public interface UserInputHandler {
    /**
     * Prompts the user with a yes/no question and returns the answer.
     *
     * @param question The text of the question.
     * @return the answer or {@code null} if not connected to a user console.
     */
    @Nullable
    @UsedByScanPlugin
    Boolean askYesNoQuestion(String question);

    /**
     * Prompts the user with a yes/no question and returns the answer.
     *
     * @param question The text of the question.
     * @return the answer or the given default if not connected to a user console.
     */
    boolean askYesNoQuestion(String question, boolean defaultValue);

    /**
     * Prompts the user to select an option from the given list and returns the answer.
     * Uses the {@link Object#toString()} representation of the options to format the prompt.
     *
     * @param question The text of the question.
     * @return the answer or the given default if not connected to a user console.
     */
    <T> T selectOption(String question, Collection<T> options, T defaultOption);

    /**
     * Prompts the user to provide a string value.
     * @param question The text of the question.
     * @return The answer or the given default if not connected to a user console.
     */
    String askQuestion(String question, String defaultValue);
}
