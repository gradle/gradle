/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.cli.AbstractCommandLineConverter;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.internal.buildoption.BuildOption;

import java.util.List;

public class LayoutCommandLineConverter extends AbstractCommandLineConverter<BuildLayoutParameters> {
    private List<BuildOption<BuildLayoutParameters>> buildOptions = BuildLayoutParametersBuildOptions.get();

    @Override
    public BuildLayoutParameters convert(ParsedCommandLine options, BuildLayoutParameters target) throws CommandLineArgumentException {
        for (BuildOption<BuildLayoutParameters> option : buildOptions) {
            option.applyFromCommandLine(options, target);
        }

        if (options.getExtraArguments().contains("init")) {
            target.setSearchUpwards(false);
        }

        if (target.getSearchDir().getName().equals("buildSrc")) {
            target.setSearchUpwards(false);
        }

        return target;
    }

    @Override
    public void configure(CommandLineParser parser) {
        for (BuildOption<BuildLayoutParameters> option : buildOptions) {
            option.configure(parser);
        }
    }
}
