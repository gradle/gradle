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
import org.gradle.api.logging.LogLevel;
import org.gradle.configuration.ImplicitTasksConfigurer;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Hans Dockter
 */
public class DefaultCommandLine2StartParameterConverter implements CommandLine2StartParameterConverter {
    private static final String NO_SEARCH_UPWARDS = "u";
    private static final String PROJECT_DIR = "p";
    private static final String PROJECT_DEPENDENCY_TASK_NAMES = "A";
    private static final String NO_PROJECT_DEPENDENCY_REBUILD = "a";
    private static final String BUILD_FILE = "b";
    public static final String INIT_SCRIPT = "I";
    private static final String SETTINGS_FILE = "c";
    public static final String TASKS = "t";
    private static final String PROPERTIES = "r";
    private static final String DEPENDENCIES = "n";
    public static final String DEBUG = "d";
    public static final String INFO = "i";
    public static final String QUIET = "q";
    public static final String NO_COLOR = "no-color";
    public static final String FULL_STACKTRACE = "S";
    public static final String STACKTRACE = "s";
    private static final String SYSTEM_PROP = "D";
    private static final String PROJECT_PROP = "P";
    private static final String GRADLE_USER_HOME = "g";
    private static final String EMBEDDED_SCRIPT = "e";
    private static final String VERSION = "v";
    private static final String CACHE = "C";
    private static final String DRY_RUN = "m";
    private static final String NO_OPT = "no-opt";
    private static final String EXCLUDE_TASK = "x";
    private static final String HELP = "h";
    private static final String GUI = "gui";
    private static final String PROFILE = "profile";

    private final CommandLineParser parser = new CommandLineParser() {{
        allowMixedSubcommandsAndOptions();
        option(NO_SEARCH_UPWARDS, "no-search-upward").hasDescription(String.format("Don't search in parent folders for a %s file.", Settings.DEFAULT_SETTINGS_FILE));
        option(CACHE, "cache").hasArgument().hasDescription("Specifies how compiled build scripts should be cached. Possible values are: 'rebuild' and 'on'. Default value is 'on'");
        option(VERSION, "version").hasDescription("Print version info.");
        option(DEBUG, "debug").hasDescription("Log in debug mode (includes normal stacktrace).");
        option(QUIET, "quiet").hasDescription("Log errors only.");
        option(DRY_RUN, "dry-run").hasDescription("Runs the builds with all task actions disabled.");
        option(INFO, "info").hasDescription("Set log level to info.");
        option(STACKTRACE, "stacktrace").hasDescription("Print out the stacktrace also for user exceptions (e.g. compile error).");
        option(FULL_STACKTRACE, "full-stacktrace").hasDescription("Print out the full (very verbose) stacktrace for any exceptions.");
        option(TASKS, "tasks").mapsToSubcommand(ImplicitTasksConfigurer.TASKS_TASK).hasDescription("Show list of available tasks.");
        option(PROPERTIES, "properties").mapsToSubcommand(ImplicitTasksConfigurer.PROPERTIES_TASK).hasDescription("Show list of all available project properties.");
        option(DEPENDENCIES, "dependencies").mapsToSubcommand(ImplicitTasksConfigurer.DEPENDENCIES_TASK).hasDescription("Show list of all project dependencies.");
        option(GUI).hasDescription("Launches a GUI application");
        option(PROJECT_DIR, "project-dir").hasArgument().hasDescription("Specifies the start directory for Gradle. Defaults to current directory.");
        option(GRADLE_USER_HOME, "gradle-user-home").hasArgument().hasDescription("Specifies the gradle user home directory.");
        option(INIT_SCRIPT, "init-script").hasArguments().hasDescription("Specifies an initialization script.");
        option(SETTINGS_FILE, "settings-file").hasArgument().hasDescription("Specifies the settings file.");
        option(BUILD_FILE, "build-file").hasArgument().hasDescription("Specifies the build file.");
        option(SYSTEM_PROP, "system-prop").hasArguments().hasDescription("Set system property of the JVM (e.g. -Dmyprop=myvalue).");
        option(PROJECT_PROP, "project-prop").hasArguments().hasDescription("Set project property for the build script (e.g. -Pmyprop=myvalue).");
        option(EMBEDDED_SCRIPT, "embedded").hasArgument().hasDescription("Specify an embedded build script.");
        option(PROJECT_DEPENDENCY_TASK_NAMES, "dep-tasks").hasArguments().hasDescription("Specify additional tasks for building project dependencies.");
        option(NO_PROJECT_DEPENDENCY_REBUILD, "no-rebuild").hasDescription("Do not rebuild project dependencies.");
        option(NO_OPT).hasDescription("Ignore any task optimization.");
        option(EXCLUDE_TASK, "exclude-task").hasArguments().hasDescription("Specify a task to be excluded from execution.");
        option(NO_COLOR).hasDescription("Do not use color in the console output.");
        option(PROFILE).hasDescription("Profiles build execution time and generates a report in the <build_dir>/reports/profile directory.");
        option(HELP, "?", "help").hasDescription("Shows this help message");
    }};

