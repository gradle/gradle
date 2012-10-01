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

package org.gradle.logging;

import org.gradle.StartParameter;
import org.gradle.cli.CommandLineConverter;
import org.gradle.internal.Factory;
import org.gradle.internal.TimeProvider;
import org.gradle.internal.TrueTimeProvider;
import org.gradle.internal.nativeplatform.NoOpTerminalDetector;
import org.gradle.internal.nativeplatform.TerminalDetector;
import org.gradle.internal.nativeplatform.services.NativeServices;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.logging.internal.*;
import org.gradle.logging.internal.logback.LogbackLoggingConfigurer;

/**
 * A {@link org.gradle.internal.service.ServiceRegistry} implementation which provides the logging services.
 */
public class LoggingServiceRegistry extends DefaultServiceRegistry {
    private TextStreamOutputEventListener stdoutListener;
    private final boolean detectConsole;
    private final boolean isEmbedded;

    LoggingServiceRegistry() {
        this(true, false);
    }

    LoggingServiceRegistry(boolean detectConsole, boolean isEmbedded) {
        this.detectConsole = detectConsole;
        this.isEmbedded = isEmbedded;
        stdoutListener = new TextStreamOutputEventListener(get(OutputEventListener.class));
    }

    /**
     * Creates a set of logging services which are suitable to use in a command-line process.
     */
    public static LoggingServiceRegistry newCommandLineProcessLogging() {
        return new LoggingServiceRegistry(true, false);
    }

    /**
     * Creates a set of logging services which are suitable to use in a child process. Does not attempt to use any terminal trickery.
     */
    public static LoggingServiceRegistry newChildProcessLogging() {
        return new LoggingServiceRegistry(false, false);
    }

    /**
     * Creates a set of logging services which are suitable to use embedded in another application. Does not attempt to use any terminal trickery.
     */
    public static LoggingServiceRegistry newEmbeddableLogging() {
        return new LoggingServiceRegistry(false, true);
    }

    protected CommandLineConverter<LoggingConfiguration> createCommandLineConverter() {
        return new LoggingCommandLineConverter();
    }

    protected TimeProvider createTimeProvider() {
        return new TrueTimeProvider();
    }

    protected StdOutLoggingSystem createStdOutLoggingSystem() {
        if (isEmbedded) {
            return new NoOpLoggingSystem();
        }
        return new DefaultStdOutLoggingSystem(stdoutListener, get(TimeProvider.class));
    }

    protected StyledTextOutputFactory createStyledTextOutputFactory() {
        return new DefaultStyledTextOutputFactory(stdoutListener, get(TimeProvider.class));
    }

    protected StdErrLoggingSystem createStdErrLoggingSystem() {
        if (isEmbedded) {
            return new NoOpLoggingSystem();
        }
        TextStreamOutputEventListener listener = new TextStreamOutputEventListener(get(OutputEventListener.class));
        return new DefaultStdErrLoggingSystem(listener, get(TimeProvider.class));
    }

    protected ProgressLoggerFactory createProgressLoggerFactory() {
        return new DefaultProgressLoggerFactory(new ProgressLoggingBridge(get(OutputEventListener.class)), get(TimeProvider.class));
    }

    protected Factory<LoggingManagerInternal> createLoggingManagerFactory() {
        OutputEventRenderer renderer = get(OutputEventRenderer.class);
        if (!isEmbedded) {
            //we want to reset and manipulate java logging only if we own the process, e.g. we're *not* embedded
            DefaultLoggingConfigurer compositeConfigurer = new DefaultLoggingConfigurer(renderer);
            compositeConfigurer.add(new LogbackLoggingConfigurer(renderer));
            compositeConfigurer.add(new JavaUtilLoggingConfigurer());
            return new DefaultLoggingManagerFactory(compositeConfigurer, renderer, getStdOutLoggingSystem(), getStdErrLoggingSystem());
        } else {
            return new EmbeddedLoggingManagerFactory(renderer);
        }
    }

    private LoggingSystem getStdErrLoggingSystem() {
        return get(StdErrLoggingSystem.class);
    }

    private LoggingSystem getStdOutLoggingSystem() {
        return get(StdOutLoggingSystem.class);
    }

    protected OutputEventRenderer createOutputEventRenderer() {
        TerminalDetector terminalDetector;
        if (detectConsole) {
            StartParameter startParameter = new StartParameter();
            NativeServices.initialize(startParameter.getGradleUserHomeDir());
            terminalDetector = NativeServices.getInstance().get(TerminalDetector.class);
        } else {
            terminalDetector = new NoOpTerminalDetector();
        }
        return new OutputEventRenderer(terminalDetector).addStandardOutputAndError();
    }
}
