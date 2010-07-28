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

package org.gradle.logging;

import org.gradle.api.internal.project.DefaultServiceRegistry;

/**
 * A {@link org.gradle.api.internal.project.ServiceRegistry} implementation which provides the logging services.
 */
public class LoggingServiceRegistry extends DefaultServiceRegistry {
    protected LoggingManagerFactory createLoggingManagerFactory() {
        Slf4jLoggingConfigurer slf4jLoggingConfigurer = createSlf4jLoggingConfigurer();
        LoggingConfigurer loggingConfigurer = new DefaultLoggingConfigurer(slf4jLoggingConfigurer,
                new JavaUtilLoggingConfigurer());
        return new DefaultLoggingManagerFactory(loggingConfigurer, slf4jLoggingConfigurer);
    }

    protected Slf4jLoggingConfigurer createSlf4jLoggingConfigurer() {
        return new Slf4jLoggingConfigurer();
    }
}
