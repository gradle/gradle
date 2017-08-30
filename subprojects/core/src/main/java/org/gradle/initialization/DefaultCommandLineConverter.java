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
import org.gradle.initialization.option.BuildOption;
import org.gradle.internal.logging.LoggingCommandLineConverter;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.gradle.StartParameter.GRADLE_USER_HOME_PROPERTY_KEY;

public class DefaultCommandLineConverter extends AbstractCommandLineConverter<StartParameter> {
    private static final String NO_PROJECT_DEPENDENCY_REBUILD = "a";
    private static final String BUILD_FILE = "b";
    public static final String INIT_SCRIPT = "I";
    private static final String SETTINGS_FILE = "c";
    private static final String DRY_RUN = "m";
    private static final String EXCLUDE_TASK = "x";

    private static final String CONTINUOUS = "continuous";
    private static final String CONTINUOUS_SHORT_FLAG = "t";

    private static final String INCLUDE_BUILD = "include-build";

    private final CommandLineConverter<LoggingConfiguration> loggingConfigurationCommandLineConverter = new LoggingCommandLineConverter();
    private final CommandLineConverter<ParallelismConfiguration> parallelConfigurationCommandLineConverter = new ParallelismConfigurationCommandLineConverter();
    private final SystemPropertiesCommandLineConverter systemPropertiesCommandLineConverter = new SystemPropertiesCommandLineConverter();
    private final ProjectPropertiesCommandLineConverter projectPropertiesCommandLineConverter = new ProjectPropertiesCommandLineConverter();
    private List<BuildOption<StartParameter>> buildOptions = new StartParameterBuildOptionFactory().create();
    private final LayoutCommandLineConverter layoutCommandLineConverter;

    public DefaultCommandLineConverter() {
        layoutCommandLineConverter = new LayoutCommandLineConverter();
    }

    public void configure(CommandLineParser parser) {
        loggingConfigurationCommandLineConverter.configure(parser);
        parallelConfigurationCommandLineConverter.configure(parser);
        systemPropertiesCommandLineConverter.configure(parser);
        projectPropertiesCommandLineConverter.configure(parser);
        layoutCommandLineConverter.configure(parser);

        parser.allowMixedSubcommandsAndOptions();
        parser.option(DRY_RUN, "dry-run").hasDescription("Run the builds with all task actions disabled.");
        parser.option(INIT_SCRIPT, "init-script").hasArguments().hasDescription("Specify an initialization script.");
        parser.option(SETTINGS_FILE, "settings-file").hasArgument().hasDescription("Specify the settings file.");
        parser.option(BUILD_FILE, "build-file").hasArgument().hasDescription("Specify the build file.");
        parser.option(NO_PROJECT_DEPENDENCY_REBUILD, "no-rebuild").hasDescription("Do not rebuild project dependencies.");
        parser.option(EXCLUDE_TASK, "exclude-task").hasArguments().hasDescription("Specify a task to be excluded from execution.");
        parser.option(CONTINUOUS, CONTINUOUS_SHORT_FLAG).hasDescription("Enables continuous build. Gradle does not exit and will re-execute tasks when task file inputs change.").incubating();
        parser.option(INCLUDE_BUILD).hasArguments().hasDescription("Include the specified build in the composite.").incubating();

        for (BuildOption<StartParameter> option : buildOptions) {
            option.configure(parser);
        }
    }

    public StartParameter convert(final ParsedCommandLine options, final StartParameter startParameter) throws CommandLineArgumentException {
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

        if (options.hasOption(BUILD_FILE)) {
            startParameter.setBuildFile(resolver.transform(options.option(BUILD_FILE).getValue()));
        }
        if (options.hasOption(SETTINGS_FILE)) {
            startParameter.setSettingsFile(resolver.transform(options.option(SETTINGS_FILE).getValue()));
        }

        for (String script : options.option(INIT_SCRIPT).getValues()) {
            startParameter.addInitScript(resolver.transform(script));
        }

        if (options.hasOption(NO_PROJECT_DEPENDENCY_REBUILD)) {
            startParameter.setBuildProjectDependencies(false);
        }

        if (!options.getExtraArguments().isEmpty()) {
            startParameter.setTaskNames(options.getExtraArguments());
        }

        if (options.hasOption(DRY_RUN)) {
            startParameter.setDryRun(true);
        }

        if (options.hasOption(EXCLUDE_TASK)) {
            startParameter.setExcludedTaskNames(options.option(EXCLUDE_TASK).getValues());
        }

        if (options.hasOption(CONTINUOUS)) {
            startParameter.setContinuous(true);
        }

        for (String includedBuild : options.option(INCLUDE_BUILD).getValues()) {
            startParameter.includeBuild(resolver.transform(includedBuild));
        }

        for (BuildOption<StartParameter> option : buildOptions) {
            option.applyFromCommandLine(options, startParameter);
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
