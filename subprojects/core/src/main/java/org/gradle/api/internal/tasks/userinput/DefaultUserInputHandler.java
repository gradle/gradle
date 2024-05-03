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

import org.apache.commons.lang.BooleanUtils;
import org.gradle.api.Transformer;
import org.gradle.internal.logging.events.BooleanQuestionPromptEvent;
import org.gradle.internal.logging.events.IntQuestionPromptEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.PromptOutputEvent;
import org.gradle.internal.logging.events.SelectOptionPromptEvent;
import org.gradle.internal.logging.events.TextQuestionPromptEvent;
import org.gradle.internal.logging.events.UserInputRequestEvent;
import org.gradle.internal.logging.events.UserInputResumeEvent;
import org.gradle.internal.logging.events.YesNoQuestionPromptEvent;
import org.gradle.internal.time.Clock;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class DefaultUserInputHandler extends AbstractUserInputHandler {
    private final OutputEventListener eventDispatch;
    private final Clock clock;
    private final UserInputReader userInputReader;
    private final AtomicBoolean interrupted = new AtomicBoolean();

    public DefaultUserInputHandler(OutputEventListener eventDispatch, Clock clock, UserInputReader userInputReader) {
        this.eventDispatch = eventDispatch;
        this.clock = clock;
        this.userInputReader = userInputReader;
    }

    @Override
    protected UserInteraction newInteraction() {
        return new InteractiveUserQuestions();
    }

    @Override
    public boolean interrupted() {
        return interrupted.get();
    }

    private class InteractiveUserQuestions implements UserInteraction {
        private boolean hasPrompted;

        @Override
        public Boolean askYesNoQuestion(String question) {
            YesNoQuestionPromptEvent prompt = new YesNoQuestionPromptEvent(clock.getCurrentTime(), question);
            return prompt(prompt, BooleanUtils::toBoolean);
        }

        @Override
        public boolean askBooleanQuestion(String question, final boolean defaultValue) {
            BooleanQuestionPromptEvent prompt = new BooleanQuestionPromptEvent(clock.getCurrentTime(), question, defaultValue);
            return prompt(prompt, defaultValue, BooleanUtils::toBoolean);
        }

        @Override
        public <T> T selectOption(String question, final Collection<T> options, final T defaultOption) {
            return choice(question, options).defaultOption(defaultOption).ask();
        }

        @Override
        public <T> Choice<T> choice(String question, Collection<T> options) {
            if (options.isEmpty()) {
                throw new IllegalArgumentException("No options provided.");
            }
            return new InteractiveChoiceBuilder<>(this, options, question);
        }

        @Override
        public int askIntQuestion(String question, int minValue, int defaultValue) {
            IntQuestionPromptEvent prompt = new IntQuestionPromptEvent(clock.getCurrentTime(), question, minValue, defaultValue);
            return prompt(prompt, defaultValue, Integer::parseInt);
        }

        @Override
        public String askQuestion(String question, final String defaultValue) {
            TextQuestionPromptEvent prompt = new TextQuestionPromptEvent(clock.getCurrentTime(), question, defaultValue);
            return prompt(prompt, defaultValue, sanitizedValue -> sanitizedValue);
        }

        private <T> T prompt(PromptOutputEvent prompt, final T defaultValue, final Transformer<T, String> parser) {
            T result = prompt(prompt, sanitizedInput -> {
                if (sanitizedInput.isEmpty()) {
                    return defaultValue;
                }
                return parser.transform(sanitizedInput);
            });
            if (result == null) {
                return defaultValue;
            }
            return result;
        }

        @Nullable
        private <T> T prompt(PromptOutputEvent prompt, Transformer<T, String> parser) {
            if (interrupted.get()) {
                return null;
            }

            if (!hasPrompted) {
                eventDispatch.onOutput(new UserInputRequestEvent());
                hasPrompted = true;
            }

            eventDispatch.onOutput(prompt);
            while (true) {
                UserInputReader.UserInput input = userInputReader.readInput();
                if (input == UserInputReader.END_OF_INPUT) {
                    interrupted.set(true);
                    return null;
                }

                T result = parser.transform(input.getText());
                if (result != null) {
                    return result;
                }
            }
        }

        private <T> T selectOption(String question, Collection<T> options, T defaultOption, Function<T, String> renderer) {
            if (!options.contains(defaultOption)) {
                throw new IllegalArgumentException("Default value is not one of the provided options.");
            }
            if (options.size() == 1) {
                return defaultOption;
            }

            final List<T> values = new ArrayList<>(options);
            final List<String> displayValues = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) {
                T option = values.get(i);
                displayValues.add(renderer.apply(option));
            }
            SelectOptionPromptEvent prompt = new SelectOptionPromptEvent(clock.getCurrentTime(), question, displayValues, values.indexOf(defaultOption));
            return prompt(prompt, defaultOption, sanitizedInput -> {
                int value = Integer.parseInt(sanitizedInput);
                return values.get(value);
            });
        }

        @Override
        public void finish() {
            if (hasPrompted) {
                eventDispatch.onOutput(new UserInputResumeEvent(clock.getCurrentTime()));
            }
        }
    }

    private static class InteractiveChoiceBuilder<T> implements Choice<T> {
        private final InteractiveUserQuestions owner;
        private final Collection<T> options;
        private final String question;
        private T defaultOption;
        private Function<T, String> renderer = Object::toString;

        public InteractiveChoiceBuilder(InteractiveUserQuestions owner, Collection<T> options, String question) {
            this.owner = owner;
            this.options = options;
            this.question = question;
            defaultOption = options.iterator().next();
        }

        @Override
        public Choice<T> renderUsing(Function<T, String> renderer) {
            this.renderer = renderer;
            return this;
        }

        @Override
        public Choice<T> defaultOption(T defaultOption) {
            this.defaultOption = defaultOption;
            return this;
        }

        @Override
        public Choice<T> whenNotConnected(T defaultOption) {
            // Ignore
            return this;
        }

        @Override
        public T ask() {
            return owner.selectOption(question, options, defaultOption, renderer);
        }
    }
}
