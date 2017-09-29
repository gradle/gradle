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

public class DefaultInputRequest implements InputRequest {

    protected final String text;

    public DefaultInputRequest(String text) {
        if (StringUtils.isBlank(text)) {
            throw new IllegalArgumentException("Text maybe not be null, empty or whitespace");
        }

        this.text = text;
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public String getPrompt() {
        return getText();
    }

    @Override
    public boolean isValid(String input) {
        return input == null ? false : true;
    }
}
