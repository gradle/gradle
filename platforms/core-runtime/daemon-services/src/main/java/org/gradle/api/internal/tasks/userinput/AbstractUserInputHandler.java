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

import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Try;

import java.util.function.Function;

public abstract class AbstractUserInputHandler implements UserInputHandler {
    @Override
    public <T> Provider<T> askUser(Function<? super UserQuestions, ? extends T> interaction) {
        return Providers.changing(new Providers.SerializableCallable<T>() {
            Try<T> result;

            @Override
            public T call() {
                if (result == null) {
                    UserInteraction questions = newInteraction();
                    try {
                        try {
                            T interactionResult = interaction.apply(questions);
                            result = Try.successful(interactionResult);
                        } catch (Throwable t) {
                            result = Try.failure(t);
                        }
                    } finally {
                        questions.finish();
                    }
                }
                return result.get();
            }
        });
    }

    protected abstract UserInteraction newInteraction();

    protected interface UserInteraction extends UserQuestions {
        /**
         * Notify the client that the interaction is complete.
         */
        void finish();
    }
}
