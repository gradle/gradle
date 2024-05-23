/*
 * Copyright 2024 the original author or authors.
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
import com.google.common.collect.ImmutableList;
import org.apache.tools.ant.Main;
import org.codehaus.groovy.util.ReleaseInfo;
import org.gradle.api.Action;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.launcher.cli.WelcomeMessageConfiguration;
import org.gradle.api.launcher.cli.WelcomeMessageDisplayMode;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.configuration.DefaultBuildClientMetaData;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.Actions;
import org.gradle.internal.buildevents.BuildExceptionReporter;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.logging.DefaultLoggingConfiguration;
import org.gradle.internal.logging.LoggingConfigurationBuildOptions;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.services.LoggingServiceRegistry;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.BasicGlobalScopeServices;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.launcher.bootstrap.CommandLineActionFactory;
import org.gradle.launcher.bootstrap.ExecutionListener;
import org.gradle.launcher.cli.converter.BuildLayoutConverter;
import org.gradle.launcher.cli.converter.BuildOptionBackedConverter;
import org.gradle.launcher.cli.converter.InitialPropertiesConverter;
import org.gradle.launcher.cli.converter.LayoutToPropertiesConverter;
import org.gradle.launcher.cli.converter.WelcomeMessageBuildOptions;
import org.gradle.launcher.configuration.AllProperties;
import org.gradle.launcher.configuration.BuildLayoutResult;
import org.gradle.launcher.configuration.InitialProperties;
import org.gradle.util.internal.DefaultGradleVersion;

import javax.annotation.Nullable;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>Responsible for converting a set of command-line arguments into a {@link Runnable} action.</p>
 */
public class DefaultCommandLineActionFactory implements CommandLineActionFactory {
    private static final String HELP = "h";
    private static final String VERSION = "v";
    private static final String VERSION_CONTINUE = "V";

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

