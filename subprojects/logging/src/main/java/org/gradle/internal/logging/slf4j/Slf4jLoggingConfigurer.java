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

package org.gradle.internal.logging.slf4j;

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.config.LoggingConfigurer;
import org.gradle.internal.logging.events.OutputEventListener;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

/**
 * A {@link LoggingConfigurer} implementation which configures custom slf4j binding to route logging events to a provided {@link
 * OutputEventListener}.
 */
public class Slf4jLoggingConfigurer implements LoggingConfigurer {
    private final OutputEventListener outputEventListener;

    private LogLevel currentLevel;

    public Slf4jLoggingConfigurer(OutputEventListener outputListener) {
        outputEventListener = outputListener;
    }

    public void configure(LogLevel logLevel) {
        if (logLevel == currentLevel) {
            return;
        }

        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        if (!(loggerFactory instanceof OutputEventListenerBackedLoggerContext)) {
            System.out.println("Gradle cannot configure Slf4j logger of type '" + loggerFactory.getClass().getSimpleName()
                + "'. This is expected when executing tests with a custom `java.system.class.loader` set.");
            return;
        }
        OutputEventListenerBackedLoggerContext context = (OutputEventListenerBackedLoggerContext) loggerFactory;

        if (currentLevel == null) {
            context.setOutputEventListener(outputEventListener);
        }

        currentLevel = logLevel;
        context.setLevel(logLevel);
    }
}
