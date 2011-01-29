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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.gradle.CacheUsage;
import org.gradle.CommandLineArgumentException;
import org.gradle.StartParameter;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.artifacts.ProjectDependenciesBuildInstruction;
import org.gradle.api.internal.file.BaseDirConverter;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.configuration.ImplicitTasksConfigurer;
import org.gradle.logging.LoggingConfiguration;
import org.gradle.logging.internal.LoggingCommandLineConverter;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Hans Dockter
 */
public class DefaultCommandLineConverter extends AbstractCommandLineConverter<StartParameter> {
    private static final String NO_SEARCH_UPWARDS = "u";
    private static final String PROJECT_DIR = "p";
    private static final String NO_PROJECT_DEPENDENCY_REBUILD = "a";
    private static final String BUILD_FILE = "b";
    public static final String INIT_SCRIPT = "I";
    private static final String SETTINGS_FILE = "c";
    private static final String TASKS = "t";
    private static final String PROPERTIES = "r";
    private static final String DEPENDENCIES = "n";
    public static final String FULL_STACKTRACE = "S";
    public static final String FULL_STACKTRACE_LONG = "full-stacktrace";
    public static final String STACKTRACE = "s";
    public static final String STACKTRACE_LONG = "stacktrace";
    private static final String SYSTEM_PROP = "D";
    private static final String PROJECT_PROP = "P";
    public static final String GRADLE_USER_HOME = "g";
    private static final String EMBEDDED_SCRIPT = "e";
    private static final String CACHE = "C";
    private static final String DRY_RUN = "m";
    private static final String NO_OPT = "no-opt";
    private static final String EXCLUDE_TASK = "x";
    private static final String PROFILE = "profile";

    private static BiMap<String, StartParameter.ShowStacktrace> showStacktraceMap = HashBiMap.create();
    private final CommandLineConverter<LoggingConfiguration> loggingConfigurationCommandLineConverter = new LoggingCommandLineConverter();

    //Initialize bi-directional maps so you can convert these back and forth from their command line options to their
    //object representation.

    static {
        showStacktraceMap.put(FULL_STACKTRACE, StartParameter.ShowStacktrace.ALWAYS_FULL);
        showStacktraceMap.put(STACKTRACE, StartParameter.ShowStacktrace.ALWAYS);
        //showStacktraceMap.put( , StartParameter.ShowStacktrace.INTERNAL_EXCEPTIONS ); there is no command argument for this. Rather, the lack of an argument means 'default to this'.
    }

    public void configure(CommandLineParser parser) {
        loggingConfigurationCommandLineConverter.configure(parser);
        parser.allowMixedSubcommandsAndOptions();
        parser.option(NO_SEARCH_UPWARDS, "no-search-upward").hasDescription(String.format("Don't search in parent folders for a %s file.", Settings.DEFAULT_SETTINGS_FILE));
        parser.option(CACHE, "cache").hasArgument().hasDescription("Specifies how compiled build scripts should be cached. Possible values are: 'rebuild' and 'on'. Default value is 'on'");
        parser.option(DRY_RUN, "dry-run").hasDescription("Runs the builds with all task actions disabled.");
        parser.option(STACKTRACE, STACKTRACE_LONG).hasDescription("Print out the stacktrace also for user exceptions (e.g. compile error).");
        parser.option(FULL_STACKTRACE, FULL_STACKTRACE_LONG).hasDescription("Print out the full (very verbose) stacktrace for any exceptions.");
        parser.option(TASKS, "tasks").mapsToSubcommand(ImplicitTasksConfigurer.TASKS_TASK).hasDescription("Show list of available tasks [deprecated - use 'gradle tasks' instead].");
        parser.option(PROPERTIES, "properties").mapsToSubcommand(ImplicitTasksConfigurer.PROPERTIES_TASK).hasDescription("Show list of all available project properties [deprecated - use 'gradle properties' instead].");
        parser.option(DEPENDENCIES, "dependencies").mapsToSubcommand(ImplicitTasksConfigurer.DEPENDENCIES_TASK).hasDescription("Show list of all project dependencies [deprecated - use 'gradle dependencies' instead].");
        parser.option(PROJECT_DIR, "project-dir").hasArgument().hasDescription("Specifies the start directory for Gradle. Defaults to current directory.");
        parser.option(GRADLE_USER_HOME, "gradle-user-home").hasArgument().hasDescription("Specifies the gradle user home directory.");
        parser.option(INIT_SCRIPT, "init-script").hasArguments().hasDescription("Specifies an initialization script.");
        parser.option(SETTINGS_FILE, "settings-file").hasArgument().hasDescription("Specifies the settings file.");
        parser.option(BUILD_FILE, "build-file").hasArgument().hasDescription("Specifies the build file.");
        parser.option(SYSTEM_PROP, "system-prop").hasArguments().hasDescription("Set system property of the JVM (e.g. -Dmyprop=myvalue).");
        parser.option(PROJECT_PROP, "project-prop").hasArguments().hasDescription("Set project property for the build script (e.g. -Pmyprop=myvalue).");
        parser.option(EMBEDDED_SCRIPT, "embedded").hasArgument().hasDescription("Specify an embedded build script.");
        parser.option(NO_PROJECT_DEPENDENCY_REBUILD, "no-rebuild").hasDescription("Do not rebuild project dependencies.");
        parser.option(NO_OPT).hasDescription("Ignore any task optimization.");
        parser.option(EXCLUDE_TASK, "exclude-task").hasArguments().hasDescription("Specify a task to be excluded from execution.");
        parser.option(PROFILE).hasDescription("Profiles build execution time and generates a report in the <build_dir>/reports/profile directory.");
    }

    @Override
    protected StartParameter newInstance() {
        return new StartParameter();
    }

