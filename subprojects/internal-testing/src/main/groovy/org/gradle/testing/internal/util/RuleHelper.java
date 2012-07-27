/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.testing.internal.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class RuleHelper {
    public static <T> T getField(Object target, Class<T> type) {
        T value = findField(target, type);
        if (value != null) {
            return value;
        }
        throw new RuntimeException(String.format("Cannot find a field of type %s for test class %s.",
                type.getSimpleName(), target.getClass().getSimpleName()));
    }

    public static <T> T findField(Object target, Class<T> type) {
        List<T> matches = new ArrayList<T>();
        for (Class<?> cl = target.getClass(); cl != Object.class; cl = cl.getSuperclass()) {
            for (Field field : cl.getDeclaredFields()) {
                if (type.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    try {
                        matches.add(type.cast(field.get(target)));
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        if (matches.size() > 1) {
            throw new RuntimeException(String.format("Multiple %s fields found for test class %s.",
                    type.getSimpleName(), target.getClass().getSimpleName()));
        }
        return matches.get(0);
    }

    /**
     * Resets logback to a reasonable base state for tests.
     *
     * This lives here because it needs to be invoked by rules that emit logging (e.g. HttpServer)
     *
     * @see ResetLogbackLogging
     */
    public static void resetLogbackLogging() {
        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.WARN);

        context.getLogger("org.mortbay.log").setLevel(Level.OFF);
        context.getLogger("org.apache.http.wire").setLevel(Level.OFF);

        ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<ILoggingEvent>();
        rootLogger.addAppender(appender);
        appender.setContext(context);
        appender.setTarget("System.out");

        PatternLayout layout = new PatternLayout();
        appender.setLayout(layout);
        layout.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        layout.setContext(context);

        layout.start();
        appender.start();
    }

}
