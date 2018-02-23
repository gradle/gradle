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

package org.gradle.internal.logging.services;

import org.gradle.internal.Factory;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.config.LoggingRouter;
import org.gradle.internal.logging.config.LoggingSourceSystem;

public class DefaultLoggingManagerFactory implements Factory<LoggingManagerInternal> {
    private final LoggingSourceSystem slfLoggingSystem;
    private final LoggingSourceSystem javaUtilLoggingSystem;
    private final LoggingSourceSystem stdOutLoggingSystem;
    private final LoggingSourceSystem stdErrLoggingSystem;
    private final DefaultLoggingManager rootManager;
    private final LoggingRouter loggingRouter;
    private boolean created;

    public DefaultLoggingManagerFactory(LoggingRouter loggingRouter, LoggingSourceSystem slf4j, LoggingSourceSystem javaUtilLoggingSystem, LoggingSourceSystem stdOutLoggingSystem, LoggingSourceSystem stdErrLoggingSystem) {
        this.loggingRouter = loggingRouter;
        this.slfLoggingSystem = slf4j;
        this.javaUtilLoggingSystem = javaUtilLoggingSystem;
        this.stdOutLoggingSystem = stdOutLoggingSystem;
        this.stdErrLoggingSystem = stdErrLoggingSystem;
        rootManager = newManager();
    }

    public LoggingManagerInternal getRoot() {
        return rootManager;
    }

    public LoggingManagerInternal create() {
        if (!created) {
            created = true;
            return getRoot();
        }
        return newManager();
    }

    private DefaultLoggingManager newManager() {
        return new DefaultLoggingManager(slfLoggingSystem, javaUtilLoggingSystem, stdOutLoggingSystem, stdErrLoggingSystem, loggingRouter);
    }
}
