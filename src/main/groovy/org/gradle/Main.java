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

import ch.qos.logback.classic.Level;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.ivy.util.DefaultMessageLogger;
import org.apache.ivy.util.Message;
import org.gradle.api.InvalidUserDataException;
import org.gradle.execution.BuiltInTaskExecuter;
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
// todo the main method is too long. Extract methods.
public class Main {
    private static Logger logger = LoggerFactory.getLogger(Main.class);

    public static final String GRADLE_HOME_PROPERTY_KEY = "gradle.home";
    public static final String GRADLE_USER_HOME_PROPERTY_KEY = "gradle.user.home";
    public static final String DEFAULT_GRADLE_USER_HOME = System.getProperty("user.home") + "/.gradle";
    public final static String DEFAULT_PLUGIN_PROPERTIES = "plugin.properties";
    public final static String IMPORTS_FILE_NAME = "gradle-imports";
    public final static String NL = System.getProperty("line.separator");

    private static final String NO_JVM_TERMINATION = "S";
    private static final String BOOTSTRAP_DEBUG_INFO = "B";
    private static final String NO_SEARCH_UPWARDS = "u";
    private static final String PROJECT_DIR = "p";
    private static final String PLUGIN_PROPERTIES_FILE = "l";
    private static final String BUILD_FILE = "b";
    private static final String SETTINGS_FILE = "c";
    private static final String TASKS = "t";
    public static final String DEBUG = "d";
    private static final String IVY_QUIET = "i";
    private static final String IVY_DEBUG = "j";
    private static final String QUIET = "q";
    public static final String FULL_STACKTRACE = "f";
    public static final String STACKTRACE = "s";
    private static final String SYSTEM_PROP = "D";
    private static final String PROJECT_PROP = "P";
    private static final String NO_DEFAULT_IMPORTS = "I";
    private static final String GRADLE_USER_HOME = "g";
    private static final String EMBEDDED_SCRIPT = "e";
    private static final String VERSION = "v";
    private static final String CACHE_OFF = "x";
    private static final String REBUILD_CACHE = "r";
    private static final String HELP = "h";


