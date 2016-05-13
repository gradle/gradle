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

package org.gradle.api.internal.tasks.testing;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.internal.logging.StandardOutputCapture;
import org.gradle.api.internal.tasks.testing.processors.DefaultStandardOutputRedirector;
import org.gradle.util.SingleMessageLogger;

import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Some hackery to get JUL output redirected to test output
 */
public class JULRedirector extends DefaultStandardOutputRedirector {
    @VisibleForTesting
    public static final String READ_LOGGING_CONFIG_FILE_PROPERTY = "org.gradle.readLoggingConfigFile";
    private boolean reset;

    @Override
    public StandardOutputCapture start() {
        super.start();
        boolean shouldReadLoggingConfigFile = System.getProperty(READ_LOGGING_CONFIG_FILE_PROPERTY, "true").equals("true");
        if (!shouldReadLoggingConfigFile) {
            SingleMessageLogger.nagUserOfDiscontinuedProperty(READ_LOGGING_CONFIG_FILE_PROPERTY,
                "Change your test to work with your java.util.logging configuration file settings.");
        }
        if (!reset) {
            LogManager.getLogManager().reset();
            if (shouldReadLoggingConfigFile) {
                try {
                    LogManager.getLogManager().readConfiguration();
                } catch (IOException error) {
                    Logger.getLogger("").addHandler(new ConsoleHandler());
                }
            } else {
                Logger.getLogger("").addHandler(new ConsoleHandler());
            }
            reset = true;
        }
        return this;
    }
}
