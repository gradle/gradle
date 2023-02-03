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

package org.slf4j.impl;

import org.gradle.internal.logging.slf4j.OutputEventListenerBackedLoggerContext;
import org.gradle.internal.time.Time;
import org.slf4j.ILoggerFactory;
import org.slf4j.spi.LoggerFactoryBinder;

@SuppressWarnings("UnusedDeclaration")
public class StaticLoggerBinder implements LoggerFactoryBinder {

    private static final StaticLoggerBinder SINGLETON = new StaticLoggerBinder();
    private static final String LOGGER_FACTORY_CLASS_STR = OutputEventListenerBackedLoggerContext.class.getName();

    private final OutputEventListenerBackedLoggerContext factory = new OutputEventListenerBackedLoggerContext(Time.clock());

    public static StaticLoggerBinder getSingleton() {
        return SINGLETON;
    }

    /**
     * Declare the version of the SLF4J API this implementation is compiled against.
     * The value of this field is usually modified with each release.
     */
    public static final String REQUESTED_API_VERSION = "1.7.28";

    @Override
    public ILoggerFactory getLoggerFactory() {
        return factory;
    }

    @Override
    public String getLoggerFactoryClassStr() {
        return LOGGER_FACTORY_CLASS_STR;
    }
}
