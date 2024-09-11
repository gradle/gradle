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

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * Allows the user to be asked various questions, potentially interactively.
 */
public interface UserQuestions {
    /**
     * Asks the user a yes/no question and returns the answer. Requires that the user explicitly type "yes" or "no".
     *
     * @param question The text of the question.
     * @return the answer or {@code null} if not connected to a console.
     */
    @Nullable
    Boolean askYesNoQuestion(String question);

    /**
     * Asks the user a question that has a boolean result and returns the answer.
     *
     * <p>The client UI is free to choose a convenient representation for boolean values, for example
     * allowing the user to type 'y' or 'n' or presenting a checkbox. The user is not required to answer 'true' or 'false'
     *
     * @param question The text of the question.
     * @param defaultValue The option to present to the user as the default choice, and the value to use when not connected to a console
     * @return the answer or the given default if not connected to a console.
     */
    boolean askBooleanQuestion(String question, boolean defaultValue);

    /**
     * Asks the user to select an option from the given list and returns the answer.
     * Uses the {@link Object#toString()} representation of the options to format the prompt.
     * Does not prompt the user when there is only one option in the given list.
     *
     * @param question The text of the question.
     * @param defaultOption The option to present to the user as the default selection, and also the value to use when not connected to a console.
     * @return the answer or the default if not connected to a console.
     */
    <T> T selectOption(String question, Collection<T> options, T defaultOption);

    /**
     * Creates a {@link Choice} that can ask the user to select an option from the given list and returns the answer.
     * Uses the {@link Object#toString()} representation of the options to format the prompt, the returned {@link Choice} can be used to change this.
     * Does not prompt the user when there is only one option in the given list.
     *
     * @param question The text of the question.
     * @return A {@link Choice} that can be used to configure the choice and ask the user to select an option.
     */
    <T> Choice<T> choice(String question, Collection<T> options);

    /**
     * Asks the user a question that has an integer result and returns the answer.
     *
     * @param question The text of the question.
     * @param defaultValue The option to present to the user as the default choice, and the value to use when not connected to a console
     * @return The answer or the given default if not connected to a console.
     */
    int askIntQuestion(String question, int minValue, int defaultValue);

    /**
     * Asks the user a question that has a string result and returns the answer. The answer must not contain multiple lines.
     *
     * @param question The text of the question.
     * @param defaultValue The option to present to the user as the default choice, and the value to use when not connected to a console
     * @return The answer or the given default if not connected to a console.
     */
    String askQuestion(String question, String defaultValue);
}