    private static BuildClientMetaData clientMetaData() {
        return new DefaultBuildClientMetaData(new GradleLauncherMetaData());
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

    /**
     * This method is left visible so that tests can override it to inject {@link CommandLineActionCreator}s which
     * don't actually attempt to run the build per normally.
     *
     * @param loggingServices logging services to use when instantiating any {@link CommandLineActionCreator}s
     * @param basicServices basic services to use when instantiating any {@link CommandLineActionCreator}s
     * @param actionCreators collection of {@link CommandLineActionCreator}s to which to add a new {@link BuildActionsFactory}
     */
    @VisibleForTesting
    protected void createBuildActionFactoryActionCreator(ServiceRegistry loggingServices, ServiceRegistry basicServices, List<CommandLineActionCreator> actionCreators) {
        actionCreators.add(new BuildActionsFactory(loggingServices, basicServices));
    }

    /**
     * This method is left visible so that tests can override it to inject mocked {@link ServiceRegistry}s.
     *
     * @return the created {@link ServiceRegistry}
     */
    @VisibleForTesting
    protected ServiceRegistry createLoggingServices() {
        return LoggingServiceRegistry.newCommandLineProcessLogging();
    }

    private static class BuiltInActionCreator implements CommandLineActionCreator {
        @Override
        public void configureCommandLineParser(CommandLineParser parser) {
            parser.option(HELP, "?", "help").hasDescription("Shows this help message.");
            parser.option(VERSION, "version").hasDescription("Print version info and exit.");
            parser.option(VERSION_CONTINUE, "show-version").hasDescription("Print version info and continue.");
        }

        @Override
        @Nullable
        public Action<? super ExecutionListener> createAction(CommandLineParser parser, ParsedCommandLine commandLine, Parameters parameters) {
            if (commandLine.hasOption(HELP)) {
                return new ShowUsageAction(parser);
            }
            if (commandLine.hasOption(VERSION)) {
                return new ShowVersionAction(parameters);
            }
            return null;
        }
    }

    /**
     * This {@link CommandLineActionCreator} is responsible for handling any command line options that produce {@link ContinuingAction}s.
     */
    private static class ContinuingActionCreator extends NonParserConfiguringCommandLineActionCreator {
        @Override
        @Nullable
        public ContinuingAction<? super ExecutionListener> createAction(CommandLineParser parser, ParsedCommandLine commandLine, Parameters parameters) {
            if (commandLine.hasOption(DefaultCommandLineActionFactory.VERSION_CONTINUE)) {
                return (ContinuingAction<ExecutionListener>) executionListener -> new ShowVersionAction(parameters).execute(executionListener);
            }
            return null;
        }
    }

    private static class CommandLineParseFailureAction implements Action<ExecutionListener> {
        private final Exception exception;
        private final CommandLineParser parser;

        public CommandLineParseFailureAction(CommandLineParser parser, Exception exception) {
            this.parser = parser;
            this.exception = exception;
        }

        @Override
        public void execute(ExecutionListener executionListener) {
            System.err.println();
            System.err.println(exception.getMessage());
            showUsage(System.err, parser);
            executionListener.onFailure(exception);
        }
    }

    private static class ShowUsageAction implements Action<ExecutionListener> {
        private final CommandLineParser parser;

        public ShowUsageAction(CommandLineParser parser) {
            this.parser = parser;
        }

        @Override
        public void execute(ExecutionListener executionListener) {
            System.out.println();
            System.out.print("To see help contextual to the project, use ");
            clientMetaData().describeCommand(System.out, "help");
            System.out.println();
            showUsage(System.out, parser);
        }
    }

    public static class ShowVersionAction implements Action<ExecutionListener> {
        private final Parameters parameters;

        public ShowVersionAction(Parameters parameters) {
            this.parameters = parameters;
        }

        @Override
        public void execute(ExecutionListener executionListener) {
            DefaultGradleVersion currentVersion = DefaultGradleVersion.current();

            System.out.println();
            System.out.println("------------------------------------------------------------");
            System.out.println("Gradle " + currentVersion.getVersion());
            System.out.println("------------------------------------------------------------");
            System.out.println();
            printAligned(ImmutableList.of(
                new Line.KeyValue("Build time", currentVersion.getBuildTimestamp()),
                new Line.KeyValue("Revision", currentVersion.getGitRevision()),
                new Line.Blank(),
                new Line.KeyValue("Kotlin", KotlinDslVersion.current().getKotlinVersion()),
                new Line.KeyValue("Groovy", ReleaseInfo.getVersion()),
                new Line.KeyValue("Ant", Main.getAntVersion()),
                new Line.KeyValue("Launcher JVM", Jvm.current().toString()),
                new Line.KeyValue("Daemon JVM", parameters.getDaemonParameters().getRequestedJvmCriteria().toString()),
                new Line.KeyValue("OS", OperatingSystem.current().toString()),
                new Line.Blank()
            ));
        }

        private interface Line {
            class KeyValue implements Line {
                private final String key;
                private final String value;

                public KeyValue(String key, String value) {
                    this.key = key;
                    this.value = value;
                }
            }

            class Blank implements Line {
            }
        }

        /**
         * Print a list of lines, aligning values to the right of the keys.
         *
         * <p>
         * For example, given the following lines:
         * <pre>
         * new Line.KeyValue("Key", "Value"),
         * new Line.KeyValue("Another key", "Another value"),
         * new Line.Blank()
         * </pre>
         * This method would print:
         * <pre>
         * Key:          Value
         * Another key:  Another value
         * [blank line]
         * </pre>
         *
         * @param lines the lines to print
         */
        private static void printAligned(List<Line> lines) {
            int maxKeyLength = lines.stream()
                .filter(line -> line instanceof Line.KeyValue)
                .map(line -> ((Line.KeyValue) line).key.length())
                .max(Integer::compare)
                .orElse(0);
            for (Line line : lines) {
                if (line instanceof Line.KeyValue) {
                    Line.KeyValue keyValue = (Line.KeyValue) line;
                    System.out.print(keyValue.key + ": ");
                    // Add one to account for colon
                    for (int i = keyValue.key.length(); i < maxKeyLength + 1; i++) {
                        System.out.print(' ');
                    }
                    System.out.println(keyValue.value);
                } else {
                    System.out.println();
                }
            }
        }
    }

    /**
     * This {@link Action} will create new {@link Action}s that will be immediately executed.
     *
     * This class accomplishes this be maintaining a list of {@link CommandLineActionCreator}s which can each attempt to
     * create an {@link Action} from the given CLI args, and handles the logic for deciding whether or not to continue processing
     * based on whether the result is a {@link ContinuingAction} or not.  It allows for injecting alternate Creators which
     * won't actually attempt to run a build via the containing class' {@link #createBuildActionFactoryActionCreator(ServiceRegistry, ServiceRegistry, List)}
     * method - this is why this class is not {@code static}.
     */
    private class ParseAndBuildAction implements Action<ExecutionListener> {
        private final ServiceRegistry loggingServices;
        private final List<String> args;
        private final List<CommandLineActionCreator> actionCreators;
        private final CommandLineParser parser = new CommandLineParser();

        private ParseAndBuildAction(ServiceRegistry loggingServices, List<String> args) {
            this.loggingServices = loggingServices;
            this.args = args;

            actionCreators = new ArrayList<>();
            actionCreators.add(new BuiltInActionCreator());
            actionCreators.add(new ContinuingActionCreator());
        }

        @Override
        public void execute(ExecutionListener executionListener) {
            ServiceRegistry basicServices = createBasicGlobalServices(loggingServices);
            BuildEnvironmentConfigurationConverter buildEnvironmentConfigurationConverter = new BuildEnvironmentConfigurationConverter(
                new BuildLayoutFactory(),
                basicServices.get(FileCollectionFactory.class));
            buildEnvironmentConfigurationConverter.configure(parser);

            // This must be added only during execute, because the actual constructor is called by various tests and this will not succeed if called then
            createBuildActionFactoryActionCreator(loggingServices, basicServices, actionCreators);
            configureCreators();

            Action<? super ExecutionListener> action;
            try {
                ParsedCommandLine commandLine = parser.parse(args);
                Parameters parameters = buildEnvironmentConfigurationConverter.convertParameters(commandLine, null);
                action = createAction(parser, commandLine, parameters);
            } catch (CommandLineArgumentException e) {
                action = new CommandLineParseFailureAction(parser, e);
            }

            action.execute(executionListener);
        }

        private void configureCreators() {
            actionCreators.forEach(creator -> creator.configureCommandLineParser(parser));
        }

        public Action<? super ExecutionListener> createAction(CommandLineParser parser, ParsedCommandLine commandLine, Parameters parameters) {
            List<Action<? super ExecutionListener>> actions = new ArrayList<>(2);
            for (CommandLineActionCreator actionCreator : actionCreators) {
                Action<? super ExecutionListener> action = actionCreator.createAction(parser, commandLine, parameters);
                if (action != null) {
                    actions.add(action);
                    if (!(action instanceof ContinuingAction)) {
                        break;
                    }
                }
            }

            if (!actions.isEmpty()) {
                return Actions.composite(actions);
            }

            throw new UnsupportedOperationException("No action factory for specified command-line arguments.");
        }
    }

    @VisibleForTesting
    public ServiceRegistry createBasicGlobalServices(ServiceRegistry loggingServices) {
        return ServiceRegistryBuilder.builder()
            .scopeStrictly(Scope.Global.class)
            .displayName("Basic global services")
            .parent(loggingServices)
            .parent(NativeServices.getInstance())
            .provider(new BasicGlobalScopeServices())
            .build();
    }

    /**
     * Abstract type for any {@link CommandLineActionCreator} that does not make use of the {@link #configureCommandLineParser(CommandLineParser)}
     * method.
     */
    private static abstract class NonParserConfiguringCommandLineActionCreator implements CommandLineActionCreator {
        @Override
        public void configureCommandLineParser(CommandLineParser parser) {
            // no-op
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
            BuildOptionBackedConverter<WelcomeMessageConfiguration> welcomeMessageConverter = new BuildOptionBackedConverter<>(new WelcomeMessageBuildOptions());
            BuildOptionBackedConverter<LoggingConfiguration> loggingBuildOptions = new BuildOptionBackedConverter<>(new LoggingConfigurationBuildOptions());
            InitialPropertiesConverter propertiesConverter = new InitialPropertiesConverter();
            BuildLayoutConverter buildLayoutConverter = new BuildLayoutConverter();
            LayoutToPropertiesConverter layoutToPropertiesConverter = new LayoutToPropertiesConverter(new BuildLayoutFactory());

            BuildLayoutResult buildLayout = buildLayoutConverter.defaultValues();

            CommandLineParser parser = new CommandLineParser();
            propertiesConverter.configure(parser);
            buildLayoutConverter.configure(parser);
            loggingBuildOptions.configure(parser);

            parser.allowUnknownOptions();
            parser.allowMixedSubcommandsAndOptions();

            WelcomeMessageConfiguration welcomeMessageConfiguration = new WelcomeMessageConfiguration(WelcomeMessageDisplayMode.ONCE);

            try {
                ParsedCommandLine parsedCommandLine = parser.parse(args);
                InitialProperties initialProperties = propertiesConverter.convert(parsedCommandLine);

                // Calculate build layout, for loading properties and other logging configuration
                buildLayout = buildLayoutConverter.convert(initialProperties, parsedCommandLine, null);

                // Read *.properties files
                AllProperties properties = layoutToPropertiesConverter.convert(initialProperties, buildLayout);

                // Calculate the logging configuration
                loggingBuildOptions.convert(parsedCommandLine, properties.getProperties(), loggingConfiguration);

                // Get configuration for showing the welcome message
                welcomeMessageConverter.convert(parsedCommandLine, properties.getProperties(), welcomeMessageConfiguration);
            } catch (CommandLineArgumentException e) {
                // Ignore, deal with this problem later
            }

            LoggingManagerInternal loggingManager = loggingServices.getFactory(LoggingManagerInternal.class).create();
            loggingManager.setLevelInternal(loggingConfiguration.getLogLevel());
            loggingManager.start();
            try {
                Action<ExecutionListener> exceptionReportingAction =
                    new ExceptionReportingAction(reporter, loggingManager,
                        new NativeServicesInitializingAction(buildLayout, loggingConfiguration, loggingManager,
                            new WelcomeMessageAction(buildLayout, welcomeMessageConfiguration,
                                new DebugLoggerWarningAction(loggingConfiguration, action))));
                exceptionReportingAction.execute(executionListener);
            } finally {
                loggingManager.stop();
            }
        }
    }
}
