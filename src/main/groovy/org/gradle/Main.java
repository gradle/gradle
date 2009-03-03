/*
 * Copyright 2007 the original author or authors.
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
package org.gradle;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.logging.LogLevel;
import org.gradle.execution.BuiltInTasksBuildExecuter;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

/**
 * @author Hans Dockter
 */
public class Main {
    private static Logger logger = LoggerFactory.getLogger(Main.class);

    public static final String GRADLE_HOME_PROPERTY_KEY = "gradle.home";
    public static final String GRADLE_USER_HOME_PROPERTY_KEY = "gradle.user.home";
    public static final String DEFAULT_GRADLE_USER_HOME = System.getProperty("user.home") + "/.gradle";
    public final static String DEFAULT_PLUGIN_PROPERTIES = "plugin.properties";
    public final static String IMPORTS_FILE_NAME = "gradle-imports";
    public final static String NL = System.getProperty("line.separator");

    private static final String NO_SEARCH_UPWARDS = "u";
    private static final String PROJECT_DIR = "p";
    private static final String PLUGIN_PROPERTIES_FILE = "l";
    private static final String DEFAULT_IMPORT_FILE = "K";
    private static final String BUILD_FILE = "b";
    private static final String SETTINGS_FILE = "c";
    private static final String TASKS = "t";
    private static final String PROPERTIES = "r";
    private static final String DEPENDENCIES = "n";
    public static final String DEBUG = "d";
    private static final String INFO = "i";
    private static final String QUIET = "q";
    public static final String FULL_STACKTRACE = "f";
    public static final String STACKTRACE = "s";
    private static final String SYSTEM_PROP = "D";
    private static final String PROJECT_PROP = "P";
    private static final String NO_DEFAULT_IMPORTS = "I";
    private static final String GRADLE_USER_HOME = "g";
    private static final String EMBEDDED_SCRIPT = "e";
    private static final String VERSION = "v";
    private static final String CACHE = "C";
    private static final String HELP = "h";

    private final String[] args;
    private BuildCompleter buildCompleter = new ProcessExitBuildCompleter();

    public Main(String[] args) {
        this.args = args;
    }

    public static void main(String[] args) throws Throwable {
        new Main(args).execute();
    }

    void setBuildCompleter(BuildCompleter buildCompleter) {
        this.buildCompleter = buildCompleter;
    }

