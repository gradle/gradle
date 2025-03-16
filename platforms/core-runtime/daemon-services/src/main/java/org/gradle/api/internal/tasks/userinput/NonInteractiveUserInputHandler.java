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

import java.util.Collection;
import java.util.function.Function;

public class NonInteractiveUserInputHandler extends AbstractUserInputHandler implements AbstractUserInputHandler.UserInteraction {
    @Override
    protected UserInteraction newInteraction() {
        return this;
    }

    @Override
    public void finish() {
    }

    @Override
    public Boolean askYesNoQuestion(String question) {
        return null;
    }

    @Override
    public boolean askBooleanQuestion(String question, boolean defaultValue) {
        return defaultValue;
    }

    @Override
    public <T> T selectOption(String question, Collection<T> options, T defaultOption) {
        return defaultOption;
    }

    @Override
    public <T> Choice<T> choice(String question, Collection<T> options) {
        return new NonInteractiveChoiceBuilder<>(options);
    }

    @Override
    public int askIntQuestion(String question, int minValue, int defaultValue) {
        return defaultValue;
    }

    @Override
    public String askQuestion(String question, String defaultValue) {
        return defaultValue;
    }

    @Override
    public boolean interrupted() {
        return false;
    }

    private static class NonInteractiveChoiceBuilder<T> implements Choice<T> {
        private T defaultOption;

        NonInteractiveChoiceBuilder(Collection<T> options) {
            defaultOption = options.iterator().next();
        }

        @Override
        public Choice<T> renderUsing(Function<T, String> renderer) {
            // Ignore, the values are never rendered
            return this;
        }

        @Override
        public Choice<T> defaultOption(T defaultOption) {
            this.defaultOption = defaultOption;
            return this;
        }

        @Override
        public Choice<T> whenNotConnected(T defaultOption) {
            this.defaultOption = defaultOption;
            return this;
        }

        @Override
        public T ask() {
            return defaultOption;
        }
    }
}
