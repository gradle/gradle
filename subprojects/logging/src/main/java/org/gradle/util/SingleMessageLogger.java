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
import org.gradle.internal.featurelifecycle.Naggers;
import org.gradle.internal.featurelifecycle.UsageLocationReporter;

// TODO: replace usages with Naggers.
@ThreadSafe
public class SingleMessageLogger {
    // IncubationNagger
    public static void incubatingFeatureUsed(String incubatingFeature) {
        Naggers.getIncubationNagger().incubatingFeatureUsed(incubatingFeature);
    }

    public static void incubatingFeatureUsed(String incubatingFeature, String additionalWarning) {
        Naggers.getIncubationNagger().incubatingFeatureUsed(incubatingFeature, additionalWarning);
    }


    // DeprecationNagger
    public static void nagUserOfDeprecated(String thing) {
        Naggers.getDeprecationNagger().nagUserOfDeprecated(thing);
    }

    public static void nagUserOfDeprecated(String thing, String explanation) {
        Naggers.getDeprecationNagger().nagUserOfDeprecated(thing, explanation);
    }

    public static void nagUserOfDeprecatedBehaviour(String behaviour) {
        Naggers.getDeprecationNagger().nagUserOfDeprecatedBehaviour(behaviour);
    }

    public static void nagUserOfDiscontinuedApi(String api, String advice) {
        Naggers.getDeprecationNagger().nagUserOfDiscontinuedApi(api, advice);
    }

    public static void nagUserOfDiscontinuedMethod(String methodName) {
        Naggers.getDeprecationNagger().nagUserOfDiscontinuedMethod(methodName);
    }

    public static void nagUserOfDiscontinuedMethod(String methodName, String advice) {
        Naggers.getDeprecationNagger().nagUserOfDiscontinuedMethod(methodName, advice);
    }

    public static void nagUserOfDiscontinuedProperty(String propertyName, String advice) {
        Naggers.getDeprecationNagger().nagUserOfDiscontinuedProperty(propertyName, advice);
    }

    public static void nagUserOfPluginReplacedWithExternalOne(String pluginName, String replacement) {
        Naggers.getDeprecationNagger().nagUserOfPluginReplacedWithExternalOne(pluginName, replacement);
    }

    public static void nagUserOfReplacedMethod(String methodName, String replacement) {
        Naggers.getDeprecationNagger().nagUserOfReplacedMethod(methodName, replacement);
    }

    public static void nagUserOfReplacedNamedParameter(String parameterName, String replacement) {
        Naggers.getDeprecationNagger().nagUserOfReplacedNamedParameter(parameterName, replacement);
    }

    public static void nagUserOfReplacedPlugin(String pluginName, String replacement) {
        Naggers.getDeprecationNagger().nagUserOfReplacedPlugin(pluginName, replacement);
    }

    public static void nagUserOfReplacedProperty(String propertyName, String replacement) {
        Naggers.getDeprecationNagger().nagUserOfReplacedProperty(propertyName, replacement);
    }

    public static void nagUserOfReplacedTask(String taskName, String replacement) {
        Naggers.getDeprecationNagger().nagUserOfReplacedTask(taskName, replacement);
    }

    public static void nagUserOfReplacedTaskType(String taskName, String replacement) {
        Naggers.getDeprecationNagger().nagUserOfReplacedTaskType(taskName, replacement);
    }


    // BasicNagger
    public static void nagUserWith(String message) {
        Naggers.getBasicNagger().nagUserWith(message);
    }

    public static void nagUserOnceWith(String message) {
        Naggers.getBasicNagger().nagUserOnceWith(message);
    }

    // Naggers
    public static void reset() {
        Naggers.reset();
    }

    public static void useLocationReporter(UsageLocationReporter reporter) {
        Naggers.useLocationReporter(reporter);
    }

    public static <T> T whileDisabled(Factory<T> factory) {
        return Naggers.whileDisabled(factory);
    }

    public static void whileDisabled(Runnable action) {
        Naggers.whileDisabled(action);
    }
}
