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
 * Represents a Gradle build option that takes a String value.
 *
 * @since 4.2
 */
public class GradleBuildStringOption extends GradleBuildOption {

    private final String gradleProperty;
    private CommandLineStringOption commandLineOption;

    public GradleBuildStringOption(String gradleProperty) {
        this.gradleProperty = gradleProperty;
    }

    @Override
    public String getGradleProperty() {
        return gradleProperty;
    }

    @Override
    public GradleBuildStringOption withCommandLineOption(String option, String description) {
        commandLineOption = new CommandLineStringOption(option, description);
        return this;
    }

    @Override
    public CommandLineStringOption getCommandLineOption() {
        return commandLineOption;
    }

    public static class CommandLineStringOption implements CommandLineOption {
        private final String option;
        private final String description;
        private boolean incubating;
        private boolean argument;

        public CommandLineStringOption(String option, String description) {
            this.option = option;
            this.description = description;
        }

        public String getOption() {
            return option;
        }

        public CommandLineStringOption hasArgument() {
            argument = true;
            return this;
        }

        @Override
        public CommandLineStringOption incubating() {
            incubating = true;
            return this;
        }

        @Override
        public void registerOption(CommandLineParser parser) {
            org.gradle.cli.CommandLineOption enabledFeatureCliOption = parser.option(option).hasDescription(description);

            if (argument) {
                enabledFeatureCliOption.hasArgument();
            }

            if (incubating) {
                enabledFeatureCliOption.incubating();
            }
        }
    }
}
