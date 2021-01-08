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
import org.gradle.util.TextUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultUserInputHandler implements UserInputHandler {
    private static final List<String> YES_NO_CHOICES = Lists.newArrayList("yes", "no");
    private final OutputEventListener outputEventBroadcaster;
    private final Clock clock;
    private final UserInputReader userInputReader;
    private final AtomicBoolean hasAsked = new AtomicBoolean();

    public DefaultUserInputHandler(OutputEventListener outputEventBroadcaster, Clock clock, UserInputReader userInputReader) {
        this.outputEventBroadcaster = outputEventBroadcaster;
        this.clock = clock;
        this.userInputReader = userInputReader;
    }

    @Override
    public Boolean askYesNoQuestion(String question) {
        StringBuilder builder = new StringBuilder();
        builder.append(question);
        builder.append(" [");
        builder.append(StringUtils.join(YES_NO_CHOICES, ", "));
        builder.append("] ");
        return prompt(builder.toString(), new BooleanParser());
    }

    @Override
    public boolean askYesNoQuestion(String question, final boolean defaultValue) {
        StringBuilder builder = new StringBuilder();
        builder.append(question);
        builder.append(" (default: ");
        builder.append(defaultValue ? "yes" : "no");
        builder.append(") [");
        builder.append(StringUtils.join(YES_NO_CHOICES, ", "));
        builder.append("] ");
        return prompt(builder.toString(), defaultValue, new BooleanParser());
    }

    @Override
    public <T> T selectOption(String question, final Collection<T> options, final T defaultOption) {
        final List<T> values = new ArrayList<T>(options);
        StringBuilder builder = new StringBuilder();
        builder.append(question);
        builder.append(":");
        builder.append(TextUtil.getPlatformLineSeparator());
        for (int i = 0; i < options.size(); i++) {
            T option = values.get(i);
            builder.append("  ");
            builder.append(i + 1);
            builder.append(": ");
            builder.append(option);
            builder.append(TextUtil.getPlatformLineSeparator());
        }
        builder.append("Enter selection (default: ");
        builder.append(defaultOption);
        builder.append(") [1..");
        builder.append(options.size());
        builder.append("] ");
        return prompt(builder.toString(), defaultOption, new Transformer<T, String>() {
            @Override
            public T transform(String sanitizedInput) {
                if (sanitizedInput.matches("\\d+")) {
                    int value = Integer.parseInt(sanitizedInput);
                    if (value > 0 && value <= values.size()) {
                        return values.get(value - 1);
                    }
                }
                sendPrompt("Please enter a value between 1 and " + options.size() + ": ");
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
        return prompt(builder.toString(), defaultValue, new Transformer<String, String>() {
            @Override
            public String transform(String sanitizedValue) {
                return sanitizedValue;
            }
        });
    }

    private <T> T prompt(String prompt, final T defaultValue, final Transformer<T, String> parser) {
        T result = prompt(prompt, new Transformer<T, String>() {
            @Override
            public T transform(String sanitizedInput) {
                if (sanitizedInput.isEmpty()) {
                    return defaultValue;
                }
                return parser.transform(sanitizedInput);
            }
        });
        if (result == null) {
            return defaultValue;
        }
        return result;
    }

    @Nullable
    private <T> T prompt(String prompt, Transformer<T, String> parser) {
        outputEventBroadcaster.onOutput(new UserInputRequestEvent());
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
                    return null;
                }

                String sanitizedInput = sanitizeInput(input);
                T result = parser.transform(sanitizedInput);
                if (result != null) {
                    return result;
                }
            }
        } finally {
            // Send a end-of-line. This is a workaround to convince the console that the cursor is at the start of the line to avoid indenting the next line of text that is displayed
            // It would be better for the console to listen for stuff read from stdin that would also be echoed to the output and update its state based on this
            sendPrompt(TextUtil.getPlatformLineSeparator());
            outputEventBroadcaster.onOutput(new UserInputResumeEvent());
        }
    }

    private void sendPrompt(String prompt) {
        outputEventBroadcaster.onOutput(new PromptOutputEvent(clock.getCurrentTime(), prompt));
    }

    private String sanitizeInput(String input) {
        return CharMatcher.javaIsoControl().removeFrom(StringUtils.trim(input));
    }

    private class BooleanParser implements Transformer<Boolean, String> {
        @Override
        public Boolean transform(String value) {
            if (YES_NO_CHOICES.contains(value)) {
                return BooleanUtils.toBoolean(value);
            }
            sendPrompt("Please enter 'yes' or 'no': ");
            return null;
        }
    }
}
