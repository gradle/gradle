/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.launcher.cli.converter;

import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.internal.buildoption.BuildOptionSet;
import org.gradle.launcher.configuration.AllProperties;

public class BuildOptionBackedConverter<T> {
    private final BuildOptionSet<T> buildOptions;

    public BuildOptionBackedConverter(BuildOptionSet<T> buildOptions) {
        this.buildOptions = buildOptions;
    }

    public void configure(CommandLineParser parser) {
        buildOptions.commandLineConverter().configure(parser);
    }

    public void convert(ParsedCommandLine commandLine, AllProperties properties, T target) {
        buildOptions.propertiesConverter().convert(properties.getProperties(), target);
        buildOptions.commandLineConverter().convert(commandLine, target);
    }
}
