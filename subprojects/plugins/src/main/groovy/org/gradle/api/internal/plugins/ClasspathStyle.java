/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.plugins;

/**
 * Determines te mechanism over which the application classpath is going to be specified.
 */
public enum ClasspathStyle {
    /**
     * The classpath will be passed on the command line, explicitly listing every path component
     * (as opposed to using wildcards).
     */
    EXPLICIT,

    /**
     * The classpath will be passed using the {@code CLASSPATH} environment variable, which
     * is picked automatically by the default JVM launcher.
     */
    ENVIRONMENT
}