    public StartParameter convert(ParsedCommandLine options, StartParameter startParameter) throws CommandLineArgumentException {
        LoggingConfiguration loggingConfiguration = loggingConfigurationCommandLineConverter.convert(options);
        startParameter.setLogLevel(loggingConfiguration.getLogLevel());
        startParameter.setColorOutput(loggingConfiguration.isColorOutput());
        FileResolver resolver = new BaseDirConverter(startParameter.getCurrentDir());

        for (String keyValueExpression : options.option(SYSTEM_PROP).getValues()) {
            String[] elements = keyValueExpression.split("=");
            startParameter.getSystemPropertiesArgs().put(elements[0], elements.length == 1 ? "" : elements[1]);
        }

        for (String keyValueExpression : options.option(PROJECT_PROP).getValues()) {
            String[] elements = keyValueExpression.split("=");
            startParameter.getProjectProperties().put(elements[0], elements.length == 1 ? "" : elements[1]);
        }

        if (options.hasOption(NO_SEARCH_UPWARDS)) {
            startParameter.setSearchUpwards(false);
        }

        if (options.hasOption(PROJECT_DIR)) {
            startParameter.setProjectDir(resolver.resolve(options.option(PROJECT_DIR).getValue()));
        }
        if (options.hasOption(GRADLE_USER_HOME)) {
            startParameter.setGradleUserHomeDir(resolver.resolve(options.option(GRADLE_USER_HOME).getValue()));
        }
        if (options.hasOption(BUILD_FILE)) {
            startParameter.setBuildFile(resolver.resolve(options.option(BUILD_FILE).getValue()));
        }
        if (options.hasOption(SETTINGS_FILE)) {
            startParameter.setSettingsFile(resolver.resolve(options.option(SETTINGS_FILE).getValue()));
        }

        for (String script : options.option(INIT_SCRIPT).getValues()) {
            startParameter.addInitScript(resolver.resolve(script));
        }

        if (options.hasOption(CACHE)) {
            try {
                startParameter.setCacheUsage(CacheUsage.fromString(options.option(CACHE).getValue()));
            } catch (InvalidUserDataException e) {
                throw new CommandLineArgumentException(e.getMessage());
            }
        }

        if (options.hasOption(EMBEDDED_SCRIPT)) {
            if (options.hasOption(BUILD_FILE) || options.hasOption(NO_SEARCH_UPWARDS) || options.hasOption(SETTINGS_FILE)) {
                System.err.println(String.format(
                        "Error: The -%s option can't be used together with the -%s, -%s or -%s options.",
                        EMBEDDED_SCRIPT, BUILD_FILE, SETTINGS_FILE, NO_SEARCH_UPWARDS));
                throw new CommandLineArgumentException(String.format(
                        "Error: The -%s option can't be used together with the -%s, -%s or -%s options.",
                        EMBEDDED_SCRIPT, BUILD_FILE, SETTINGS_FILE, NO_SEARCH_UPWARDS));
            }
            startParameter.useEmbeddedBuildFile(options.option(EMBEDDED_SCRIPT).getValue());
        }

        if (options.hasOption(FULL_STACKTRACE)) {
            if (options.hasOption(STACKTRACE)) {
                throw new CommandLineArgumentException(String.format(
                        "Error: The -%s option can't be used together with the -%s option.", FULL_STACKTRACE,
                        STACKTRACE));
            }
            startParameter.setShowStacktrace(StartParameter.ShowStacktrace.ALWAYS_FULL);
        } else if (options.hasOption(STACKTRACE)) {
            startParameter.setShowStacktrace(StartParameter.ShowStacktrace.ALWAYS);
        }

        if (options.hasOption(NO_PROJECT_DEPENDENCY_REBUILD)) {
            startParameter.setProjectDependenciesBuildInstruction(new ProjectDependenciesBuildInstruction(false));
        }

        if (!options.getExtraArguments().isEmpty()) {
            startParameter.setTaskNames(options.getExtraArguments());
        }

        if (options.hasOption(DRY_RUN)) {
            startParameter.setDryRun(true);
        }

        if (options.hasOption(NO_OPT)) {
            startParameter.setNoOpt(true);
        }

        if (options.hasOption(EXCLUDE_TASK)) {
            startParameter.setExcludedTaskNames(options.option(EXCLUDE_TASK).getValues());
        }

        if (options.hasOption(PROFILE)) {
            startParameter.setProfile(true);
        }

        return startParameter;
    }

    /**
     * This returns the stack trace level object represented by the command line argument
     *
     * @param commandLineArgument a single command line argument (with no '-')
     * @return the corresponding stack trace level or null if it doesn't match any.
     * @author mhunsicker
     */
    public StartParameter.ShowStacktrace getShowStacktrace(String commandLineArgument) {
        StartParameter.ShowStacktrace showStacktrace = showStacktraceMap.get(commandLineArgument);
        if (showStacktrace == null) {
            return null;
        }

        return showStacktrace;
    }

    /**
     * This returns the command line argument that represents the specified stack trace level.
     *
     * @param showStacktrace the stack trace level.
     * @return the command line argument or null if this level cannot be represented on the command line.
     * @author mhunsicker
     */
    public String getShowStacktraceCommandLine(StartParameter.ShowStacktrace showStacktrace) {
        String commandLine = showStacktraceMap.inverse().get(showStacktrace);
        if (commandLine == null) {
            return null;
        }

        return commandLine;
    }

    /**
     * This returns the ShowStacktrace levels that are supported on the command line.
     *
     * @return a collection of available ShowStacktrace levels
     * @author mhunsicker
     */
    public Collection<StartParameter.ShowStacktrace> getShowStacktrace() {
        return Collections.unmodifiableCollection(showStacktraceMap.values());
    }
}
