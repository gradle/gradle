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

package org.gradle.logging.internal.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.spi.FilterReply;
import org.gradle.api.logging.LogLevel;
import org.gradle.internal.UncheckedException;
import org.gradle.logging.internal.LogEvent;
import org.gradle.logging.internal.LoggingConfigurer;
import org.gradle.logging.internal.OutputEventListener;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import java.io.PrintStream;

/**
 * A {@link org.gradle.logging.internal.LoggingConfigurer} implementation which configures Logback
 * to route logging events to a {@link org.gradle.logging.internal.OutputEventListener}.
 */
public class LogbackLoggingConfigurer implements LoggingConfigurer {
    private final OutputEventListener outputEventListener;
    private final PrintStream defaultStandardOut = System.out;

    private LogLevel currentLevel;

    public LogbackLoggingConfigurer(OutputEventListener outputListener) {
        outputEventListener = outputListener;
    }

    public void configure(LogLevel logLevel) {
        if (logLevel == currentLevel) {
            return;
        }

        try {
            doConfigure(logLevel);
        } catch (Throwable e) {
            doFailSafeConfiguration();
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private void doConfigure(LogLevel logLevel) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);

        if (currentLevel == null) {
            context.reset();
            context.addTurboFilter(new GradleFilter());
            context.getLogger("org.apache.http.wire").setLevel(Level.OFF);
            GradleAppender appender = new GradleAppender();
            appender.setContext(context);
            appender.start();
            rootLogger.addAppender(appender);
        }

        currentLevel = logLevel;
        rootLogger.setLevel(LogLevelConverter.toLogbackLevel(logLevel));
    }

    private void doFailSafeConfiguration() {
        // Not really fail-safe, just less likely to fail
        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.INFO);

        ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<ILoggingEvent>();
        rootLogger.addAppender(appender);
        appender.setContext(context);
        appender.setTarget("System.err");

        PatternLayout layout = new PatternLayout();
        appender.setLayout(layout);
        layout.setPattern("%msg%n%ex");
        layout.setContext(context);

        layout.start();
        appender.start();
    }

    private class GradleFilter extends TurboFilter {
        @Override
        public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
            Level loggerLevel = logger.getEffectiveLevel();
            if (loggerLevel == Level.INFO && (level == Level.INFO || level == Level.WARN)
                    || level == Level.INFO && (loggerLevel == Level.INFO || loggerLevel == Level.WARN)) {
                // Need to take into account Gradle's LIFECYCLE and QUIET markers. Whether those are set can only be determined
                // for the global log level, but not for the logger's log level (at least not without walking the logger's
                // hierarchy, which is something that Logback is designed to avoid for performance reasons).
                // Hence we base our decision on the global log level.
                LogLevel eventLevel = LogLevelConverter.toGradleLogLevel(level, marker);
                return eventLevel.compareTo(currentLevel) >= 0 ? FilterReply.ACCEPT : FilterReply.DENY;
            }

            return level.isGreaterOrEqual(loggerLevel) ? FilterReply.ACCEPT : FilterReply.DENY;
        }
    }

    private class GradleAppender extends AppenderBase<ILoggingEvent> {
        @Override
        protected void append(ILoggingEvent event) {
            try {
                ThrowableProxy throwableProxy = (ThrowableProxy) event.getThrowableProxy();
                Throwable throwable = throwableProxy == null ? null : throwableProxy.getThrowable();
                String message = event.getFormattedMessage();
                LogLevel level = LogLevelConverter.toGradleLogLevel(event.getLevel(), event.getMarker());
                outputEventListener.onOutput(new LogEvent(event.getTimeStamp(), event.getLoggerName(), level, message, throwable));
            } catch (Throwable t) {
                // fall back to standard out
                t.printStackTrace(defaultStandardOut);
            }
        }
    }
}
