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

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.cli.CommandLineConverter;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.initialization.BuildLayoutParametersBuildOptions;
import org.gradle.initialization.LayoutCommandLineConverter;
import org.gradle.initialization.location.BuildLocationConfiguration;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.launcher.configuration.BuildLocationResult;
import org.gradle.launcher.configuration.InitialProperties;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;
import java.util.function.Consumer;

public class BuildLocationConverter {
    private final CommandLineConverter<BuildLayoutParameters> buildLayoutConverter = new LayoutCommandLineConverter();

    public void configure(CommandLineParser parser) {
        buildLayoutConverter.configure(parser);
    }

    public BuildLocationResult defaultValues() {
        return new Result(new BuildLayoutParameters());
    }

    public BuildLocationResult convert(InitialProperties systemProperties, ParsedCommandLine commandLine, @Nullable File workingDir) {
        return convert(systemProperties, commandLine, workingDir, parameters -> {
        });
    }

    public BuildLocationResult convert(InitialProperties systemProperties, ParsedCommandLine commandLine, @Nullable File workingDir, Consumer<BuildLayoutParameters> defaults) {
        BuildLayoutParameters locationParameters = new BuildLayoutParameters();
        if (workingDir != null) {
            locationParameters.setCurrentDir(workingDir);
        }
        defaults.accept(locationParameters);
        Map<String, String> requestedSystemProperties = systemProperties.getRequestedSystemProperties();
        new BuildLayoutParametersBuildOptions().propertiesConverter().convert(requestedSystemProperties, locationParameters);
        buildLayoutConverter.convert(commandLine, locationParameters);
        return new Result(locationParameters);
    }

    private static class Result implements BuildLocationResult {
        private final BuildLayoutParameters locationParameters;

        public Result(BuildLayoutParameters locationParameters) {
            this.locationParameters = locationParameters;
        }

        @Override
        public void applyTo(BuildLayoutParameters buildLayoutParameters) {
            buildLayoutParameters.setCurrentDir(this.locationParameters.getCurrentDir());
            buildLayoutParameters.setProjectDir(this.locationParameters.getProjectDir());
            buildLayoutParameters.setGradleUserHomeDir(this.locationParameters.getGradleUserHomeDir());
            buildLayoutParameters.setGradleInstallationHomeDir(this.locationParameters.getGradleInstallationHomeDir());
            buildLayoutParameters.setSettingsFile(this.locationParameters.getSettingsFile());
            buildLayoutParameters.setBuildFile(this.locationParameters.getBuildFile());
        }

        @Override
        @SuppressWarnings("deprecation") // StartParameter.setSettingsFile()
        public void applyTo(StartParameterInternal startParameter) {
            // Note that order is important here, as the setters have some side effects
            if (locationParameters.getProjectDir() != null) {
                startParameter.setProjectDir(locationParameters.getProjectDir());
            }
            startParameter.setCurrentDir(locationParameters.getCurrentDir());
            startParameter.setGradleUserHomeDir(locationParameters.getGradleUserHomeDir());
            if (locationParameters.getBuildFile() != null) {
                DeprecationLogger.whileDisabled(() ->
                    startParameter.setBuildFile(locationParameters.getBuildFile())
                );
            }
            if (locationParameters.getSettingsFile() != null) {
                DeprecationLogger.whileDisabled(() ->
                    startParameter.setSettingsFile(locationParameters.getSettingsFile())
                );
            }
        }

        @Override
        public BuildLocationConfiguration toLocationConfiguration() {
            return new BuildLocationConfiguration(locationParameters);
        }

        @Override
        public File getGradleInstallationHomeDir() {
            return locationParameters.getGradleInstallationHomeDir();
        }

        @Override
        public File getGradleUserHomeDir() {
            return locationParameters.getGradleUserHomeDir();
        }
    }
}
