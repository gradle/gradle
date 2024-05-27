/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.logging.services;

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.config.LoggingSourceSystem;
import org.gradle.internal.logging.config.LoggingSystemAdapter;
import org.gradle.internal.logging.console.DefaultUserInputReceiver;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.sink.OutputEventListenerManager;
import org.gradle.internal.logging.sink.OutputEventRenderer;
import org.gradle.internal.logging.slf4j.Slf4jLoggingConfigurer;
import org.gradle.internal.logging.source.DefaultStdErrLoggingSystem;
import org.gradle.internal.logging.source.DefaultStdOutLoggingSystem;
import org.gradle.internal.logging.source.JavaUtilLoggingSystem;
import org.gradle.internal.logging.source.NoOpLoggingSystem;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.time.Clock;
import org.gradle.internal.time.Time;

/**
 * A {@link org.gradle.internal.service.ServiceRegistry} implementation that provides the logging services. To use this:
 *
 * <ol>
 * <li>Create an instance using one of the static factory methods below.</li>
 * <li>Create an instance of {@link LoggingManagerInternal}.</li>
 * <li>Configure the logging manager as appropriate.</li>
 * <li>Start the logging manager using {@link LoggingManagerInternal#start()}.</li>
 * <li>When finished, stop the logging manager using {@link LoggingManagerInternal#stop()}.</li>
 * </ol>
 */
public abstract class LoggingServiceRegistry extends DefaultServiceRegistry {

    public static final ServiceRegistrationProvider NO_OP = new ServiceRegistrationProvider() {
        @Provides
        OutputEventListener createOutputEventListener() {
            return OutputEventListener.NO_OP;
        }
    };

    private TextStreamOutputEventListener stdoutListener;

    private final DefaultUserInputReceiver userInput = new DefaultUserInputReceiver();
    protected final OutputEventRenderer renderer = makeOutputEventRenderer();
    protected final OutputEventListenerManager outputEventListenerManager = new OutputEventListenerManager(renderer);

    /**
     * Creates a set of logging services which are suitable to use globally in a process. In particular:
     *
     * <ul>
     * <li>Replaces System.out and System.err with implementations that route output through the logging system as per {@link LoggingManagerInternal#captureSystemSources()}.</li>
     * <li>Configures slf4j, log4j and java util logging to route log messages through the logging system.</li>
     * <li>Routes logging output to the original System.out and System.err as per {@link LoggingManagerInternal#attachSystemOutAndErr()}.</li>
     * <li>Sets log level to {@link org.gradle.api.logging.LogLevel#LIFECYCLE}.</li>
     * </ul>
     *
     * <p>Does nothing until started.</p>
     *
     * <p>Allows dynamic and colored output to be written to the console. Use {@link LoggingManagerInternal#attachProcessConsole(org.gradle.api.logging.configuration.ConsoleOutput)} to enable this.</p>
     */
    public static LoggingServiceRegistry newCommandLineProcessLogging() {
        CommandLineLogging loggingServices = new CommandLineLogging();
        LoggingManagerInternal rootLoggingManager = loggingServices.get(DefaultLoggingManagerFactory.class).getRoot();
        rootLoggingManager.captureSystemSources();
        rootLoggingManager.attachSystemOutAndErr();
        return loggingServices;
    }

    /**
     * Creates a set of logging services which are suitable to use embedded in another application. In particular:
     *
     * <ul>
     * <li>Configures slf4j and log4j to route log messages through the logging system.</li>
     * <li>Sets log level to {@link org.gradle.api.logging.LogLevel#LIFECYCLE}.</li>
     * </ul>
     *
     * <p>Does not:</p>
     *
     * <ul>
     * <li>Replace System.out and System.err to capture output written to these destinations. Use {@link LoggingManagerInternal#captureSystemSources()} to enable this.</li>
     * <li>Configure java util logging. Use {@link LoggingManagerInternal#captureSystemSources()} to enable this.</li>
     * <li>Route logging output to the original System.out and System.err. Use {@link LoggingManagerInternal#attachSystemOutAndErr()} to enable this.</li>
     * </ul>
     *
     * <p>Does nothing until started.</p>
     */
    public static LoggingServiceRegistry newEmbeddableLogging() {
        return new CommandLineLogging();
    }

    /**
     * Creates a set of logging services to set up a new logging scope that does nothing by default. The methods on {@link LoggingManagerInternal} can be used to configure the
     * logging services do useful things.
     *
     * <p>Sets log level to {@link org.gradle.api.logging.LogLevel#LIFECYCLE}.</p>
     */
    public static LoggingServiceRegistry newNestedLogging() {
        return new NestedLogging();
    }

    @Provides
    protected Clock createTimeProvider() {
        return Time.clock();
    }

    @Provides
    protected StyledTextOutputFactory createStyledTextOutputFactory() {
        return new DefaultStyledTextOutputFactory(getStdoutListener(), get(Clock.class));
    }

    protected TextStreamOutputEventListener getStdoutListener() {
        if (stdoutListener == null) {
            stdoutListener = new TextStreamOutputEventListener(get(OutputEventListenerManager.class).getBroadcaster());
        }
        return stdoutListener;
    }

    @Provides
    protected DefaultLoggingManagerFactory createLoggingManagerFactory() {
        OutputEventListener outputEventBroadcaster = outputEventListenerManager.getBroadcaster();

        LoggingSourceSystem stdout = new DefaultStdOutLoggingSystem(getStdoutListener(), get(Clock.class));
        stdout.setLevel(LogLevel.QUIET);
        LoggingSourceSystem stderr = new DefaultStdErrLoggingSystem(new TextStreamOutputEventListener(outputEventBroadcaster), get(Clock.class));
        stderr.setLevel(LogLevel.ERROR);
        return new DefaultLoggingManagerFactory(
            renderer,
            new LoggingSystemAdapter(new Slf4jLoggingConfigurer(outputEventBroadcaster)),
            new JavaUtilLoggingSystem(),
            stdout,
            stderr);
    }

    @Provides
    protected OutputEventListener createOutputEventListener(OutputEventListenerManager manager) {
        return manager.getBroadcaster();
    }

    @Provides
    protected OutputEventListenerManager createOutputEventListenerManager() {
        return outputEventListenerManager;
    }

    @Provides
    protected DefaultUserInputReceiver createUserInput() {
        return userInput;
    }

    // Intentionally not a “create” method as this should not be exposed as a service
    protected OutputEventRenderer makeOutputEventRenderer() {
        OutputEventRenderer eventRenderer = new OutputEventRenderer(Time.clock(), userInput);
        userInput.attachConsole(eventRenderer);
        return eventRenderer;
    }

    private static class CommandLineLogging extends LoggingServiceRegistry {
    }

    private static class NestedLogging extends LoggingServiceRegistry {

        @Provides
        @Override
        protected DefaultLoggingManagerFactory createLoggingManagerFactory() {
            // Don't configure anything
            return new DefaultLoggingManagerFactory(
                renderer,
                new NoOpLoggingSystem(),
                new NoOpLoggingSystem(),
                new NoOpLoggingSystem(),
                new NoOpLoggingSystem()
            );
        }
    }
}