    private static BiMap<String, LogLevel> logLevelMap = HashBiMap.create();
    private static BiMap<String, StartParameter.ShowStacktrace> showStacktraceMap = HashBiMap.create();

    //Initialize bi-directional maps so you can convert these back and forth from their command line options to their
    //object representation.

    static {
        logLevelMap.put(QUIET, LogLevel.QUIET);
        logLevelMap.put(INFO, LogLevel.INFO);
        logLevelMap.put(DEBUG, LogLevel.DEBUG);
        logLevelMap.put("", LogLevel.LIFECYCLE);
        //there are also other log levels that gradle doesn't support command-line-wise.

        showStacktraceMap.put(FULL_STACKTRACE, StartParameter.ShowStacktrace.ALWAYS_FULL);
        showStacktraceMap.put(STACKTRACE, StartParameter.ShowStacktrace.ALWAYS);
        //showStacktraceMap.put( , StartParameter.ShowStacktrace.INTERNAL_EXCEPTIONS ); there is no command argument for this. Rather, the lack of an argument means 'default to this'.
    }

    public StartParameter convert(String[] args) {
        StartParameter startParameter = new StartParameter();
        convert(args, startParameter);
        return startParameter;
    }

    public void convert(String[] args, StartParameter startParameter) {
        ParsedCommandLine options = parser.parse(args);

        if (options.hasOption(HELP)) {
            startParameter.setShowHelp(true);
            return;
        }

        if (options.hasOption(VERSION)) {
            startParameter.setShowVersion(true);
            return;
        }

        if (options.hasOption(GUI)) {
            startParameter.setLaunchGUI(true);
        }

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
            startParameter.setProjectDir(new File(options.option(PROJECT_DIR).getValue()));
        }
        if (options.hasOption(GRADLE_USER_HOME)) {
            startParameter.setGradleUserHomeDir(new File(options.option(GRADLE_USER_HOME).getValue()));
        }
        if (options.hasOption(BUILD_FILE)) {
            startParameter.setBuildFile(new File(options.option(BUILD_FILE).getValue()));
        }
        if (options.hasOption(SETTINGS_FILE)) {
            startParameter.setSettingsFile(new File(options.option(SETTINGS_FILE).getValue()));
        }

