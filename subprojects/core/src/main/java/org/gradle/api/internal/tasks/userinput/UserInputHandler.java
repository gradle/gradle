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
     * @return the answer or {@code null} if not connected to a console.
     */
    @Nullable
    @UsedByScanPlugin
    Boolean askYesNoQuestion(String question);

    /**
     * Prompts the user with a yes/no question and returns the answer.
     *
     * @param question The text of the question.
     * @param defaultValue The option to present to the user as the default choice, and the value to use when not connected to a console
     * @return the answer or the given default if not connected to a console.
     */
    boolean askYesNoQuestion(String question, boolean defaultValue);

    /**
     * Prompts the user to select an option from the given list and returns the answer.
     * Uses the {@link Object#toString()} representation of the options to format the prompt.
     *
     * @param question The text of the question.
     * @param defaultOption The option to present to the user as the default selection, and also the value to use when not connected to a console.
     * @return the answer or the default if not connected to a console.
     */
    <T> T selectOption(String question, Collection<T> options, T defaultOption);

    /**
     * Prompts the user to select an option from the given list and returns the answer.
     * Uses the {@link Object#toString()} representation of the options to format the prompt.
     *
     * @param question The text of the question.
     * @return A builder that can be used to configure the choice and ask the user to select an option.
     */
    <T> ChoiceBuilder<T> choice(String question, Collection<T> options);

    /**
     * Prompts the user to provide a string value.
     * @param question The text of the question.
     * @param defaultValue The option to present to the user as the default choice, and the value to use when not connected to a console
     * @return The answer or the given default if not connected to a console.
     */
    String askQuestion(String question, String defaultValue);

    /**
     * Return true if the user input was interrupted, e.g. via Ctrl+C.
     */
    boolean interrupted();

    interface ChoiceBuilder<T> {
        /**
         * Specifies the option to present to the user as the default selection, and the value to use when not connected to a console.
         * Both of these values default to the first option.
         *
         * <p>Replaces any value set using {@link #whenNotConnected(Object)}.
         */
        ChoiceBuilder<T> defaultOption(T defaultOption);

        /**
         * Specifies the option to use when not connected to a console.
         */
        ChoiceBuilder<T> whenNotConnected(T defaultOption);

        T ask();
    }
}
