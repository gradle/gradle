/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.logging.internal.slf4j;

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.Actions;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.logging.internal.OutputEventRenderer;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OutputEventListenerBackedLoggerContext implements ILoggerFactory {

    private static final LogLevel DEFAULT_LOG_LEVEL = LogLevel.LIFECYCLE;

    static final String HTTP_CLIENT_WIRE_LOGGER_NAME = "org.apache.http.wire";
    static final String META_INF_EXTENSION_MODULE_LOGGER_NAME = "org.codehaus.groovy.runtime.m12n.MetaInfExtensionModule";

    private final Map<String, Logger> loggers = new ConcurrentHashMap<String, Logger>();
    private final OutputStream defaultOutputStream;
    private final OutputStream defaultErrorStream;
    private volatile LogLevel level;

    private volatile OutputEventListener outputEventListener;

    public OutputEventListenerBackedLoggerContext(OutputStream defaultOutputStream, OutputStream defaultErrorStream) {
        this.defaultOutputStream = defaultOutputStream;
        this.defaultErrorStream = defaultErrorStream;
        level = DEFAULT_LOG_LEVEL;
        setDefaultOutputEventListener();
        applyDefaultLoggersConfig();
    }

    private void applyDefaultLoggersConfig() {
        addNoOpLogger(HTTP_CLIENT_WIRE_LOGGER_NAME);
        addNoOpLogger(META_INF_EXTENSION_MODULE_LOGGER_NAME);
    }

    private void addNoOpLogger(String name) {
        loggers.put(name, new NoOpLogger(name));
    }

    private void setDefaultOutputEventListener() {
        OutputEventRenderer renderer = new OutputEventRenderer(Actions.doNothing());
        renderer.addStandardOutputListener(defaultOutputStream);
        renderer.addStandardErrorListener(defaultErrorStream);
        outputEventListener = renderer;
    }

    public void setOutputEventListener(OutputEventListener outputEventListener) {
        this.outputEventListener = outputEventListener;
    }

    public OutputEventListener getOutputEventListener() {
        return outputEventListener;
    }

    public Logger getLogger(String name) {
        Logger logger = loggers.get(name);
        if (logger != null) {
            return logger;
        }

        synchronized (loggers) {
            logger = loggers.get(name);
            if (logger == null) {
                logger = new OutputEventListenerBackedLogger(name, this);
                loggers.put(name, logger);
            }
            return logger;
        }
    }

    public void reset() {
        level = DEFAULT_LOG_LEVEL;
        setDefaultOutputEventListener();
    }

    public LogLevel getLevel() {
        return level;
    }

    public void setLevel(LogLevel level) {
        if (level == null) {
            throw new IllegalArgumentException("Global log level cannot be set to null");
        }
        this.level = level;
    }
}
