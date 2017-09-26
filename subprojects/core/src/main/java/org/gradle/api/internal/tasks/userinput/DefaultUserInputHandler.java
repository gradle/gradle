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
import org.apache.commons.lang.StringUtils;
import org.gradle.internal.logging.events.UserInputRequestEvent;
import org.gradle.internal.logging.events.UserInputResumeEvent;
import org.gradle.internal.logging.sink.OutputEventRenderer;

public class DefaultUserInputHandler implements UserInputHandler {
    private final OutputEventRenderer outputEventRenderer;
    private final UserInputReader userInputReader;

    public DefaultUserInputHandler(OutputEventRenderer outputEventRenderer, UserInputReader userInputReader) {
        this.outputEventRenderer = outputEventRenderer;
        this.userInputReader = userInputReader;
    }

    @Override
    public String getInput(InputRequest inputRequest) {
        outputEventRenderer.onOutput(new UserInputRequestEvent(inputRequest.getPrompt()));

        try {
            while (true) {
                String input = userInputReader.readInput();

                if (isInputCancelled(input)) {
                    return null;
                }

                if (isInputDefaultValue(inputRequest, input)) {
                    return inputRequest.getDefaultValue();
                }

                String sanitizedInput = sanitizeInput(input);

                if (inputRequest.isValid(sanitizedInput)) {
                    return sanitizedInput;
                }
            }
        } finally {
            outputEventRenderer.onOutput(new UserInputResumeEvent());
        }
    }

    private boolean isInputCancelled(String input) {
        return input == null;
    }

    private boolean isInputDefaultValue(InputRequest inputRequest, String input) {
        return inputRequest.getDefaultValue() != null && "".equals(input);
    }

    private String sanitizeInput(String input) {
        return CharMatcher.JAVA_ISO_CONTROL.removeFrom(StringUtils.trim(input));
    }
}
