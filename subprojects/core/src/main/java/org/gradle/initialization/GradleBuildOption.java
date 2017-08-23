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

public abstract class GradleBuildOption {

    public static GradleBuildOption create(OptionType type, String gradleProperty) {
        return new DefaultGradleBuildOption(type, gradleProperty);
    }

    public abstract OptionType getType();
    public abstract String getGradleProperty();
    abstract GradleBuildOption withCommandLineOption(String option, String description);

    @Nullable
    public abstract CommandLineOption getCommandLineOption();

    public enum OptionType {
        BOOLEAN, STRING
    }

    public static class CommandLineOption {
        private final OptionType type;
        private final String option;
        private final String description;
        private boolean incubating;
        private boolean argument;

        public CommandLineOption(OptionType type, String option, String description) {
            this.type = type;
            this.option = option;
            this.description = description;
        }

        public String getOption() {
            return option;
        }

        public String getDescription() {
            return description;
        }

        public CommandLineOption incubating() {
            incubating = true;
            return this;
        }

        public CommandLineOption hasArgument() {
            argument = true;
            return this;
        }

        public void registerOption(CommandLineParser parser) {
            org.gradle.cli.CommandLineOption enabledFeatureCliOption = parser.option(option).hasDescription(description);

            if (type == OptionType.STRING) {
                if (argument) {
                    enabledFeatureCliOption.hasArgument();
                }
            }

            if (incubating) {
                enabledFeatureCliOption.incubating();
            }

            if (type == OptionType.BOOLEAN) {
                String disabledOption = createDisabledOption();
                org.gradle.cli.CommandLineOption disabledFeatureCliOption = parser.option(disabledOption).hasDescription(createDisabledDescription());

                if (incubating) {
                    disabledFeatureCliOption.incubating();
                }

                parser.allowOneOf(option, disabledOption);
            }
        }

        private String createDisabledOption() {
            return "no-" + option;
        }

        private String createDisabledDescription() {
            return "Disables feature --" + option + ".";
        }
    }
}
