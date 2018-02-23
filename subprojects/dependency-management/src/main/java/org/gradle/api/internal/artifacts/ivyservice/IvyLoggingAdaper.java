/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.util.AbstractMessageLogger;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.logging.LogLevelMapping;

/**
 * This class is for integrating Ivy log statements into our logging system. We don't want to have a dependency on
 * logback. This would be bad for embedded usage. We only want one on slf4j. But slf4j has no constants for log levels.
 * As we want to avoid the execution of if statements for each Ivy request, we use Map which delegates Ivy log
 * statements to Sl4j action classes.
 */
public class IvyLoggingAdaper extends AbstractMessageLogger {
    private final Logger logger = Logging.getLogger(IvyLoggingAdaper.class);

    public void log(String msg, int level) {
        logger.log(LogLevelMapping.ANT_IVY_2_SLF4J.get(level), msg);
    }

    public void rawlog(String msg, int level) {
        log(msg, level);
    }

    /**
     * Overrides the default implementation, which doesn't delegate to {@link #log(String, int)}.
     */
    @Override
    public void warn(String msg) {
        logger.warn(msg);
    }

    /**
     * Overrides the default implementation, which doesn't delegate to {@link #log(String, int)}.
     */
    @Override
    public void error(String msg) {
        logger.error(msg);
    }

    public void doProgress() {
    }

    public void doEndProgress(String msg) {
        logger.info(msg);
    }
}
