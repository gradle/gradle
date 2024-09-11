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

import org.gradle.api.provider.Provider;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
import java.util.function.Function;

@ServiceScope(Scope.BuildSession.class)
public interface UserInputHandler {
    /**
     * Prompts the user with a yes/no question and returns the answer. Requires that the user explicitly type "yes" or "no".
     *
     * @param question The text of the question.
     * @return the answer or {@code null} if not connected to a console.
     */
    @Nullable
    @UsedByScanPlugin
    default Boolean askYesNoQuestion(String question) {
        return askUser(interaction -> interaction.askYesNoQuestion(question)).getOrNull();
    }

    /**
     * Ask the user one or more questions to produce a value of type {@link T}.
     */
    <T> Provider<T> askUser(Function<? super UserQuestions, ? extends T> interaction);

    /**
     * Return true if the user input has been interrupted, e.g. via Ctrl+C or some interaction with the client UI.
     */
    boolean interrupted();
}