    public static void main(String[] args) throws Throwable {
        BuildResultLogger resultLogger = new BuildResultLogger(logger);
        BuildExceptionReporter exceptionReporter = new BuildExceptionReporter(logger);

        String gradleHome = System.getProperty(GRADLE_HOME_PROPERTY_KEY);
        StartParameter startParameter = new StartParameter();

        OptionParser parser = new OptionParser() {
            {
                acceptsAll(WrapUtil.toList(NO_JVM_TERMINATION), "Don't trigger a System.exit(0) for normal termination. Used for Gradle's internal testing.");
                acceptsAll(WrapUtil.toList(NO_DEFAULT_IMPORTS, "no-imports"), "Disable usage of default imports for build script files.");
                acceptsAll(WrapUtil.toList(NO_SEARCH_UPWARDS, "no-search-upward"), "Don't search in parent folders for a settings.gradle file.");
                acceptsAll(WrapUtil.toList(CACHE_OFF, "cache-off"), "No caching of compiled build scripts.");
                acceptsAll(WrapUtil.toList(REBUILD_CACHE, "rebuild-cache"), "Rebuild the cache of compiled build scripts.");
                acceptsAll(WrapUtil.toList(VERSION, "version"), "Print version info.");
                acceptsAll(WrapUtil.toList(DEBUG, "debug"), "Log in debug mode (includes normal stacktrace).");
                acceptsAll(WrapUtil.toList(QUIET, "quiet"), "Log errors only.");
                acceptsAll(WrapUtil.toList(IVY_DEBUG, "ivy-debug"), "Set Ivy log level to debug (very verbose).");
                acceptsAll(WrapUtil.toList(IVY_QUIET, "ivy-quiet"), "Set Ivy log level to quiet.");
                acceptsAll(WrapUtil.toList(STACKTRACE, "stacktrace"), "Print out the stacktrace also for user exceptions (e.g. compile error).");
                acceptsAll(WrapUtil.toList(FULL_STACKTRACE, "full-stacktrace"), "Print out the full (very verbose) stacktrace for any exceptions.");
                acceptsAll(WrapUtil.toList(TASKS, "tasks"), "Show list of all available tasks and their dependencies.");
                acceptsAll(WrapUtil.toList(PROJECT_DIR, "project-dir"), "Specifies the start dir for Gradle. Defaults to current dir.").withRequiredArg().ofType(String.class);
                acceptsAll(WrapUtil.toList(GRADLE_USER_HOME, "gradle-user-home"), "Specifies the gradle user home dir.").withRequiredArg().ofType(String.class);
                acceptsAll(WrapUtil.toList(PLUGIN_PROPERTIES_FILE, "plugin-properties-file"), "Specifies the plugin.properties file.").withRequiredArg().ofType(String.class);
                acceptsAll(WrapUtil.toList(SETTINGS_FILE, "settingsfile"), "Specifies the settings file name. Defaults to settings.gradle.").withRequiredArg().ofType(String.class);
                acceptsAll(WrapUtil.toList(BUILD_FILE, "buildfile"), "Specifies the build file name (also for subprojects). Defaults to build.gradle.").withRequiredArg().ofType(String.class);
                acceptsAll(WrapUtil.toList(SYSTEM_PROP, "systemprop"), "Set system property of the JVM (e.g. -Dmyprop=myvalue).").withRequiredArg().ofType(String.class);
                acceptsAll(WrapUtil.toList(PROJECT_PROP, "projectprop"), "Set project property for the build script (e.g. -Pmyprop=myvalue).").withRequiredArg().ofType(String.class);
                acceptsAll(WrapUtil.toList(EMBEDDED_SCRIPT, "embedded"), "Specify an embedded build script.").withRequiredArg().ofType(String.class);
                acceptsAll(WrapUtil.toList(BOOTSTRAP_DEBUG_INFO, "bootstrap-debug"), "Specify a text to be logged at the beginning (e.g. used by Gradle's bootstrap class.").withRequiredArg().ofType(String.class);
                acceptsAll(WrapUtil.toList(HELP, "?"), "Shows this help message");
            }
        };

        OptionSet options;
        try {
            options = parser.parse(args);
        } catch (OptionException e) {
            parser.printHelpOn(System.err);
            System.err.println("====");
            return;
        }

        exceptionReporter.setOptions(options);

        if (options.has(HELP)) {
            parser.printHelpOn(System.out);
            exitWithSuccess(options);
            return;
        }

        configureLogger(options);

        if (options.hasArgument(BOOTSTRAP_DEBUG_INFO)) {
            logger.debug(options.argumentOf(BOOTSTRAP_DEBUG_INFO));
        }

        if (options.has(VERSION)) {
            System.out.println(new GradleVersion().prettyPrint());
            exitWithSuccess(options);
            return;
        }

        if (!GUtil.isTrue(gradleHome)) {
            logger.error("The gradle.home property is not set. Please set it and try again.");
            exitWithError(options, new InvalidUserDataException());
            return;
        }

        startParameter.setDefaultImportsFile(
                options.has(NO_DEFAULT_IMPORTS) ? null : new File(gradleHome + '/' + IMPORTS_FILE_NAME));

        if (options.has(SYSTEM_PROP)) {
            List<String> props = options.argumentsOf(SYSTEM_PROP);
            logger.info("Running with System props: " + props);
            for (String keyValueExpression : props) {
                String[] elements = keyValueExpression.split("=");
                startParameter.getSystemPropertiesArgs().put(elements[0], elements.length == 1 ? "" : elements[1]);
            }
        }

        if (options.has(PROJECT_PROP)) {
            List<String> props = options.argumentsOf(PROJECT_PROP);
            logger.info("Running with Project props: " + props);
            for (String keyValueExpression : props) {
                String[] elements = keyValueExpression.split("=");
                startParameter.getProjectProperties().put(elements[0], elements.length == 1 ? "" : elements[1]);
            }
        }

        startParameter.setSearchUpwards(!options.has(NO_SEARCH_UPWARDS));

        if (options.has(PROJECT_DIR)) {
            startParameter.setCurrentDir(new File(options.argumentOf(PROJECT_DIR)));
            if (!startParameter.getCurrentDir().isDirectory()) {
                logger.error("Error: Directory " + startParameter.getCurrentDir().getCanonicalFile() + " does not exist!");
                exitWithError(options, new InvalidUserDataException());
                return;
            }
        } else {
            startParameter.setCurrentDir(new File(System.getProperty("user.dir")));
        }

        startParameter.setGradleUserHomeDir(
                options.hasArgument(GRADLE_USER_HOME) ?
                        new File(options.argumentOf(GRADLE_USER_HOME)) : new File(DEFAULT_GRADLE_USER_HOME));

        if (options.hasArgument(BUILD_FILE)) {
            startParameter.setBuildFileName(options.argumentOf(BUILD_FILE));
        }
        if (options.hasArgument(SETTINGS_FILE)) {
            startParameter.setSettingsFileName(options.argumentOf(SETTINGS_FILE));
        }

        startParameter.setPluginPropertiesFile(
                options.hasArgument(PLUGIN_PROPERTIES_FILE) ? new File(options.argumentOf(PLUGIN_PROPERTIES_FILE)) :
                        new File(gradleHome + '/' + DEFAULT_PLUGIN_PROPERTIES));

        if (options.has(CACHE_OFF)) {
            if (options.has(REBUILD_CACHE)) {
                logger.error(String.format("Error: The -%s option can't be used together with the -%s option.",
                        CACHE_OFF, REBUILD_CACHE));
                exitWithError(options, new InvalidUserDataException());
            }
            startParameter.setCacheUsage(CacheUsage.OFF);
        } else if (options.has(REBUILD_CACHE)) {
            startParameter.setCacheUsage(CacheUsage.REBUILD);
        } else {
            startParameter.setCacheUsage(CacheUsage.ON);
        }

        if (options.has(EMBEDDED_SCRIPT)) {
            if (options.has(BUILD_FILE) || options.has(NO_SEARCH_UPWARDS) || options.has(SETTINGS_FILE)) {
                logger.error(String.format("Error: The -%s option can't be used together with the -%s, -%s or -%s options.",
                        EMBEDDED_SCRIPT, BUILD_FILE, SETTINGS_FILE, NO_SEARCH_UPWARDS));
                exitWithError(options, new InvalidUserDataException());
                return;
            }
            startParameter.useEmbeddedBuildFile(options.argumentOf(EMBEDDED_SCRIPT));
        }

        logger.debug("gradle.home= " + gradleHome);
        logger.debug("Project dir: " + startParameter.getCurrentDir());
        logger.debug("Gradle user home: " + startParameter.getGradleUserHomeDir());
        logger.info("Buildfilename: " + startParameter.getBuildFileName());
        logger.debug("Plugin properties: " + startParameter.getPluginPropertiesFile());
        logger.debug("Default imports file: " + startParameter.getDefaultImportsFile());

        if (options.has(TASKS)) {
            startParameter.setTaskExecuter(new BuiltInTaskExecuter());
        } else {
            startParameter.setTaskNames(options.nonOptionArguments());
        }

        try {
            Build build = Build.newInstance(startParameter);

            build.addBuildListener(exceptionReporter);
            build.addBuildListener(resultLogger);

            BuildResult buildResult = build.run(startParameter);
            if (buildResult.getFailure() != null) {
                exitWithError(options, buildResult.getFailure());
            }
        } catch (Throwable e) {
            exceptionReporter.buildFinished(new BuildResult(null, e));
            exitWithError(options, e);
        }
        exitWithSuccess(options);
    }

