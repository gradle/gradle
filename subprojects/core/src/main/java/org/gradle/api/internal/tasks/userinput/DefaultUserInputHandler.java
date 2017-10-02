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
import org.gradle.internal.logging.events.UserInputRequestEvent;
import org.gradle.internal.logging.events.UserInputResumeEvent;
import org.gradle.internal.logging.sink.OutputEventRenderer;

import java.util.List;

public class DefaultUserInputHandler implements UserInputHandler {
    private static final List<String> YES_NO_CHOICES = Lists.newArrayList("yes", "no");
    private final OutputEventRenderer outputEventRenderer;
    private final UserInputReader userInputReader;

    public DefaultUserInputHandler(OutputEventRenderer outputEventRenderer, UserInputReader userInputReader) {
        this.outputEventRenderer = outputEventRenderer;
        this.userInputReader = userInputReader;
    }

    @Override
    public Boolean askYesNoQuestion(String question) {
        outputEventRenderer.onOutput(new UserInputRequestEvent(createPrompt(question)));

        try {
            while (true) {
                String input = userInputReader.readInput();

                if (isInputCancelled(input)) {
                    return null;
                }

                String sanitizedInput = sanitizeInput(input);

                if (YES_NO_CHOICES.contains(sanitizedInput)) {
                    return BooleanUtils.toBoolean(sanitizedInput);
                }
            }
        } finally {
            outputEventRenderer.onOutput(new UserInputResumeEvent());
        }
    }

    private String createPrompt(String question) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(question);
        prompt.append(" [");
        prompt.append(StringUtils.join(YES_NO_CHOICES, ", "));
        prompt.append("]");
        return prompt.toString();
    }

    private boolean isInputCancelled(String input) {
        return input == null;
    }

    private String sanitizeInput(String input) {
        return CharMatcher.JAVA_ISO_CONTROL.removeFrom(StringUtils.trim(input));
    }
}
