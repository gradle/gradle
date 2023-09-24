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
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.sink.OutputEventRenderer;
import org.gradle.internal.time.Clock;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.Marker;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

public class OutputEventListenerBackedLoggerContext implements ILoggerFactory {

    private static final LogLevel DEFAULT_LOG_LEVEL = LogLevel.LIFECYCLE;

    static final String HTTP_CLIENT_WIRE_LOGGER_NAME = "org.apache.http.wire";
    static final String META_INF_EXTENSION_MODULE_LOGGER_NAME = "org.codehaus.groovy.runtime.m12n.MetaInfExtensionModule";
    private static final String GROOVY_VM_PLUGIN_FACTORY = "org.codehaus.groovy.vmplugin.VMPluginFactory";

    private final ConcurrentMap<String, Logger> loggers = new ConcurrentHashMap<String, Logger>();
    private final AtomicReference<LogLevel> level = new AtomicReference<LogLevel>();
    private final AtomicReference<OutputEventListener> outputEventListener = new AtomicReference<OutputEventListener>();
    private final Clock clock;

    public OutputEventListenerBackedLoggerContext(Clock clock) {
        this.clock = clock;
        applyDefaultLoggersConfig();
        reset();
    }

    private void applyDefaultLoggersConfig() {
        addNoOpLogger("java.lang.ProcessBuilder");
        addNoOpLogger(HTTP_CLIENT_WIRE_LOGGER_NAME);
        addNoOpLogger("org.apache.http.headers");
        addNoOpLogger(META_INF_EXTENSION_MODULE_LOGGER_NAME);
        addNoOpLogger("org.littleshoot.proxy.HttpRequestHandler");
        // We ignore logging from here because this is when the Groovy runtime is initialized.
        // This may happen in BuildOperationTrace, and then the logging from the plugin factory would go into the build operation trace again.
        // That then will fail because we can't use JsonOutput in BuildOperationTrace when the Groovy VM hasn't been initialized.
        addNoOpLogger(GROOVY_VM_PLUGIN_FACTORY);
    }

    private void addNoOpLogger(String name) {
        loggers.put(name, new NoOpLogger(name));
    }

    public void setOutputEventListener(OutputEventListener outputEventListener) {
        this.outputEventListener.set(outputEventListener);
    }

    public OutputEventListener getOutputEventListener() {
        return outputEventListener.get();
    }

    @Override
    public Logger getLogger(String name) {
        Logger logger = loggers.get(name);
        if (logger != null) {
            return logger;
        }

        logger = loggers.putIfAbsent(name, new OutputEventListenerBackedLogger(name, this, clock));
        return logger != null ? logger : loggers.get(name);
    }

    public void reset() {
        setLevel(DEFAULT_LOG_LEVEL);
        OutputEventRenderer renderer = new OutputEventRenderer(clock);
        renderer.attachSystemOutAndErr();
        setOutputEventListener(renderer);
    }

    public LogLevel getLevel() {
        return level.get();
    }

    public void setLevel(LogLevel level) {
        if (level == null) {
            throw new IllegalArgumentException("Global log level cannot be set to null");
        }
        this.level.set(level);
    }

    private static class NoOpLogger implements org.gradle.api.logging.Logger {

        private final String name;

