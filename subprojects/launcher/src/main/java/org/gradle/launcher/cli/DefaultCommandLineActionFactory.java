/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.launcher.cli;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.Main;
import org.codehaus.groovy.util.ReleaseInfo;
import org.gradle.api.Action;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineConverter;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.cli.SystemPropertiesCommandLineConverter;
import org.gradle.concurrent.ParallelismConfiguration;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.initialization.LayoutCommandLineConverter;
import org.gradle.initialization.ParallelismConfigurationCommandLineConverter;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.Actions;
import org.gradle.internal.IoActions;
import org.gradle.internal.buildevents.BuildExceptionReporter;
import org.gradle.internal.concurrent.DefaultParallelismConfiguration;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.logging.DefaultLoggingConfiguration;
import org.gradle.internal.logging.LoggingCommandLineConverter;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.services.LoggingServiceRegistry;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.bootstrap.CommandLineActionFactory;
import org.gradle.launcher.bootstrap.ExecutionListener;
import org.gradle.launcher.cli.converter.LayoutToPropertiesConverter;
import org.gradle.launcher.cli.converter.PropertiesToLogLevelConfigurationConverter;
import org.gradle.launcher.cli.converter.PropertiesToParallelismConfigurationConverter;
import org.gradle.util.GFileUtils;
import org.gradle.util.GradleVersion;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>Responsible for converting a set of command-line arguments into a {@link Runnable} action.</p>
 */
public class DefaultCommandLineActionFactory implements CommandLineActionFactory {
    public static final String WELCOME_MESSAGE_ENABLED_SYSTEM_PROPERTY = "org.gradle.internal.launcher.welcomeMessageEnabled";
    private static final String HELP = "h";
    private static final String VERSION = "v";

    /**
     * <p>Converts the given command-line arguments to an {@link Action} which performs the action requested by the
     * command-line args.
     *
     * @param args The command-line arguments.
     * @return The action to execute.
     */
    @Override
    public CommandLineExecution convert(List<String> args) {
        ServiceRegistry loggingServices = createLoggingServices();

        LoggingConfiguration loggingConfiguration = new DefaultLoggingConfiguration();

        return new WithLogging(loggingServices,
            args,
            loggingConfiguration,
            new ParseAndBuildAction(loggingServices, args),
            new BuildExceptionReporter(loggingServices.get(StyledTextOutputFactory.class), loggingConfiguration, clientMetaData()));
    }

    @VisibleForTesting
    protected void createActionFactories(ServiceRegistry loggingServices, Collection<CommandLineAction> actions) {
        actions.add(new BuildActionsFactory(loggingServices));
    }

    private static GradleLauncherMetaData clientMetaData() {
        return new GradleLauncherMetaData();
    }

    public ServiceRegistry createLoggingServices() {
        return LoggingServiceRegistry.newCommandLineProcessLogging();
    }

    private static void showUsage(PrintStream out, CommandLineParser parser) {
        out.println();
        out.print("USAGE: ");
        clientMetaData().describeCommand(out, "[option...]", "[task...]");
        out.println();
        out.println();
        parser.printUsage(out);
        out.println();
    }

    @VisibleForTesting
    static class WelcomeMessageAction implements Action<Logger> {

        private final BuildLayoutParameters buildLayoutParameters;
        private final GradleVersion gradleVersion;
        private final Function<String, InputStream> inputStreamProvider;

        WelcomeMessageAction(BuildLayoutParameters buildLayoutParameters) {
            this(buildLayoutParameters, GradleVersion.current(), new Function<String, InputStream>() {
                @Nullable
                @Override
                public InputStream apply(@Nullable String input) {
                    return getClass().getClassLoader().getResourceAsStream(input);
                }
            });
        }

        @VisibleForTesting
        WelcomeMessageAction(BuildLayoutParameters buildLayoutParameters, GradleVersion gradleVersion, Function<String, InputStream> inputStreamProvider) {
            this.buildLayoutParameters = buildLayoutParameters;
            this.gradleVersion = gradleVersion;
            this.inputStreamProvider = inputStreamProvider;
        }

        @Override
        public void execute(Logger logger) {
            if (isWelcomeMessageEnabled()) {
                File markerFile = getMarkerFile();

                if (!markerFile.exists() && logger.isLifecycleEnabled()) {
                    logger.lifecycle("");
                    logger.lifecycle("Welcome to Gradle " + gradleVersion.getVersion() + "!");

                    String featureList = readReleaseFeatures();

                    if (StringUtils.isNotBlank(featureList)) {
                        logger.lifecycle("");
                        logger.lifecycle("Here are the highlights of this release:");
                        logger.lifecycle(StringUtils.stripEnd(featureList, " \n\r"));
                    }

                    if (!gradleVersion.isSnapshot()) {
                        logger.lifecycle("");
                        logger.lifecycle("For more details see https://docs.gradle.org/" + gradleVersion.getVersion() + "/release-notes.html");
                    }

                    logger.lifecycle("");

                    writeMarkerFile(markerFile);
                }
            }
        }

