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
import org.gradle.cli.*;
import org.gradle.logging.LoggingConfiguration;
import org.gradle.logging.internal.LoggingCommandLineConverter;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.gradle.StartParameter.GRADLE_USER_HOME_PROPERTY_KEY;

public class DefaultCommandLineConverter extends AbstractCommandLineConverter<StartParameter> {
    private static final String NO_PROJECT_DEPENDENCY_REBUILD = "a";
    private static final String BUILD_FILE = "b";
    public static final String INIT_SCRIPT = "I";
    private static final String SETTINGS_FILE = "c";
    private static final String DRY_RUN = "m";
    private static final String RERUN_TASKS = "rerun-tasks";
    private static final String EXCLUDE_TASK = "x";
    private static final String PROFILE = "profile";
    private static final String CONTINUE = "continue";
    private static final String OFFLINE = "offline";
    private static final String REFRESH_DEPENDENCIES = "refresh-dependencies";
    private static final String PROJECT_CACHE_DIR = "project-cache-dir";
    private static final String RECOMPILE_SCRIPTS = "recompile-scripts";

    private static final String PARALLEL = "parallel";
    private static final String PARALLEL_THREADS = "parallel-threads";

    private static final String CONFIGURE_ON_DEMAND = "configure-on-demand";

    private final CommandLineConverter<LoggingConfiguration> loggingConfigurationCommandLineConverter = new LoggingCommandLineConverter();
    private final SystemPropertiesCommandLineConverter systemPropertiesCommandLineConverter = new SystemPropertiesCommandLineConverter();
    private final ProjectPropertiesCommandLineConverter projectPropertiesCommandLineConverter = new ProjectPropertiesCommandLineConverter();
    private final LayoutCommandLineConverter layoutCommandLineConverter;

    public DefaultCommandLineConverter() {
        layoutCommandLineConverter = new LayoutCommandLineConverter();
    }

    public void configure(CommandLineParser parser) {
        loggingConfigurationCommandLineConverter.configure(parser);
        systemPropertiesCommandLineConverter.configure(parser);
        projectPropertiesCommandLineConverter.configure(parser);
        layoutCommandLineConverter.configure(parser);

        parser.allowMixedSubcommandsAndOptions();
        parser.option(PROJECT_CACHE_DIR).hasArgument().hasDescription("Specifies the project-specific cache directory. Defaults to .gradle in the root project directory.");
        parser.option(DRY_RUN, "dry-run").hasDescription("Runs the builds with all task actions disabled.");
        parser.option(INIT_SCRIPT, "init-script").hasArguments().hasDescription("Specifies an initialization script.");
        parser.option(SETTINGS_FILE, "settings-file").hasArgument().hasDescription("Specifies the settings file.");
        parser.option(BUILD_FILE, "build-file").hasArgument().hasDescription("Specifies the build file.");
        parser.option(NO_PROJECT_DEPENDENCY_REBUILD, "no-rebuild").hasDescription("Do not rebuild project dependencies.");
        parser.option(RERUN_TASKS).hasDescription("Ignore previously cached task results.");
        parser.option(RECOMPILE_SCRIPTS).hasDescription("Force build script recompiling.");
        parser.option(EXCLUDE_TASK, "exclude-task").hasArguments().hasDescription("Specify a task to be excluded from execution.");
        parser.option(PROFILE).hasDescription("Profiles build execution time and generates a report in the <build_dir>/reports/profile directory.");
        parser.option(CONTINUE).hasDescription("Continues task execution after a task failure.");
        parser.option(OFFLINE).hasDescription("The build should operate without accessing network resources.");
        parser.option(REFRESH_DEPENDENCIES).hasDescription("Refresh the state of dependencies.");
        parser.option(PARALLEL).hasDescription("Build projects in parallel. Gradle will attempt to determine the optimal number of executor threads to use.").incubating();
        parser.option(PARALLEL_THREADS).hasArgument().hasDescription("Build projects in parallel, using the specified number of executor threads.").incubating();
        parser.option(CONFIGURE_ON_DEMAND).hasDescription("Only relevant projects are configured in this build run. This means faster build for large multi-project builds.").incubating();
    }

    public StartParameter convert(final ParsedCommandLine options, final StartParameter startParameter) throws CommandLineArgumentException {
        loggingConfigurationCommandLineConverter.convert(options, startParameter);
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

        if (options.hasOption(PROJECT_CACHE_DIR)) {
            startParameter.setProjectCacheDir(resolver.transform(options.option(PROJECT_CACHE_DIR).getValue()));
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

        if (options.hasOption(RERUN_TASKS)) {
            startParameter.setRerunTasks(true);
        }

        if (options.hasOption(RECOMPILE_SCRIPTS)) {
            startParameter.setRecompileScripts(true);
        }

        if (options.hasOption(EXCLUDE_TASK)) {
            startParameter.setExcludedTaskNames(options.option(EXCLUDE_TASK).getValues());
        }

        if (options.hasOption(PROFILE)) {
            startParameter.setProfile(true);
        }

        if (options.hasOption(CONTINUE)) {
            startParameter.setContinueOnFailure(true);
        }

        if (options.hasOption(OFFLINE)) {
            startParameter.setOffline(true);
        }

        if (options.hasOption(REFRESH_DEPENDENCIES)) {
            startParameter.setRefreshDependencies(true);
        }

        if (options.hasOption(PARALLEL)) {
            startParameter.setParallelThreadCount(-1);
        }

        if (options.hasOption(PARALLEL_THREADS)) {
            try {
                int parallelThreads = Integer.parseInt(options.option(PARALLEL_THREADS).getValue());
                startParameter.setParallelThreadCount(parallelThreads);
            } catch (NumberFormatException e) {
                throw new CommandLineArgumentException(String.format("Not a numeric argument for %s", PARALLEL_THREADS));
            }
        }

        if (options.hasOption(CONFIGURE_ON_DEMAND)) {
            startParameter.setConfigureOnDemand(true);
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
