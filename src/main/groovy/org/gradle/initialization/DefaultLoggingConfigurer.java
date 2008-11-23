/*
 * Copyright 2007-2008 the original author or authors.
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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.filter.LevelFilter;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import org.apache.ivy.util.Message;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logging;
import org.gradle.logging.IvyLoggingAdaper;
import org.gradle.logging.MarkerFilter;
import org.slf4j.LoggerFactory;

/**
 * @author Hans Dockter
 */
public class DefaultLoggingConfigurer implements LoggingConfigurer {
    public void configure(LogLevel logLevel) {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        lc.shutdownAndReset();
        ch.qos.logback.classic.Logger rootLogger = lc.getLogger(LoggerContext.ROOT_NAME);

        ConsoleAppender errorConsoleAppender = new ConsoleAppender();
        errorConsoleAppender.setContext(lc);
        errorConsoleAppender.setTarget("System.err");
        errorConsoleAppender.addFilter(createLevelFilter(lc, Level.ERROR, FilterReply.ACCEPT, FilterReply.DENY));
        Level level = Level.INFO;
        ConsoleAppender nonErrorConsoleAppender = new ConsoleAppender();
        nonErrorConsoleAppender.setContext(lc);

        setLayouts(logLevel, errorConsoleAppender, nonErrorConsoleAppender, lc);

        MarkerFilter quietFilter = new MarkerFilter(Logging.QUIET, FilterReply.DENY);
        nonErrorConsoleAppender.addFilter(quietFilter);
        if (!(logLevel == LogLevel.QUIET)) {
            quietFilter.setOnMismatch(FilterReply.NEUTRAL);
            if (logLevel == LogLevel.DEBUG) {
                level = Level.DEBUG;
                nonErrorConsoleAppender.addFilter(createLevelFilter(lc, Level.INFO, FilterReply.ACCEPT, FilterReply.NEUTRAL));
                nonErrorConsoleAppender.addFilter(createLevelFilter(lc, Level.DEBUG, FilterReply.ACCEPT, FilterReply.NEUTRAL));
            } else {
                if (logLevel == LogLevel.INFO) {
                    level = Level.INFO;
                    nonErrorConsoleAppender.addFilter(createLevelFilter(lc, Level.INFO, FilterReply.ACCEPT, FilterReply.NEUTRAL));
                } else {
                    nonErrorConsoleAppender.addFilter(new MarkerFilter(Logging.LIFECYCLE));
                }
            }
            nonErrorConsoleAppender.addFilter(createLevelFilter(lc, Level.WARN, FilterReply.ACCEPT, FilterReply.DENY));
        }
        rootLogger.addAppender(nonErrorConsoleAppender);
        nonErrorConsoleAppender.start();
        rootLogger.addAppender(errorConsoleAppender);
        errorConsoleAppender.start();
        Message.setDefaultLogger(new IvyLoggingAdaper());
        rootLogger.setLevel(level);
    }

    private void setLayouts(LogLevel logLevel, ConsoleAppender errorConsoleAppender, ConsoleAppender nonErrorConsoleAppender, LoggerContext lc) {
        String debugLayout = "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n";
        String infoLayout = "%msg%n";
        if (logLevel == LogLevel.DEBUG) {
            nonErrorConsoleAppender.setLayout(createPatternLayout(lc, debugLayout));
            errorConsoleAppender.setLayout(createPatternLayout(lc, debugLayout));
        } else {
            nonErrorConsoleAppender.setLayout(createPatternLayout(lc, infoLayout));
            errorConsoleAppender.setLayout(createPatternLayout(lc, infoLayout));
        }
    }

    private Filter createLevelFilter(LoggerContext lc, Level level, FilterReply onMatch, FilterReply onMismatch) {
        LevelFilter levelFilter = new LevelFilter();
        levelFilter.setContext(lc);
        levelFilter.setOnMatch(onMatch);
        levelFilter.setOnMismatch(onMismatch);
        levelFilter.setLevel(level.toString());
        levelFilter.start();
        return levelFilter;
    }

    private PatternLayout createPatternLayout(LoggerContext loggerContext, String pattern) {
        PatternLayout patternLayout = new PatternLayout();
        patternLayout.setPattern(pattern);
        patternLayout.setContext(loggerContext);
        patternLayout.start();
        return patternLayout;
    }
}
