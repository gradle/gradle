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

import javax.annotation.Nullable;

public class DefaultInputRequest implements InputRequest {

    private final String prompt;
    private final String defaultValue;

    public DefaultInputRequest(String prompt) {
        this(prompt, null);
    }

    public DefaultInputRequest(String prompt, String defaultValue) {
        if (StringUtils.isBlank(prompt)) {
            throw new IllegalArgumentException("Prompt maybe not be null, empty or whitespace");
        }

        this.prompt = prompt;
        this.defaultValue = defaultValue;
    }

    @Override
    public String getPrompt() {
        StringBuilder descriptivePrompt = new StringBuilder();
        descriptivePrompt.append(prompt);

        if (defaultValue != null) {
            descriptivePrompt.append(" (");
            descriptivePrompt.append(defaultValue);
            descriptivePrompt.append(")");
        }

        return descriptivePrompt.toString();
    }

    @Nullable
    @Override
    public String getDefaultValue() {
        return defaultValue;
    }

    @Override
    public boolean isValid(String input) {
        return input == null ? false : true;
    }
}
