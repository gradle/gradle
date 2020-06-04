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

import org.gradle.cli.CommandLineConverter;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.cli.SystemPropertiesCommandLineConverter;
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.initialization.BuildLayoutParametersBuildOptions;
import org.gradle.initialization.LayoutCommandLineConverter;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class BuildLayoutConverter {
    private final CommandLineConverter<BuildLayoutParameters> buildLayoutConverter = new LayoutCommandLineConverter();
    private final CommandLineConverter<Map<String, String>> systemPropertiesCommandLineConverter = new SystemPropertiesCommandLineConverter();

    public void configure(CommandLineParser parser) {
        buildLayoutConverter.configure(parser);
        systemPropertiesCommandLineConverter.configure(parser);
    }

    public Result defaultValues() {
        return new Result(new BuildLayoutParameters(), Collections.emptyMap());
    }

    public Result convert(ParsedCommandLine commandLine) {
        return convert(commandLine, parameters -> {
        });
    }

    public Result convert(ParsedCommandLine commandLine, Consumer<BuildLayoutParameters> overrides) {
        BuildLayoutParameters layoutParameters = new BuildLayoutParameters();
        Map<String, String> requestedSystemProperties = systemPropertiesCommandLineConverter.convert(commandLine, new HashMap<>());
        new BuildLayoutParametersBuildOptions().propertiesConverter().convert(requestedSystemProperties, layoutParameters);
        buildLayoutConverter.convert(commandLine, layoutParameters);
        overrides.accept(layoutParameters);
        return new Result(layoutParameters, Collections.unmodifiableMap(requestedSystemProperties));
    }

    /**
     * Immutable build layout details calculated from command-line arguments and the environment.
     */
    public static class Result {
        private final BuildLayoutParameters buildLayout;
        private final Map<String, String> systemProperties;

        public Result(BuildLayoutParameters buildLayout, Map<String, String> systemProperties) {
            this.buildLayout = buildLayout;
            this.systemProperties = systemProperties;
        }

        public void collectSystemPropertiesInto(Map<String, String> dest) {
            dest.putAll(systemProperties);
        }

        public void applyTo(BuildLayoutParameters buildLayout) {
            buildLayout.setCurrentDir(this.buildLayout.getCurrentDir());
            buildLayout.setProjectDir(this.buildLayout.getProjectDir());
            buildLayout.setSearchUpwards(this.buildLayout.getSearchUpwards());
            buildLayout.setGradleUserHomeDir(this.buildLayout.getGradleUserHomeDir());
            buildLayout.setGradleInstallationHomeDir(this.buildLayout.getGradleInstallationHomeDir());
        }

        public File getGradleUserHomeDir() {
            return buildLayout.getGradleUserHomeDir();
        }
    }
}
