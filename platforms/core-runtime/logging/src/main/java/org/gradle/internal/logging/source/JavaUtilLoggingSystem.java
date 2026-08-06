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

package org.gradle.internal.logging.source;

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.config.LoggingSourceSystem;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link LoggingSourceSystem} which configures JUL to route logging events to SLF4J.
 */
@NullMarked
public class JavaUtilLoggingSystem implements LoggingSourceSystem {

    private static final Map<LogLevel, Level> LOG_LEVEL_MAPPING = new HashMap<LogLevel, Level>();

    // Gradle's log levels correspond to slf4j log levels
    // as implemented in OutputEventListenerBackedLogger.
    // These levels are mapped to java.util.logging.Levels
    // corresponding to the mapping implemented in the
    // SLF4JBridgeHandler which is installed by this logging system.
    static {
        LOG_LEVEL_MAPPING.put(LogLevel.DEBUG, Level.FINE);
        LOG_LEVEL_MAPPING.put(LogLevel.INFO, Level.CONFIG);
        LOG_LEVEL_MAPPING.put(LogLevel.LIFECYCLE, Level.WARNING);
        LOG_LEVEL_MAPPING.put(LogLevel.WARN, Level.WARNING);
        LOG_LEVEL_MAPPING.put(LogLevel.QUIET, Level.SEVERE);
        LOG_LEVEL_MAPPING.put(LogLevel.ERROR, Level.SEVERE);
    }

    private final Logger logger;
    private @Nullable LogLevel requestedLevel;
    private boolean installed;

    public JavaUtilLoggingSystem() {
        logger = Logger.getLogger("");
    }

    @Override
    public Snapshot setLevel(LogLevel logLevel) {
        Snapshot snapshot = snapshot();
        if (logLevel != requestedLevel) {
            requestedLevel = logLevel;
            if (installed) {
                logger.setLevel(LOG_LEVEL_MAPPING.get(logLevel));
            }
        }
        return snapshot;
    }

    @Override
    public Snapshot startCapture() {
        Snapshot snapshot = snapshot();
        install(LOG_LEVEL_MAPPING.get(requestedLevel));
        return snapshot;
    }

    @Override
    public void restore(Snapshot state) {
        JulSnapshot snapshot = (JulSnapshot) state;
        requestedLevel = snapshot.requestedLevel;
        if (snapshot.installed) {
            install(snapshot.javaUtilLevel);
        } else {
            uninstall(snapshot.handlers, snapshot.javaUtilLevel);
        }
    }

    @Override
    public Snapshot snapshot() {
        return new JulSnapshot(installed, logger.getHandlers(), logger.getLevel(), requestedLevel);
    }

    private void uninstall(Handler[] newHandlers, Level level) {
        if (!installed) {
            return;
        }

        Handler[] uninstalled = replaceHandlers(newHandlers);

        // Close the handlers that were installed while capturing was active, mirroring LogManager's
        // behavior of closing attached handlers when the process exits.
        for (Handler displaced : uninstalled) {
            if (!contains(newHandlers, displaced)) {
                displaced.close();
            }
        }

        logger.setLevel(level);
        installed = false;
    }

    private static boolean contains(Handler[] handlers, Handler handler) {
        for (Handler candidate : handlers) {
            if (candidate == handler) {
                return true;
            }
        }
        return false;
    }

    private void install(Level level) {
        if (!installed) {
            replaceHandlers(new Handler[]{new SLF4JBridgeHandler()});
            installed = true;
        }

        logger.setLevel(level);
    }

    private Handler[] replaceHandlers(Handler[] handlers) {
        Handler[] displaced = logger.getHandlers();
        for (Handler handler : displaced) {
            logger.removeHandler(handler);
        }
        for (Handler handler : handlers) {
            logger.addHandler(handler);
        }
        return displaced;
    }

    private static class JulSnapshot implements Snapshot {

        private final boolean installed;
        private final Handler[] handlers;
        private final Level javaUtilLevel;
        private final @Nullable LogLevel requestedLevel;

        JulSnapshot(
            boolean installed,
            Handler[] handlers,
            Level javaUtilLevel,
            @Nullable LogLevel requestedLevel
        ) {
            this.installed = installed;
            this.handlers = handlers;
            this.javaUtilLevel = javaUtilLevel;
            this.requestedLevel = requestedLevel;
        }

    }

}