        public NoOpLogger(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isTraceEnabled() {
            return false;
        }

        @Override
        public void trace(String msg) {
        }

        @Override
        public void trace(String format, Object arg) {
        }

        @Override
        public void trace(String format, Object arg1, Object arg2) {
        }

        @Override
        public void trace(String format, Object... arguments) {
        }

        @Override
        public void trace(String msg, Throwable t) {
        }

        @Override
        public boolean isTraceEnabled(Marker marker) {
            return false;
        }

        @Override
        public void trace(Marker marker, String msg) {
        }

        @Override
        public void trace(Marker marker, String format, Object arg) {
        }

        @Override
        public void trace(Marker marker, String format, Object arg1, Object arg2) {
        }

        @Override
        public void trace(Marker marker, String format, Object... argArray) {
        }

        @Override
        public void trace(Marker marker, String msg, Throwable t) {
        }

        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public void debug(String msg) {
        }

        @Override
        public void debug(String format, Object arg) {
        }

        @Override
        public void debug(String format, Object arg1, Object arg2) {
        }

        @Override
        public boolean isLifecycleEnabled() {
            return false;
        }

        @Override
        public void debug(String format, Object... arguments) {
        }

        @Override
        public void lifecycle(String message) {
        }

        @Override
        public void lifecycle(String message, Object... objects) {
        }

        @Override
        public void lifecycle(String message, Throwable throwable) {
        }

        @Override
        public void debug(String msg, Throwable t) {
        }

        @Override
        public boolean isDebugEnabled(Marker marker) {
            return false;
        }

        @Override
        public void debug(Marker marker, String msg) {
        }

        @Override
        public void debug(Marker marker, String format, Object arg) {
        }

        @Override
        public void debug(Marker marker, String format, Object arg1, Object arg2) {
        }

        @Override
        public void debug(Marker marker, String format, Object... arguments) {
        }

        @Override
        public void debug(Marker marker, String msg, Throwable t) {
        }

        @Override
        public boolean isInfoEnabled() {
            return false;
        }

        @Override
        public void info(String msg) {
        }

        @Override
        public void info(String format, Object arg) {
        }

        @Override
        public void info(String format, Object arg1, Object arg2) {
        }

        @Override
        public void info(String format, Object... arguments) {
        }

        @Override
        public boolean isQuietEnabled() {
            return false;
        }

        @Override
        public void quiet(String message) {
        }

        @Override
        public void quiet(String message, Object... objects) {
        }

        @Override
        public void quiet(String message, Throwable throwable) {
        }

        @Override
        public boolean isEnabled(LogLevel level) {
            return false;
        }

        @Override
        public void log(LogLevel level, String message) {
        }

        @Override
        public void log(LogLevel level, String message, Object... objects) {
        }

        @Override
        public void log(LogLevel level, String message, Throwable throwable) {
        }

        @Override
        public void info(String msg, Throwable t) {
        }

        @Override
        public boolean isInfoEnabled(Marker marker) {
            return false;
        }

        @Override
        public void info(Marker marker, String msg) {
        }

        @Override
        public void info(Marker marker, String format, Object arg) {
        }

        @Override
        public void info(Marker marker, String format, Object arg1, Object arg2) {
        }

        @Override
        public void info(Marker marker, String format, Object... arguments) {
        }

        @Override
        public void info(Marker marker, String msg, Throwable t) {
        }

        @Override
        public boolean isWarnEnabled() {
            return false;
        }

        @Override
        public void warn(String msg) {
        }

        @Override
        public void warn(String format, Object arg) {
        }

        @Override
        public void warn(String format, Object... arguments) {
        }

        @Override
        public void warn(String format, Object arg1, Object arg2) {
        }

        @Override
        public void warn(String msg, Throwable t) {
        }

        @Override
        public boolean isWarnEnabled(Marker marker) {
            return false;
        }

        @Override
        public void warn(Marker marker, String msg) {
        }

        @Override
        public void warn(Marker marker, String format, Object arg) {
        }

        @Override
        public void warn(Marker marker, String format, Object arg1, Object arg2) {
        }

        @Override
        public void warn(Marker marker, String format, Object... arguments) {
        }

        @Override
        public void warn(Marker marker, String msg, Throwable t) {
        }

        @Override
        public boolean isErrorEnabled() {
            return false;
        }

        @Override
        public void error(String msg) {
        }

        @Override
        public void error(String format, Object arg) {
        }

        @Override
        public void error(String format, Object arg1, Object arg2) {
        }

        @Override
        public void error(String format, Object... arguments) {
        }

        @Override
        public void error(String msg, Throwable t) {
        }

        @Override
        public boolean isErrorEnabled(Marker marker) {
            return false;
        }

        @Override
        public void error(Marker marker, String msg) {
        }

        @Override
        public void error(Marker marker, String format, Object arg) {
        }

        @Override
        public void error(Marker marker, String format, Object arg1, Object arg2) {
        }

        @Override
        public void error(Marker marker, String format, Object... arguments) {
        }

        @Override
        public void error(Marker marker, String msg, Throwable t) {
        }
    }
}
