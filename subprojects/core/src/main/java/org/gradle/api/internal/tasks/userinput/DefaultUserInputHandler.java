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

import com.google.common.base.CharMatcher;
import com.google.common.collect.Lists;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Transformer;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.PromptOutputEvent;
import org.gradle.internal.logging.events.UserInputRequestEvent;
import org.gradle.internal.logging.events.UserInputResumeEvent;
import org.gradle.internal.time.Clock;
import org.gradle.util.internal.TextUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class DefaultUserInputHandler extends AbstractUserInputHandler {
    private static final List<String> YES_NO_CHOICES = Lists.newArrayList("yes", "no");
    private static final List<String> LENIENT_YES_NO_CHOICES = Lists.newArrayList("yes", "no", "y", "n");
    private final OutputEventListener outputEventBroadcaster;
    private final Clock clock;
    private final UserInputReader userInputReader;
    private final AtomicBoolean hasAsked = new AtomicBoolean();
    private final AtomicBoolean interrupted = new AtomicBoolean();

    public DefaultUserInputHandler(OutputEventListener outputEventBroadcaster, Clock clock, UserInputReader userInputReader) {
        this.outputEventBroadcaster = outputEventBroadcaster;
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

    private void sendPrompt(String prompt) {
        outputEventBroadcaster.onOutput(new PromptOutputEvent(clock.getCurrentTime(), prompt));
    }

    private String sanitizeInput(String input) {
        return CharMatcher.javaIsoControl().removeFrom(StringUtils.trim(input));
    }

    private class InteractiveUserQuestions implements UserInteraction {
        private boolean hasPrompted;

        @Override
        public Boolean askYesNoQuestion(String question) {
            StringBuilder builder = new StringBuilder();
            builder.append(question);
            builder.append(" [");
            builder.append(StringUtils.join(YES_NO_CHOICES, ", "));
            builder.append("] ");
            return prompt(builder.toString(), value -> {
                if (YES_NO_CHOICES.contains(value)) {
                    return BooleanUtils.toBoolean(value);
                }
                sendPrompt("Please enter 'yes' or 'no': ");
                return null;
            });
        }

        @Override
        public boolean askBooleanQuestion(String question, final boolean defaultValue) {
            StringBuilder builder = new StringBuilder();
            builder.append(question);
            builder.append(" (default: ");
            String defaultString = defaultValue ? "yes" : "no";
            builder.append(defaultString);
            builder.append(") [");
            builder.append(StringUtils.join(YES_NO_CHOICES, ", "));
            builder.append("] ");
            return prompt(builder.toString(), defaultValue, value -> {
                if (LENIENT_YES_NO_CHOICES.contains(value.toLowerCase(Locale.US))) {
                    return BooleanUtils.toBoolean(value);
                }
                sendPrompt("Please enter 'yes' or 'no' (default: '" + defaultString + "'): ");
                return null;
            });
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
            StringBuilder builder = new StringBuilder();
            builder.append(question);
            builder.append(" (min: ");
            builder.append(minValue);
            builder.append(", default: ");
            builder.append(defaultValue);
            builder.append("): ");
            return prompt(builder.toString(), defaultValue, sanitizedValue -> {
                try {
                    int result = Integer.parseInt(sanitizedValue);
                    if (result >= minValue) {
                        return result;
                    }
                    sendPrompt("Please enter an integer value >= " + minValue + " (default: " + defaultValue + "): ");
                    return null;
                } catch (NumberFormatException e) {
                    sendPrompt("Please enter an integer value (min: " + minValue + ", default: " + defaultValue + "): ");
                    return null;
                }
            });
        }

        @Override
        public String askQuestion(String question, final String defaultValue) {
            StringBuilder builder = new StringBuilder();
            builder.append(question);
            builder.append(" (default: ");
            builder.append(defaultValue);
            builder.append("): ");
            return prompt(builder.toString(), defaultValue, sanitizedValue -> sanitizedValue);
        }

        private <T> T prompt(String prompt, final T defaultValue, final Transformer<T, String> parser) {
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
        private <T> T prompt(String prompt, Transformer<T, String> parser) {
            if (interrupted.get()) {
                return null;
            }

            if (!hasPrompted) {
                outputEventBroadcaster.onOutput(new UserInputRequestEvent());
                hasPrompted = true;
            }

            try {
                // Add a line before the first question that has been asked of the user
                // This makes the assumption that all questions happen together, which is ok for now
                // It would be better to allow this handler to ask the output renderers to show a blank line before the prompt, if not already present
                if (hasAsked.compareAndSet(false, true)) {
                    sendPrompt(TextUtil.getPlatformLineSeparator());
                }
                sendPrompt(prompt);
                while (true) {
                    String input = userInputReader.readInput();
                    if (input == null) {
                        interrupted.set(true);
                        return null;
                    }

                    String sanitizedInput = sanitizeInput(input);
                    T result = parser.transform(sanitizedInput);
                    if (result != null) {
                        return result;
                    }
                }
            } finally {
                // Send an end-of-line. This is a workaround to convince the console that the cursor is at the start of the line to avoid indenting the next line of text that is displayed
                // It would be better for the console to listen for stuff read from stdin that would also be echoed to the output and update its state based on this
                sendPrompt(TextUtil.getPlatformLineSeparator());
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
            StringBuilder builder = new StringBuilder();
            builder.append(question);
            builder.append(":");
            builder.append(TextUtil.getPlatformLineSeparator());
            for (int i = 0; i < options.size(); i++) {
                T option = values.get(i);
                builder.append("  ");
                builder.append(i + 1);
                builder.append(": ");
                builder.append(renderer.apply(option));
                builder.append(TextUtil.getPlatformLineSeparator());
            }
            builder.append("Enter selection (default: ");
            builder.append(renderer.apply(defaultOption));
            builder.append(") [1..");
            builder.append(options.size());
            builder.append("] ");
            return prompt(builder.toString(), defaultOption, sanitizedInput -> {
                if (sanitizedInput.matches("\\d+")) {
                    int value = Integer.parseInt(sanitizedInput);
                    if (value > 0 && value <= values.size()) {
                        return values.get(value - 1);
                    }
                }
                sendPrompt("Please enter a value between 1 and " + options.size() + ": ");
                return null;
            });
        }

        @Override
        public void finish() {
            if (hasPrompted) {
                outputEventBroadcaster.onOutput(new UserInputResumeEvent());
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
