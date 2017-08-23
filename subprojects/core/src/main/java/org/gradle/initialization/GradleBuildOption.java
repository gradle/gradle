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

package org.gradle.initialization;

import org.gradle.cli.CommandLineParser;

import javax.annotation.Nullable;

/**
 * Defines a Gradle build option exposed as command-line option, Gradle property or both.
 *
 * @since 4.2
 */
public class GradleBuildOption {

    private final OptionType type;
    private final CommandLineOption commandLineOption;
    private final String gradleProperty;

    public GradleBuildOption(OptionType type, String gradleProperty) {
        this(type, gradleProperty, null);
    }

    public GradleBuildOption(OptionType type, String gradleProperty, @Nullable CommandLineOption commandLineOption) {
        this.type = type;
        this.gradleProperty = gradleProperty;
        this.commandLineOption = commandLineOption;
    }

    public OptionType getType() {
        return type;
    }

    public String getGradleProperty() {
        return gradleProperty;
    }

    @Nullable
    public CommandLineOption getCommandLineOption() {
        return commandLineOption;
    }

    public enum OptionType {
        BOOLEAN, STRING
    }

    public static class CommandLineOption {
        private final String option;
        private final String description;
        private final boolean incubating;

        public CommandLineOption(String option, String description, boolean incubating) {
            this.option = option;
            this.description = description;
            this.incubating = incubating;
        }

        public String getOption() {
            return option;
        }

        public String getDescription() {
            return description;
        }

        public boolean isIncubating() {
            return incubating;
        }

        public org.gradle.cli.CommandLineOption registerOption(CommandLineParser parser) {
            org.gradle.cli.CommandLineOption commandLineOption = parser.option(option).hasDescription(description);

            if (incubating) {
                commandLineOption.incubating();
            }

            return commandLineOption;
        }
    }
}