        /**
         * The system property is set for the purpose of internal testing.
         * In user environments the system property will never be available.
         */
        private boolean isWelcomeMessageEnabled() {
            String messageEnabled = System.getProperty(DefaultCommandLineActionFactory.WELCOME_MESSAGE_ENABLED_SYSTEM_PROPERTY);

            if (messageEnabled == null) {
                return true;
            }

            return Boolean.parseBoolean(messageEnabled);
        }

        private File getMarkerFile() {
            File gradleUserHomeDir = buildLayoutParameters.getGradleUserHomeDir();
            File notificationsDir = new File(gradleUserHomeDir, "notifications");
            File versionedNotificationsDir = new File(notificationsDir, gradleVersion.getVersion());
            return new File(versionedNotificationsDir, "release-features.rendered");
        }

        private String readReleaseFeatures() {
            InputStream inputStream = inputStreamProvider.apply("release-features.txt");

            if (inputStream != null) {
                StringWriter writer = new StringWriter();

                try {
                    IOUtils.copy(inputStream, writer, "UTF-8");
                    return writer.toString();
                } catch (IOException e) {
                    // do not fail the build as feature is non-critical
                } finally {
                    IoActions.closeQuietly(inputStream);
                }
            }

            return null;
        }

        private void writeMarkerFile(File markerFile) {
            GFileUtils.mkdirs(markerFile.getParentFile());
            GFileUtils.touch(markerFile);
        }
    }

    private static class BuiltInActions implements CommandLineAction {
        @Override
        public void configureCommandLineParser(CommandLineParser parser) {
            parser.option(HELP, "?", "help").hasDescription("Shows this help message.");
            parser.option(VERSION, "version").hasDescription("Print version info.");
        }

        @Override
        public Runnable createAction(CommandLineParser parser, ParsedCommandLine commandLine) {
            if (commandLine.hasOption(HELP)) {
                return new ShowUsageAction(parser);
            }
            if (commandLine.hasOption(VERSION)) {
                return new ShowVersionAction();
            }
            return null;
        }
    }

    private static class CommandLineParseFailureAction implements Action<ExecutionListener> {
        private final Exception e;
        private final CommandLineParser parser;

        public CommandLineParseFailureAction(CommandLineParser parser, Exception e) {
            this.parser = parser;
            this.e = e;
        }

        @Override
        public void execute(ExecutionListener executionListener) {
            System.err.println();
            System.err.println(e.getMessage());
            showUsage(System.err, parser);
            executionListener.onFailure(e);
        }
    }

    private static class ShowUsageAction implements Runnable {
        private final CommandLineParser parser;

        public ShowUsageAction(CommandLineParser parser) {
            this.parser = parser;
        }

        @Override
        public void run() {
            showUsage(System.out, parser);
        }
    }

    private static class ShowVersionAction implements Runnable {
        @Override
        public void run() {
            GradleVersion currentVersion = GradleVersion.current();

            final StringBuilder sb = new StringBuilder();
            sb.append("%n------------------------------------------------------------%nGradle ");
            sb.append(currentVersion.getVersion());
            sb.append("%n------------------------------------------------------------%n%nBuild time:   ");
            sb.append(currentVersion.getBuildTime());
            sb.append("%nRevision:     ");
            sb.append(currentVersion.getRevision());
            sb.append("%n%nKotlin:       ");
            sb.append(KotlinDslVersion.current().getKotlinVersion());
            sb.append("%nGroovy:       ");
            sb.append(ReleaseInfo.getVersion());
            sb.append("%nAnt:          ");
            sb.append(Main.getAntVersion());
            sb.append("%nJVM:          ");
            sb.append(Jvm.current());
            sb.append("%nOS:           ");
            sb.append(OperatingSystem.current());
            sb.append("%n");

            System.out.println(String.format(sb.toString()));
        }
    }

    private static class WithLogging implements CommandLineExecution {
        private final ServiceRegistry loggingServices;
        private final List<String> args;
        private final LoggingConfiguration loggingConfiguration;
        private final Action<ExecutionListener> action;
        private final Action<Throwable> reporter;

        WithLogging(ServiceRegistry loggingServices, List<String> args, LoggingConfiguration loggingConfiguration, Action<ExecutionListener> action, Action<Throwable> reporter) {
            this.loggingServices = loggingServices;
            this.args = args;
            this.loggingConfiguration = loggingConfiguration;
            this.action = action;
            this.reporter = reporter;
        }

