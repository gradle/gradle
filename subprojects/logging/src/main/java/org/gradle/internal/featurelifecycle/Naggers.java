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

package org.gradle.internal.featurelifecycle;

import org.gradle.internal.Factory;

public class Naggers {
    public static final String ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME = "org.gradle.deprecation.trace";

    private static final UsageNagger NAGGER = new UsageNagger(UsageNagger.class);
    private static boolean traceLoggingEnabled;

    public static DeprecationNagger getDeprecationNagger() {
        return NAGGER;
    }

    public static IncubationNagger getIncubationNagger() {
        return NAGGER;
    }

    public static BasicNagger getBasicNagger() {
        return NAGGER;
    }

    public static void useLocationReporter(UsageLocationReporter reporter) {
        NAGGER.useLocationReporter(reporter);
    }

    public static <T> T whileDisabled(Factory<T> factory) {
        return NAGGER.whileDisabled(factory);
    }

    public static void whileDisabled(Runnable action) {
        NAGGER.whileDisabled(action);
    }

    public static void reset() {
        NAGGER.reset();
    }

    /**
     * Whether or not nagging should print a full stack trace.
     *
     * This property can be overridden by setting the ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME
     * system property.
     *
     * @param traceLoggingEnabled if trace logging should be enabled.
     */
    public static void setTraceLoggingEnabled(boolean traceLoggingEnabled) {
        Naggers.traceLoggingEnabled = traceLoggingEnabled;
    }

    static boolean isTraceLoggingEnabled() {
        String value = System.getProperty(ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME);
        if(value == null) {
            return traceLoggingEnabled;
        }
        return Boolean.parseBoolean(value);
    }
}
