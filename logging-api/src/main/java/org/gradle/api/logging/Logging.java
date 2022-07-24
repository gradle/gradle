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

package org.gradle.api.logging;

import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * <p>The main entry point for Gradle's logging system. Gradle routes all logging via SLF4J. You can use either an SLF4J
 * {@link org.slf4j.Logger} or a Gradle {@link Logger} to perform logging.</p>
 */
public class Logging {
    public static final Marker LIFECYCLE = MarkerFactory.getDetachedMarker("LIFECYCLE");
    public static final Marker QUIET = MarkerFactory.getDetachedMarker("QUIET");

    /**
     * Returns the logger for the given class.
     *
     * @param c the class.
     * @return the logger. Never returns null.
     */
    @SuppressWarnings("rawtypes")
    public static Logger getLogger(Class c) {
        return (Logger) LoggerFactory.getLogger(c);
    }

    /**
     * Returns the logger with the given name.
     *
     * @param name the logger name.
     * @return the logger. Never returns null.
     */
    public static Logger getLogger(String name) {
        return (Logger) LoggerFactory.getLogger(name);
    }
}
