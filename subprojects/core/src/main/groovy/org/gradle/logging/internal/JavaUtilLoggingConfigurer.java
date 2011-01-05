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

package org.gradle.logging.internal;

import org.gradle.api.logging.LogLevel;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * A {@link org.gradle.logging.internal.LoggingConfigurer} which configures JUL to route logging events to SLF4J.
 */
public class JavaUtilLoggingConfigurer implements LoggingConfigurer {
    private boolean configured;

    public void configure(LogLevel logLevel) {
        if (configured) {
            return;
        }

        LogManager.getLogManager().reset();
        SLF4JBridgeHandler.install();
        Logger.getLogger("").setLevel(java.util.logging.Level.FINE);
        configured = true;
    }
}
