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

package org.gradle.initialization.option;

import org.gradle.cli.CommandLineParser;

/**
 * Represents a Gradle build option that takes a Boolean value.
 *
 * @since 4.2
 */
public class GradleBuildBooleanOption extends GradleBuildOption {

    private final String gradleProperty;
    private CommandLineBooleanOption commandLineOption;

    public GradleBuildBooleanOption(String gradleProperty) {
        this.gradleProperty = gradleProperty;
    }

    @Override
    public String getGradleProperty() {
        return gradleProperty;
    }

    @Override
    public GradleBuildBooleanOption withCommandLineOption(String option, String description) {
        commandLineOption = new CommandLineBooleanOption(option, description);
        return this;
    }

    @Override
    public CommandLineBooleanOption getCommandLineOption() {
        return commandLineOption;
    }

    public static class CommandLineBooleanOption implements CommandLineOption {
        private static final String DISABLED_OPTION_PREFIX = "no-";
        private final String enabledOption;
        private final String disabledOption;
        private final String description;
        private boolean incubating;

        public CommandLineBooleanOption(String option, String description) {
            this.enabledOption = option;
            this.disabledOption = DISABLED_OPTION_PREFIX + option;
            this.description = description;
        }

        public String getEnabledOption() {
            return enabledOption;
        }

        public String getDisabledOption() {
            return disabledOption;
        }

        @Override
        public CommandLineBooleanOption incubating() {
            incubating = true;
            return this;
        }

        @Override
        public void registerOption(CommandLineParser parser) {
            org.gradle.cli.CommandLineOption enabledFeatureCliOption = parser.option(enabledOption).hasDescription(description);

            if (incubating) {
                enabledFeatureCliOption.incubating();
            }

            org.gradle.cli.CommandLineOption disabledFeatureCliOption = parser.option(disabledOption).hasDescription(createDisabledDescription());

            if (incubating) {
                disabledFeatureCliOption.incubating();
            }

            parser.allowOneOf(enabledOption, disabledOption);
        }

        private String createDisabledDescription() {
            return "Disables feature --" + enabledOption + ".";
        }
    }
}