    private static void exitWithSuccess(OptionSet options) {
        if (!options.has(NO_JVM_TERMINATION)) {
            System.exit(0);
        }
    }

    private static void configureLogger(OptionSet options) throws Throwable {

        String normalLayout = "%msg%n";
        String debugLayout = "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n";

        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("root");

        String loglevel = null;
        String layout = null;

        int ivyLogLevel = Message.MSG_INFO;
        if (options.has(IVY_DEBUG) && options.has(IVY_QUIET)) {
            System.out.printf("Error: For the dependency output you must either set -%s or -%s. Not Both!", IVY_QUIET, IVY_DEBUG);
            exitWithError(options, new RuntimeException("Wrong Parameter"));
        } else if (options.has(IVY_QUIET)) {
            ivyLogLevel = Message.MSG_ERR;
        } else if (options.has(IVY_DEBUG)) {
            ivyLogLevel = Message.MSG_DEBUG;
        }

        if (options.has(DEBUG) && options.has(QUIET)) {
            System.out.printf("Error: For the loglevel you must either set -%s or -%s. Not Both!", QUIET, DEBUG);
            exitWithError(options, new RuntimeException("Wrong Parameter"));
        } else if (options.has(DEBUG)) {
            loglevel = "DEBUG";
            layout = debugLayout;
        } else if (options.has(QUIET)) {
            loglevel = "ERROR";
            layout = normalLayout;
            if (!options.has(IVY_DEBUG)) {
                ivyLogLevel = Message.MSG_ERR;
            }
        } else {
            loglevel = "INFO";
            layout = normalLayout;
        }
        Message.setDefaultLogger(new DefaultMessageLogger(ivyLogLevel));
        rootLogger.setLevel(Level.toLevel(loglevel));
    }

    private static void exitWithError(OptionSet options, Throwable e) throws Throwable {
        if (!options.has(NO_JVM_TERMINATION)) {
            System.exit(1);
        }
        throw e;
    }
}
