/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.nativeplatform.NoOpTerminalDetector;
import org.gradle.logging.internal.LoggingConfigurer;
import org.gradle.logging.internal.OutputEventRenderer;

/**
 * Simple Logback configurer meant to be used in embedded mode
 * in 'safe' environment (e.g. own class loader).
 *
 * by Szczepan Faber, created at: 1/23/12
 */
public class SimpleLogbackLoggingConfigurer implements LoggingConfigurer {
    private final OutputEventRenderer renderer = new OutputEventRenderer(new NoOpTerminalDetector(), null);
    private final LoggingConfigurer configurer = new LogbackLoggingConfigurer(renderer);

    public SimpleLogbackLoggingConfigurer() {
        renderer.addStandardOutputAndError();
    }

    public void configure(LogLevel logLevel) {
        renderer.configure(logLevel);
        configurer.configure(logLevel);
    }
}
