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

import org.apache.commons.lang.StringUtils;

import java.util.List;

public class MultipleChoiceInputRequest implements InputRequest {

    private final InputRequest delegate;
    private final List<String> choices;

    public MultipleChoiceInputRequest(String text, List<String> choices) {
        delegate = new DefaultInputRequest(text);

        if (choices == null || choices.size() < 2) {
            throw new IllegalArgumentException("At least two choices need to be provided");
        }

        this.choices = choices;
    }

    @Override
    public String getText() {
        return delegate.getText();
    }

    @Override
    public String getPrompt() {
        StringBuilder descriptivePrompt = new StringBuilder();
        descriptivePrompt.append(getText());
        descriptivePrompt.append(" [");
        descriptivePrompt.append(StringUtils.join(choices, ", "));
        descriptivePrompt.append("]");
        return descriptivePrompt.toString();
    }

    @Override
    public boolean isValid(String input) {
        return choices.contains(input);
    }
}
