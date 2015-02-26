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

import org.gradle.logging.internal.LogEvent;
import org.gradle.logging.internal.OutputEvent;
import org.gradle.logging.internal.OutputEventListener;
import org.slf4j.ILoggerFactory;

import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.gradle.api.logging.LogLevel.INFO;
import static org.slf4j.Logger.ROOT_LOGGER_NAME;

public class OutputEventListenerBackedLoggerContext implements ILoggerFactory {

    private final Map<String, OutputEventListenerBackedLogger> loggers = new ConcurrentHashMap<String, OutputEventListenerBackedLogger>();
    private final OutputEventListenerBackedLogger root;
    private final PrintStream defaultOutputStream;

    private OutputEventListener outputEventListener;

    public OutputEventListenerBackedLoggerContext(PrintStream defaultOutputStream) {
        this.defaultOutputStream = defaultOutputStream;
        assignDefaultOutputEventListener();
        root = new OutputEventListenerBackedLogger(ROOT_LOGGER_NAME, null, this);
        root.setLevel(INFO);
    }

    private void assignDefaultOutputEventListener() {
        outputEventListener = new StreamBackedOutputEventListener(defaultOutputStream);
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
        assignDefaultOutputEventListener();
        root.reset();
    }

    private static class StreamBackedOutputEventListener implements OutputEventListener {

        private final PrintStream stream;

        public StreamBackedOutputEventListener(PrintStream stream) {
            this.stream = stream;
        }
        @Override
        public void onOutput(OutputEvent event) {
            if (event instanceof LogEvent) {
                LogEvent logEvent = (LogEvent) event;
                stream.print(logEvent.getLogLevel());
                stream.print(" ");
                stream.print(logEvent.getCategory());
                stream.print(" - ");
                if (logEvent.getMessage() != null) {
                    stream.println(logEvent.getMessage());
                }
                if (logEvent.getThrowable() != null) {
                    logEvent.getThrowable().printStackTrace(stream);
                }
            }
        }
    }
}