    public void execute() throws Exception {
        BuildResultLogger resultLogger = new BuildResultLogger(logger);
        BuildExceptionReporter exceptionReporter = new BuildExceptionReporter(logger);

        StartParameter startParameter = new StartParameter();

        OptionParser parser = new OptionParser() {
            {
                acceptsAll(WrapUtil.toList(NO_DEFAULT_IMPORTS, "no-imports"), "Disable usage of default imports for build script files.");
                acceptsAll(WrapUtil.toList(NO_SEARCH_UPWARDS, "no-search-upward"),
                        String.format("Don't search in parent folders for a %s file.", Settings.DEFAULT_SETTINGS_FILE));
                acceptsAll(WrapUtil.toList(CACHE, "cache"),
                        "Specifies how compiled build scripts should be cached. Possible values are: 'rebuild', 'off', 'on'. Default value is 'on'").withRequiredArg().ofType(String.class);
                acceptsAll(WrapUtil.toList(VERSION, "version"), "Print version info.");
                acceptsAll(WrapUtil.toList(DEBUG, "debug"), "Log in debug mode (includes normal stacktrace).");
                acceptsAll(WrapUtil.toList(QUIET, "quiet"), "Log errors only.");
                acceptsAll(WrapUtil.toList(INFO, "info"), "Set log level to info.");
                acceptsAll(WrapUtil.toList(STACKTRACE, "stacktrace"), "Print out the stacktrace also for user exceptions (e.g. compile error).");
                acceptsAll(WrapUtil.toList(FULL_STACKTRACE, "full-stacktrace"), "Print out the full (very verbose) stacktrace for any exceptions.");
                acceptsAll(WrapUtil.toList(TASKS, "tasks"), "Show list of all available tasks and their dependencies.");
                acceptsAll(WrapUtil.toList(PROPERTIES, "properties"), "Show list of all available project properties.");
                acceptsAll(WrapUtil.toList(DEPENDENCIES, "dependencies"), "Show list of all project dependencies.");
                acceptsAll(WrapUtil.toList(PROJECT_DIR, "project-dir"), "Specifies the start directory for Gradle. Defaults to current directory.").withRequiredArg().ofType(String.class);
                acceptsAll(WrapUtil.toList(GRADLE_USER_HOME, "gradle-user-home"), "Specifies the gradle user home directory.").withRequiredArg().ofType(String.class);
                acceptsAll(WrapUtil.toList(PLUGIN_PROPERTIES_FILE, "plugin-properties-file"), "Specifies the plugin.properties file.").withRequiredArg().ofType(String.class);
                acceptsAll(WrapUtil.toList(DEFAULT_IMPORT_FILE, "default-import-file"), "Specifies the default import file.").withRequiredArg().ofType(String.class);
                acceptsAll(WrapUtil.toList(SETTINGS_FILE, "settings-file"), "Specifies the settings file.").withRequiredArg().ofType(String.class);
                acceptsAll(WrapUtil.toList(BUILD_FILE, "build-file"), "Specifies the build file.").withRequiredArg().ofType(String.class);
                acceptsAll(WrapUtil.toList(SYSTEM_PROP, "system-prop"), "Set system property of the JVM (e.g. -Dmyprop=myvalue).").withRequiredArg().ofType(String.class);
                acceptsAll(WrapUtil.toList(PROJECT_PROP, "project-prop"), "Set project property for the build script (e.g. -Pmyprop=myvalue).").withRequiredArg().ofType(String.class);
                acceptsAll(WrapUtil.toList(EMBEDDED_SCRIPT, "embedded"), "Specify an embedded build script.").withRequiredArg().ofType(String.class);
                acceptsAll(WrapUtil.toList(HELP, "?", "help"), "Shows this help message");
            }
        };

        OptionSet options = null;
        try {
            options = parser.parse(args);
        } catch (OptionException e) {
            System.err.println(e.getMessage());
            parser.printHelpOn(System.err);
            buildCompleter.exit(e);
        }

        exceptionReporter.setOptions(options);

        if (options.has(HELP)) {
            parser.printHelpOn(System.out);
            buildCompleter.exit(null);
        }

        if (options.has(VERSION)) {
            System.out.println(new GradleVersion().prettyPrint());
            buildCompleter.exit(null);
        }

        String gradleHome = System.getProperty(GRADLE_HOME_PROPERTY_KEY);
        if (!GUtil.isTrue(gradleHome)) {
            System.err.println("The gradle.home property is not set. Please set it and try again.");
            buildCompleter.exit(new InvalidUserDataException());
        }
        startParameter.setGradleHomeDir(new File(gradleHome));

        if (options.has(NO_DEFAULT_IMPORTS)) {
            startParameter.setDefaultImportsFile(null);
        } else if (options.has(DEFAULT_IMPORT_FILE)) {
            startParameter.setDefaultImportsFile(new File(options.argumentOf(DEFAULT_IMPORT_FILE)));
        }

        if (options.has(SYSTEM_PROP)) {
            List<String> props = options.argumentsOf(SYSTEM_PROP);
            for (String keyValueExpression : props) {
                String[] elements = keyValueExpression.split("=");
                startParameter.getSystemPropertiesArgs().put(elements[0], elements.length == 1 ? "" : elements[1]);
            }
        }

        if (options.has(PROJECT_PROP)) {
            List<String> props = options.argumentsOf(PROJECT_PROP);
            for (String keyValueExpression : props) {
                String[] elements = keyValueExpression.split("=");
                startParameter.getProjectProperties().put(elements[0], elements.length == 1 ? "" : elements[1]);
            }
        }

        startParameter.setSearchUpwards(!options.has(NO_SEARCH_UPWARDS));

        if (options.has(PROJECT_DIR)) {
            startParameter.setCurrentDir(new File(options.argumentOf(PROJECT_DIR)));
            if (!startParameter.getCurrentDir().isDirectory()) {
                System.err.println("Error: Directory " + startParameter.getCurrentDir().getCanonicalFile() + " does not exist!");
                buildCompleter.exit(new InvalidUserDataException());
            }
        }

        if (options.hasArgument(GRADLE_USER_HOME)) {
            startParameter.setGradleUserHomeDir(new File(options.argumentOf(GRADLE_USER_HOME)));
        }
        if (options.hasArgument(BUILD_FILE)) {
            startParameter.setBuildFile(new File(options.argumentOf(BUILD_FILE)));
        }
        if (options.hasArgument(SETTINGS_FILE)) {
            startParameter.setSettingsFile(new File(options.argumentOf(SETTINGS_FILE)));
        }
        if (options.hasArgument(PLUGIN_PROPERTIES_FILE)) {
            startParameter.setPluginPropertiesFile(new File(options.argumentOf(PLUGIN_PROPERTIES_FILE)));
        }

        if (options.has(CACHE)) {
            try {
                startParameter.setCacheUsage(CacheUsage.fromString(options.valueOf(CACHE).toString()));
            } catch (InvalidUserDataException e) {
                System.err.println(e.getMessage());
                buildCompleter.exit(e);
            }
        }

        if (options.has(EMBEDDED_SCRIPT)) {
            if (options.has(BUILD_FILE) || options.has(NO_SEARCH_UPWARDS) || options.has(SETTINGS_FILE)) {
                System.err.println(String.format("Error: The -%s option can't be used together with the -%s, -%s or -%s options.",
                        EMBEDDED_SCRIPT, BUILD_FILE, SETTINGS_FILE, NO_SEARCH_UPWARDS));
                buildCompleter.exit(new InvalidUserDataException());
            }
            startParameter.useEmbeddedBuildFile(options.argumentOf(EMBEDDED_SCRIPT));
        }

        if (options.has(TASKS) && options.has(PROPERTIES)) {
            System.err.println(String.format("Error: The -%s and -%s options cannot be used together.", TASKS, PROPERTIES));
            buildCompleter.exit(new InvalidUserDataException());
        }
        if (options.has(TASKS)) {
            startParameter.setBuildExecuter(new BuiltInTasksBuildExecuter(BuiltInTasksBuildExecuter.Options.TASKS));
        } else if (options.has(PROPERTIES)) {
            startParameter.setBuildExecuter(new BuiltInTasksBuildExecuter(BuiltInTasksBuildExecuter.Options.PROPERTIES));
        } else if (options.has(DEPENDENCIES)) {
            startParameter.setBuildExecuter(new BuiltInTasksBuildExecuter(BuiltInTasksBuildExecuter.Options.DEPENDENCIES));
        } else {
            startParameter.setTaskNames(options.nonOptionArguments());
        }

        startParameter.setLogLevel(getLogLevel(options));

        try {
            Gradle gradle = Gradle.newInstance(startParameter);

            gradle.addBuildListener(exceptionReporter);
            gradle.addBuildListener(resultLogger);

            BuildResult buildResult = gradle.run();
            if (buildResult.getFailure() != null) {
                buildCompleter.exit(buildResult.getFailure());
            }
        } catch (Throwable e) {
            exceptionReporter.buildFinished(new BuildResult(null, e));
            buildCompleter.exit(e);
        }
        buildCompleter.exit(null);
    }

    private static LogLevel getLogLevel(OptionSet options) throws Exception {
        LogLevel logLevel = null;
        if (options.has(QUIET)) {
            logLevel = LogLevel.QUIET;
        }
        if (options.has(INFO)) {
            quitWithErrorIfLogLevelAlreadyDefined(logLevel, INFO);
            logLevel = LogLevel.INFO;
        }
        if (options.has(DEBUG)) {
            quitWithErrorIfLogLevelAlreadyDefined(logLevel, DEBUG);
            logLevel = LogLevel.DEBUG;
        }
        if (logLevel == null) {
            logLevel = LogLevel.LIFECYCLE;
        }
        return logLevel;
    }

    private static void quitWithErrorIfLogLevelAlreadyDefined(LogLevel logLevel, String option) {
        if (logLevel != null) {
            System.err.println(String.format("Error: The log level is already defined by another option. Therefore the option %s is invalid.",
                    option));
            throw new InvalidUserDataException();
        }
    }

    public interface BuildCompleter {
        void exit(Throwable failure);
    }

    private static class ProcessExitBuildCompleter implements BuildCompleter {
        public void exit(Throwable failure) {
            System.exit(failure == null ? 0 : 1);
        }
    }
}
