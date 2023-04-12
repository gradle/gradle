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
import org.gradle.initialization.layout.BuildLayoutConfiguration;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.launcher.configuration.BuildLayoutResult;
import org.gradle.launcher.configuration.InitialProperties;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;
import java.util.function.Consumer;

public class BuildLayoutConverter {
    private final CommandLineConverter<BuildLayoutParameters> buildLayoutConverter = new LayoutCommandLineConverter();

    public void configure(CommandLineParser parser) {
        buildLayoutConverter.configure(parser);
    }

    public BuildLayoutResult defaultValues() {
        return new Result(new BuildLayoutParameters());
    }

    public BuildLayoutResult convert(InitialProperties systemProperties, ParsedCommandLine commandLine, @Nullable File workingDir) {
        return convert(systemProperties, commandLine, workingDir, parameters -> {
        });
    }

    public BuildLayoutResult convert(InitialProperties systemProperties, ParsedCommandLine commandLine, @Nullable File workingDir, Consumer<BuildLayoutParameters> defaults) {
        BuildLayoutParameters layoutParameters = new BuildLayoutParameters();
        if (workingDir != null) {
            layoutParameters.setCurrentDir(workingDir);
        }
        defaults.accept(layoutParameters);
        Map<String, String> requestedSystemProperties = systemProperties.getRequestedSystemProperties();
        new BuildLayoutParametersBuildOptions().propertiesConverter().convert(requestedSystemProperties, layoutParameters);
        buildLayoutConverter.convert(commandLine, layoutParameters);
        return new Result(layoutParameters);
    }

    private static class Result implements BuildLayoutResult {
        private final BuildLayoutParameters buildLayout;

        public Result(BuildLayoutParameters buildLayout) {
            this.buildLayout = buildLayout;
        }

        @Override
        public void applyTo(BuildLayoutParameters buildLayout) {
            buildLayout.setCurrentDir(this.buildLayout.getCurrentDir());
            buildLayout.setProjectDir(this.buildLayout.getProjectDir());
            buildLayout.setGradleUserHomeDir(this.buildLayout.getGradleUserHomeDir());
            buildLayout.setGradleInstallationHomeDir(this.buildLayout.getGradleInstallationHomeDir());
            buildLayout.setSettingsFile(this.buildLayout.getSettingsFile());
            buildLayout.setBuildFile(this.buildLayout.getBuildFile());
        }

        @Override
        @SuppressWarnings("deprecation") // StartParameter.setSettingsFile()
        public void applyTo(StartParameterInternal startParameter) {
            // Note that order is important here, as the setters have some side effects
            if (buildLayout.getProjectDir() != null) {
                startParameter.setProjectDir(buildLayout.getProjectDir());
            }
            startParameter.setCurrentDir(buildLayout.getCurrentDir());
            startParameter.setGradleUserHomeDir(buildLayout.getGradleUserHomeDir());
            if (buildLayout.getBuildFile() != null) {
                DeprecationLogger.whileDisabled(() ->
                    startParameter.setBuildFile(buildLayout.getBuildFile())
                );
            }
            if (buildLayout.getSettingsFile() != null) {
                DeprecationLogger.whileDisabled(() ->
                    startParameter.setSettingsFile(buildLayout.getSettingsFile())
                );
            }
        }

        @Override
        public BuildLayoutConfiguration toLayoutConfiguration() {
            return new BuildLayoutConfiguration(buildLayout);
        }

        @Override
        public File getGradleInstallationHomeDir() {
            return buildLayout.getGradleInstallationHomeDir();
        }

        @Override
        public File getGradleUserHomeDir() {
            return buildLayout.getGradleUserHomeDir();
        }
    }
}