        for (String script : options.option(INIT_SCRIPT).getValues()) {
            startParameter.addInitScript(new File(script));
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

        if (options.hasOption(PROJECT_DEPENDENCY_TASK_NAMES) && options.hasOption(NO_PROJECT_DEPENDENCY_REBUILD)) {
            throw new CommandLineArgumentException(String.format(
                    "Error: The -%s and -%s options cannot be used together.", PROJECT_DEPENDENCY_TASK_NAMES,
                    NO_PROJECT_DEPENDENCY_REBUILD));
        } else if (options.hasOption(NO_PROJECT_DEPENDENCY_REBUILD)) {
            startParameter.setProjectDependenciesBuildInstruction(new ProjectDependenciesBuildInstruction(null));
        } else if (options.hasOption(PROJECT_DEPENDENCY_TASK_NAMES)) {
            List<String> normalizedTaskNames = new ArrayList<String>();
            for (String taskName : options.option(PROJECT_DEPENDENCY_TASK_NAMES).getValues()) {
                normalizedTaskNames.add(taskName);
            }
            startParameter.setProjectDependenciesBuildInstruction(new ProjectDependenciesBuildInstruction(
                    normalizedTaskNames));
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

        startParameter.setLogLevel(getLogLevel(options));
        if (options.hasOption(NO_COLOR)) {
            startParameter.setColorOutput(false);
        }

        if (options.hasOption(PROFILE)) {
            startParameter.setProfile(true);
        }
    }

    public void showHelp(OutputStream out) {
        parser.printUsage(out);
    }

    private LogLevel getLogLevel(ParsedCommandLine options) {
        LogLevel logLevel = null;
        if (options.hasOption(QUIET)) {
            logLevel = LogLevel.QUIET;
        }
        if (options.hasOption(INFO)) {
            quitWithErrorIfLogLevelAlreadyDefined(logLevel, INFO);
            logLevel = LogLevel.INFO;
        }
        if (options.hasOption(DEBUG)) {
            quitWithErrorIfLogLevelAlreadyDefined(logLevel, DEBUG);
            logLevel = LogLevel.DEBUG;
        }
        if (logLevel == null) {
            logLevel = LogLevel.LIFECYCLE;
        }
        return logLevel;
    }

    /*
       This returns the log level object represented by the command line argument
       @param  commandLineArgument a single command line argument (with no '-')
       @return the corresponding log level or null if it doesn't match any.
       @author mhunsicker
    */

    public LogLevel getLogLevel(String commandLineArgument) {
        LogLevel logLevel = logLevelMap.get(commandLineArgument);
        if (logLevel == null) {
            return null;
        }

        return logLevel;
    }

    /*
       This returns the command line argument that represents the specified
       log level.
       @param  logLevel       the log level.
       @return the command line argument or null if this level cannot be
                represented on the command line.
       @author mhunsicker
    */

    public String getLogLevelCommandLine(LogLevel logLevel) {
        String commandLine = logLevelMap.inverse().get(logLevel);
        if (commandLine == null) {
            return null;
        }

        return commandLine;
    }

    /*
       This returns the log levels that are supported on the command line.
       @return a collection of available log levels
       @author mhunsicker
    */

    public Collection<LogLevel> getLogLevels() {
        return Collections.unmodifiableCollection(logLevelMap.values());
    }

    /*
       This returns the stack trace level object represented by the command
       line argument
       @param  commandLineArgument a single command line argument (with no '-')
       @return the corresponding stack trace level or null if it doesn't match any.
       @author mhunsicker
    */

    public StartParameter.ShowStacktrace getShowStacktrace(String commandLineArgument) {
        StartParameter.ShowStacktrace showStacktrace = showStacktraceMap.get(commandLineArgument);
        if (showStacktrace == null) {
            return null;
        }

        return showStacktrace;
    }

    /*
       This returns the command line argument that represents the specified
       stack trace level.

       @param  showStacktrace the stack trace level.
       @return the command line argument or null if this level cannot be
                represented on the command line.
       @author mhunsicker
    */

    public String getShowStacktraceCommandLine(StartParameter.ShowStacktrace showStacktrace) {
        String commandLine = showStacktraceMap.inverse().get(showStacktrace);
        if (commandLine == null) {
            return null;
        }

        return commandLine;
    }

    /*
       This returns the ShowStacktrace levels that are supported on the command
       line.
       @return a collection of available ShowStacktrace levels
       @author mhunsicker
    */

    public Collection<StartParameter.ShowStacktrace> getShowStacktrace() {
        return Collections.unmodifiableCollection(showStacktraceMap.values());
    }

    private void quitWithErrorIfLogLevelAlreadyDefined(LogLevel logLevel, String option) {
        if (logLevel != null) {
            System.err.println(String.format(
                    "Error: The log level is already defined by another option. Therefore the option %s is invalid.",
                    option));
            throw new InvalidUserDataException();
        }
    }
}
