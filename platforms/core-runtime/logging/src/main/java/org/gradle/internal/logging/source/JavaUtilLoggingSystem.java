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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.ConsoleHandler;
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
    private final SLF4JBridgeHandler bridge = new SLF4JBridgeHandler();
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
            uninstall(snapshot.consoleAndBridgeHandlers, snapshot.javaUtilLevel);
        }
    }

    @Override
    public Snapshot snapshot() {
        return new JulSnapshot(installed, consoleAndBridgeHandlers(), logger.getLevel(), requestedLevel);
    }

    private void uninstall(Iterable<Handler> toRestore, Level level) {
        if (!installed) {
            return;
        }

        logger.removeHandler(bridge);
        addHandlers(toRestore);

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
            // To avoid duplicate logging events, remove the console handler
            // installed by default, if present, and remove any slf4j bridge
            // handlers installed by a parent instance of this logging system.
            removeHandlers(consoleAndBridgeHandlers());
            logger.addHandler(bridge);
            installed = true;
        }

        logger.setLevel(level);
    }

    /**
     * Adds the given handlers to the root logger, skipping any that are already attached.
     */
    private void addHandlers(Iterable<Handler> toAdd) {
        Handler[] attached = logger.getHandlers();
        for (Handler handler : toAdd) {
            if (!contains(attached, handler)) {
                logger.addHandler(handler);
            }
        }
    }

    private void removeHandlers(Iterable<Handler> toRemove) {
        for (Handler handler : toRemove) {
            logger.removeHandler(handler);
        }
    }

    /**
     * The set of handlers that are managed by this logging system.
     * <p>
     * The ConsoleHandler is installed by the JDK's default LogManager, but may not be present
     * if the user provided a custom LogManager. The SLF4JBridgeHandler is installed by this
     * logging system, so by definition is managed by this logging system. Other handlers may be
     * present but are not managed by this logging system and are therefore left in place
     * during start and restore operations.
     */
    private List<Handler> consoleAndBridgeHandlers() {
        List<Handler> handlers = new ArrayList<>();
        for (Handler handler : logger.getHandlers()) {
            if (handler instanceof ConsoleHandler || handler instanceof SLF4JBridgeHandler) {
                handlers.add(handler);
            }
        }
        return handlers;
    }

    private static class JulSnapshot implements Snapshot {

        private final boolean installed;
        private final List<Handler> consoleAndBridgeHandlers;
        private final Level javaUtilLevel;
        private final @Nullable LogLevel requestedLevel;

        JulSnapshot(
            boolean installed,
            List<Handler> consoleAndBridgeHandlers,
            Level javaUtilLevel,
            @Nullable LogLevel requestedLevel
        ) {
            this.installed = installed;
            this.consoleAndBridgeHandlers = consoleAndBridgeHandlers;
            this.javaUtilLevel = javaUtilLevel;
            this.requestedLevel = requestedLevel;
        }

    }

}
