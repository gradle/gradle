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

package org.gradle.util;

import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.Factory;
import org.gradle.internal.featurelifecycle.UsageLocationReporter;


import org.gradle.internal.featurelifecycle.UsageNagger;

// TODO: replace usages with Naggers.
@ThreadSafe
public class SingleMessageLogger {
    /*
     * This is NOT the UsageNagger instance returned by Naggers methods.
     * It has a different calledFrom!
     */
    private static final UsageNagger USAGE_NAGGER = new UsageNagger(SingleMessageLogger.class);


    // IncubationNagger
    public static void incubatingFeatureUsed(String incubatingFeature) {
        USAGE_NAGGER.incubatingFeatureUsed(incubatingFeature);
    }

    public static void incubatingFeatureUsed(String incubatingFeature, String additionalWarning) {
        USAGE_NAGGER.incubatingFeatureUsed(incubatingFeature, additionalWarning);
    }


    // DeprecationNagger
    public static void nagUserOfDeprecated(String thing) {
        USAGE_NAGGER.nagUserOfDeprecated(thing);
    }

    public static void nagUserOfDeprecated(String thing, String explanation) {
        USAGE_NAGGER.nagUserOfDeprecated(thing, explanation);
    }

    public static void nagUserOfDeprecatedBehaviour(String behaviour) {
        USAGE_NAGGER.nagUserOfDeprecatedBehaviour(behaviour);
    }

    public static void nagUserOfDiscontinuedApi(String api, String advice) {
        USAGE_NAGGER.nagUserOfDiscontinuedApi(api, advice);
    }

    public static void nagUserOfDiscontinuedMethod(String methodName) {
        USAGE_NAGGER.nagUserOfDiscontinuedMethod(methodName);
    }

    public static void nagUserOfDiscontinuedMethod(String methodName, String advice) {
        USAGE_NAGGER.nagUserOfDiscontinuedMethod(methodName, advice);
    }

    public static void nagUserOfDiscontinuedProperty(String propertyName, String advice) {
        USAGE_NAGGER.nagUserOfDiscontinuedProperty(propertyName, advice);
    }

    public static void nagUserOfPluginReplacedWithExternalOne(String pluginName, String replacement) {
        USAGE_NAGGER.nagUserOfPluginReplacedWithExternalOne(pluginName, replacement);
    }

    public static void nagUserOfReplacedMethod(String methodName, String replacement) {
        USAGE_NAGGER.nagUserOfReplacedMethod(methodName, replacement);
    }

    public static void nagUserOfReplacedNamedParameter(String parameterName, String replacement) {
        USAGE_NAGGER.nagUserOfReplacedNamedParameter(parameterName, replacement);
    }

    public static void nagUserOfReplacedPlugin(String pluginName, String replacement) {
        USAGE_NAGGER.nagUserOfReplacedPlugin(pluginName, replacement);
    }

    public static void nagUserOfReplacedProperty(String propertyName, String replacement) {
        USAGE_NAGGER.nagUserOfReplacedProperty(propertyName, replacement);
    }

    public static void nagUserOfReplacedTask(String taskName, String replacement) {
        USAGE_NAGGER.nagUserOfReplacedTask(taskName, replacement);
    }

    public static void nagUserOfReplacedTaskType(String taskName, String replacement) {
        USAGE_NAGGER.nagUserOfReplacedTaskType(taskName, replacement);
    }


    // BasicNagger
    public static void nagUserWith(String message) {
        USAGE_NAGGER.nagUserWith(message);
    }

    public static void nagUserOnceWith(String message) {
        USAGE_NAGGER.nagUserOnceWith(message);
    }

    // Naggers
    public static void reset() {
        USAGE_NAGGER.reset();
    }

    public static void useLocationReporter(UsageLocationReporter reporter) {
        USAGE_NAGGER.useLocationReporter(reporter);
    }

    public static <T> T whileDisabled(Factory<T> factory) {
        return USAGE_NAGGER.whileDisabled(factory);
    }

    public static void whileDisabled(Runnable action) {
        USAGE_NAGGER.whileDisabled(action);
    }
}
