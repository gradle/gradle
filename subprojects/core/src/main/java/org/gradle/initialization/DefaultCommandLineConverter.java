/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.StartParameter;
import org.gradle.api.Transformer;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.file.BasicFileResolver;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.cli.AbstractCommandLineConverter;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineConverter;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.cli.ProjectPropertiesCommandLineConverter;
import org.gradle.cli.SystemPropertiesCommandLineConverter;
import org.gradle.concurrent.ParallelismConfiguration;
import org.gradle.internal.buildoption.BuildOption;
import org.gradle.internal.logging.LoggingCommandLineConverter;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.gradle.StartParameter.GRADLE_USER_HOME_PROPERTY_KEY;

public class DefaultCommandLineConverter extends AbstractCommandLineConverter<StartParameterInternal> {
    private final CommandLineConverter<LoggingConfiguration> loggingConfigurationCommandLineConverter = new LoggingCommandLineConverter();
    private final CommandLineConverter<ParallelismConfiguration> parallelConfigurationCommandLineConverter = new ParallelismConfigurationCommandLineConverter();
    private final SystemPropertiesCommandLineConverter systemPropertiesCommandLineConverter = new SystemPropertiesCommandLineConverter();
    private final ProjectPropertiesCommandLineConverter projectPropertiesCommandLineConverter = new ProjectPropertiesCommandLineConverter();
    private final List<BuildOption<StartParameterInternal>> buildOptions = StartParameterBuildOptions.get();
    private final LayoutCommandLineConverter layoutCommandLineConverter;

    public DefaultCommandLineConverter() {
        layoutCommandLineConverter = new LayoutCommandLineConverter();
    }

    @Override
    public void configure(CommandLineParser parser) {
        loggingConfigurationCommandLineConverter.configure(parser);
        parallelConfigurationCommandLineConverter.configure(parser);
        systemPropertiesCommandLineConverter.configure(parser);
        projectPropertiesCommandLineConverter.configure(parser);
        layoutCommandLineConverter.configure(parser);
        parser.allowMixedSubcommandsAndOptions();

        for (BuildOption<? extends StartParameter> option : buildOptions) {
            option.configure(parser);
        }
    }

    @Override
    public StartParameterInternal convert(final ParsedCommandLine options, final StartParameterInternal startParameter) throws CommandLineArgumentException {
        loggingConfigurationCommandLineConverter.convert(options, startParameter);
        parallelConfigurationCommandLineConverter.convert(options, startParameter);
        Transformer<File, String> resolver = new BasicFileResolver(startParameter.getCurrentDir());

        Map<String, String> systemProperties = systemPropertiesCommandLineConverter.convert(options, new HashMap<String, String>());
        convertCommandLineSystemProperties(systemProperties, startParameter, resolver);

        Map<String, String> projectProperties = projectPropertiesCommandLineConverter.convert(options, new HashMap<String, String>());
        startParameter.getProjectProperties().putAll(projectProperties);

        BuildLayoutParameters layout = new BuildLayoutParameters()
                .setGradleUserHomeDir(startParameter.getGradleUserHomeDir())
                .setProjectDir(startParameter.getProjectDir())
                .setCurrentDir(startParameter.getCurrentDir());
        layoutCommandLineConverter.convert(options, layout);
        startParameter.setGradleUserHomeDir(layout.getGradleUserHomeDir());
        if (layout.getProjectDir() != null) {
            startParameter.setProjectDir(layout.getProjectDir());
        }
        startParameter.setSearchUpwards(layout.getSearchUpwards());

        if (!options.getExtraArguments().isEmpty()) {
            startParameter.setTaskNames(options.getExtraArguments());
        }

        for (BuildOption<StartParameterInternal> option : buildOptions) {
            option.applyFromCommandLine(options, startParameter);
        }

        for (String deprecation : layout.getDeprecations()) {
            startParameter.addDeprecation(deprecation);
        }

        return startParameter;
    }

    void convertCommandLineSystemProperties(Map<String, String> systemProperties, StartParameter startParameter, Transformer<File, String> resolver) {
        startParameter.getSystemPropertiesArgs().putAll(systemProperties);
        if (systemProperties.containsKey(GRADLE_USER_HOME_PROPERTY_KEY)) {
            startParameter.setGradleUserHomeDir(resolver.transform(systemProperties.get(GRADLE_USER_HOME_PROPERTY_KEY)));
        }
    }

    public LayoutCommandLineConverter getLayoutConverter() {
        return layoutCommandLineConverter;
    }

    public SystemPropertiesCommandLineConverter getSystemPropertiesConverter() {
        return systemPropertiesCommandLineConverter;
    }
}
