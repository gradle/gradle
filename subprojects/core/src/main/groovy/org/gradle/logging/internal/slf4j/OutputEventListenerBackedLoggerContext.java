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

import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.slf4j.Logger.ROOT_LOGGER_NAME;

public class OutputEventListenerBackedLoggerContext implements ILoggerFactory {

    private final Map<String, OutputEventListenerBackedLogger> loggers = new ConcurrentHashMap<String, OutputEventListenerBackedLogger>();
    private final OutputEventListenerBackedLogger root;
    private final OutputStream defaultOutputStream;
    private final OutputStream defaultErrorStream;

    private OutputEventListener outputEventListener;

    public OutputEventListenerBackedLoggerContext(OutputStream defaultOutputStream, OutputStream defaultErrorStream) {
        this.defaultOutputStream = defaultOutputStream;
        this.defaultErrorStream = defaultErrorStream;
        setDefaultOutputEventListener();
        root = new OutputEventListenerBackedLogger(ROOT_LOGGER_NAME, null, this);
        root.setLevel(LogLevel.LIFECYCLE);
        configureDefaultLevels();
    }

    private void configureDefaultLevels() {
        getLogger("org.apache.http.wire").disable();
        getLogger("org.codehaus.groovy.runtime.m12n.MetaInfExtensionModule").setLevel(LogLevel.ERROR);
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

    public OutputEventListenerBackedLogger getLogger(String name) {
        if (ROOT_LOGGER_NAME.equals(name)) {
            return root;
        }

        OutputEventListenerBackedLogger childLogger = loggers.get(name);
        if (childLogger != null) {
            return childLogger;
        }

        OutputEventListenerBackedLogger logger = root;
        int separatorIndex = 0;

        while (true) {
            int nextSeparatorIndex = OutputEventListenerBackedLogger.getSeparatorIndex(name, separatorIndex);
            String childName = nextSeparatorIndex == -1 ? name : name.substring(0, nextSeparatorIndex);
            separatorIndex = nextSeparatorIndex + 1;

            synchronized (logger) {
                childLogger = logger.getChildByName(childName);
                if (childLogger == null) {
                    childLogger = logger.createChildByName(childName);
                    loggers.put(childName, childLogger);
                }
            }

            logger = childLogger;
            if (nextSeparatorIndex == -1) {
                return childLogger;
            }
        }
    }

    public void reset() {
        setDefaultOutputEventListener();
        root.reset();
        root.setLevel(LogLevel.LIFECYCLE);
    }
}
