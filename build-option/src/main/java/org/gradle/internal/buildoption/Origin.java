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

package org.gradle.internal.buildoption;

import org.apache.commons.lang.StringUtils;
import org.gradle.cli.CommandLineArgumentException;

public abstract class Origin {
    protected String source;

    public static Origin forGradleProperty(String gradleProperty) {
        return new GradlePropertyOrigin(gradleProperty);
    }

    public static Origin forCommandLine(String commandLineOption) {
        return new CommandLineOrigin(commandLineOption);
    }

    private Origin(String source) {
        this.source = source;
    }

    public abstract void handleInvalidValue(String value, String hint);

    public void handleInvalidValue(String value) {
        handleInvalidValue(value, null);
    }

    String hintMessage(String hint) {
        if (StringUtils.isBlank(hint)) {
            return "";
        }
        return String.format(" (%s)", hint);
    }

    private static class GradlePropertyOrigin extends Origin {
        public GradlePropertyOrigin(String value) {
            super(value);
        }

        @Override
        public void handleInvalidValue(String value, String hint) {
            String message = String.format("Value '%s' given for %s Gradle property is invalid%s", value, source, hintMessage(hint));
            throw new IllegalArgumentException(message);
        }
    }

    private static class CommandLineOrigin extends Origin {
        public CommandLineOrigin(String value) {
            super(value);
        }

        @Override
        public void handleInvalidValue(String value, String hint) {
            String message = String.format("Argument value '%s' given for --%s option is invalid%s", value, source, hintMessage(hint));
            throw new CommandLineArgumentException(message);
        }
    }
}
