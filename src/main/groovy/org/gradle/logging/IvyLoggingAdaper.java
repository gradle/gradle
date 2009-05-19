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
package org.gradle.logging;

import org.apache.ivy.util.AbstractMessageLogger;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logging;

/**
 * This class is for integrating Ivy log statements into our logging system. We don't want to have a dependency on logback.
 * This would be bad for embedded usage. We only want one on slf4j. But slf4j has no constants for log levels. As we
 * want to avoid the execution of if statements for each Ivy request, we use Map which delegates Ivy log statements to
 * Sl4j action classes. 
 *
 * @author Hans Dockter
 */
public class IvyLoggingAdaper extends AbstractMessageLogger {

    public void log(String msg, int level) {
        Logging.ANT_IVY_2_SLF4J_LEVEL_MAPPER.get(level).log(msg);
    }

    public void rawlog(String msg, int level) {
        log(msg, level);
    }

    public void doProgress() { }

    public void doEndProgress(String msg) {
        LogLevel.INFO.log(msg);
    }

}