        @Override
        public void execute(ExecutionListener executionListener) {
            CommandLineConverter<LoggingConfiguration> loggingConfigurationConverter = new LoggingCommandLineConverter();
            CommandLineConverter<BuildLayoutParameters> buildLayoutConverter = new LayoutCommandLineConverter();
            CommandLineConverter<ParallelismConfiguration> parallelConverter = new ParallelismConfigurationCommandLineConverter();
            CommandLineConverter<Map<String, String>> systemPropertiesCommandLineConverter = new SystemPropertiesCommandLineConverter();
            LayoutToPropertiesConverter layoutToPropertiesConverter = new LayoutToPropertiesConverter(new BuildLayoutFactory());

            BuildLayoutParameters buildLayout = new BuildLayoutParameters();
            ParallelismConfiguration parallelismConfiguration = new DefaultParallelismConfiguration();

            CommandLineParser parser = new CommandLineParser();
            loggingConfigurationConverter.configure(parser);
            buildLayoutConverter.configure(parser);
            parallelConverter.configure(parser);
            systemPropertiesCommandLineConverter.configure(parser);

            parser.allowUnknownOptions();
            parser.allowMixedSubcommandsAndOptions();

            try {
                ParsedCommandLine parsedCommandLine = parser.parse(args);

                buildLayoutConverter.convert(parsedCommandLine, buildLayout);


                Map<String, String> properties = new HashMap<String, String>();
                // Read *.properties files
                layoutToPropertiesConverter.convert(buildLayout, properties);
                // Read -D command line flags
                systemPropertiesCommandLineConverter.convert(parsedCommandLine, properties);

                // Convert properties for logging  object
                PropertiesToLogLevelConfigurationConverter propertiesToLogLevelConfigurationConverter = new PropertiesToLogLevelConfigurationConverter();
                propertiesToLogLevelConfigurationConverter.convert(properties, loggingConfiguration);
                loggingConfigurationConverter.convert(parsedCommandLine, loggingConfiguration);

                // Convert properties to ParallelismConfiguration object
                PropertiesToParallelismConfigurationConverter propertiesToParallelismConfigurationConverter = new PropertiesToParallelismConfigurationConverter();
                propertiesToParallelismConfigurationConverter.convert(properties, parallelismConfiguration);
                // Parse parallelism flags
                parallelConverter.convert(parsedCommandLine, parallelismConfiguration);
            } catch (CommandLineArgumentException e) {
                // Ignore, deal with this problem later
            }

            LoggingManagerInternal loggingManager = loggingServices.getFactory(LoggingManagerInternal.class).create();
            loggingManager.setLevelInternal(loggingConfiguration.getLogLevel());
            loggingManager.start();
            Action<ExecutionListener> exceptionReportingAction = new ExceptionReportingAction(action, reporter, loggingManager);
            try {
                NativeServices.initialize(buildLayout.getGradleUserHomeDir());
                loggingManager.attachProcessConsole(loggingConfiguration.getConsoleOutput());
                new WelcomeMessageAction(buildLayout).execute(Logging.getLogger(WelcomeMessageAction.class));
                exceptionReportingAction.execute(executionListener);
            } finally {
                loggingManager.stop();
            }
        }
    }

    private class ParseAndBuildAction implements Action<ExecutionListener> {
        private final ServiceRegistry loggingServices;
        private final List<String> args;

        private ParseAndBuildAction(ServiceRegistry loggingServices, List<String> args) {
            this.loggingServices = loggingServices;
            this.args = args;
        }

        @Override
        public void execute(ExecutionListener executionListener) {
            List<CommandLineAction> actions = new ArrayList<CommandLineAction>();
            actions.add(new BuiltInActions());
            createActionFactories(loggingServices, actions);

            CommandLineParser parser = new CommandLineParser();
            for (CommandLineAction action : actions) {
                action.configureCommandLineParser(parser);
            }

            Action<? super ExecutionListener> action;
            try {
                ParsedCommandLine commandLine = parser.parse(args);
                action = createAction(actions, parser, commandLine);
            } catch (CommandLineArgumentException e) {
                action = new CommandLineParseFailureAction(parser, e);
            }

            action.execute(executionListener);
        }

        private Action<? super ExecutionListener> createAction(Iterable<CommandLineAction> factories, CommandLineParser parser, ParsedCommandLine commandLine) {
            for (CommandLineAction factory : factories) {
                Runnable action = factory.createAction(parser, commandLine);
                if (action != null) {
                    return Actions.toAction(action);
                }
            }
            throw new UnsupportedOperationException("No action factory for specified command-line arguments.");
        }
    }
}
