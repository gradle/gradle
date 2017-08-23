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

public class DefaultGradleBuildOption extends GradleBuildOption {

    private final OptionType type;
    private final String gradleProperty;
    private CommandLineOption commandLineOption;

    public DefaultGradleBuildOption(OptionType type, String gradleProperty) {
        this.type = type;
        this.gradleProperty = gradleProperty;
    }

    @Override
    public OptionType getType() {
        return type;
    }

    @Override
    public String getGradleProperty() {
        return gradleProperty;
    }

    @Override
    public GradleBuildOption withCommandLineOption(String option, String description) {
        commandLineOption = new CommandLineOption(type, option, description);
        return this;
    }

    @Override
    public CommandLineOption getCommandLineOption() {
        return commandLineOption;
    }
}
