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
import org.apache.commons.lang.StringUtils;
import org.gradle.api.logging.configuration.WarningMode;
import org.gradle.internal.Factory;
import org.gradle.internal.featurelifecycle.FeatureHandler;
import org.gradle.internal.featurelifecycle.FeatureUsage;
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler;
import org.gradle.internal.featurelifecycle.LoggingIncubatingFeatureHandler;
import org.gradle.internal.featurelifecycle.UsageLocationReporter;
import org.gradle.internal.operations.BuildOperationExecutor;

import javax.annotation.Nullable;

import static org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler.getDeprecationMessage;

@ThreadSafe
public class SingleMessageLogger {
    private static final ThreadLocal<Boolean> ENABLED = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return true;
        }
    };

    public static final String INCUBATION_MESSAGE = "%s is an incubating feature.";

    private static LoggingDeprecatedFeatureHandler deprecatedFeatureHandler = new LoggingDeprecatedFeatureHandler();
    private static LoggingIncubatingFeatureHandler incubatingFeatureHandler = new LoggingIncubatingFeatureHandler();

    public synchronized static void reset() {
        deprecatedFeatureHandler.reset();
        incubatingFeatureHandler.reset();
    }

    public synchronized static void init(UsageLocationReporter reporter, WarningMode warningMode, BuildOperationExecutor buildOperationExecutor) {
        deprecatedFeatureHandler.init(reporter, warningMode, buildOperationExecutor);
    }

    public synchronized static void reportSuppressedDeprecations() {
        deprecatedFeatureHandler.reportSuppressedDeprecations();
    }

    public static void nagUserOfReplacedPlugin(String pluginName, String replacement) {
        if (isEnabled()) {
            nagUserWith(String.format(
                    "The %s plugin %s. Please use the %s plugin instead.",
                    pluginName, getDeprecationMessage(), replacement));
        }
    }

    public static void nagUserOfPluginReplacedWithExternalOne(String pluginName, String replacement) {
        if (isEnabled()) {
            nagUserWith(String.format(
                    "The %s plugin %s. Consider using the %s plugin instead.",
                    pluginName, getDeprecationMessage(), replacement));
        }
    }

    public static void nagUserOfToolReplacedWithExternalOne(String toolName, String replacement) {
        if (isEnabled()) {
            nagUserWith(String.format(
                    "The %s %s. Consider using %s instead.",
                    toolName, getDeprecationMessage(), replacement));
        }
    }

    public static void nagUserOfReplacedTask(String taskName, String replacement) {
        if (isEnabled()) {
            nagUserWith(String.format(
                    "The %s task %s. Please use the %s task instead.",
                    taskName, getDeprecationMessage(), replacement));
        }
    }

    public static void nagUserOfReplacedTaskType(String taskName, String replacement) {
        if (isEnabled()) {
            nagUserWith(String.format(
                    "The %s task type %s. Please use the %s instead.",
                    taskName, getDeprecationMessage(), replacement));
        }
    }

    public static void nagUserOfReplacedMethod(String methodName, String replacement) {
        if (isEnabled()) {
            nagUserWith(String.format(
                    "The %s method %s. Please use the %s method instead.",
                    methodName, getDeprecationMessage(), replacement));
        }
    }

    public static void nagUserOfReplacedProperty(String propertyName, String replacement) {
        if (isEnabled()) {
            nagUserWith(String.format(
                    "The %s property %s. Please use the %s property instead.",
                    propertyName, getDeprecationMessage(), replacement));
        }
    }

    public static void nagUserOfDiscontinuedMethod(String methodName) {
        if (isEnabled()) {
            nagUserWith(String.format("The %s method %s.",
                    methodName, getDeprecationMessage()));
        }
    }

    public static void nagUserOfDiscontinuedMethod(String methodName, String advice) {
        if (isEnabled()) {
            nagUserWith(String.format("The %s method %s. %s",
                    methodName, getDeprecationMessage(), advice));
        }
    }

    public static void nagUserOfDiscontinuedProperty(String propertyName, String advice) {
        if (isEnabled()) {
            nagUserWith(String.format("The %s property %s. %s",
                    propertyName, getDeprecationMessage(), advice));
        }
    }

    public static void nagUserOfDiscontinuedApi(String api, String advice) {
        if (isEnabled()) {
            nagUserWith(String.format("The %s %s. %s",
                api, getDeprecationMessage(), advice));
        }
    }

    public static void nagUserOfReplacedNamedParameter(String parameterName, String replacement) {
        if (isEnabled()) {
            nagUserWith(String.format(
                    "The %s named parameter %s. Please use the %s named parameter instead.",
                    parameterName, getDeprecationMessage(), replacement));
        }
    }

    /**
     * Try to avoid using this nagging method. The other methods use a consistent wording for when things will be removed.
     */
    public static void nagUserWith(String message) {
        if (isEnabled()) {
            nagUserWith(deprecatedFeatureHandler, new FeatureUsage(message, SingleMessageLogger.class));
        }
    }

    private synchronized static void nagUserWith(FeatureHandler handler, FeatureUsage usage) {
        handler.featureUsed(usage);
    }

    /**
     * Avoid using this method, use the variant with an explanation instead.
     */
    public static void nagUserOfDeprecated(String thing) {
        if (isEnabled()) {
            nagUserWith(String.format("%s %s", thing, getDeprecationMessage()));
        }
    }

    public static void nagUserOfDeprecated(String thing, String explanation) {
        if (isEnabled()) {
            nagUserWith(String.format("%s %s. %s.", thing, getDeprecationMessage(), explanation));
        }
    }

    public static void nagUserOfDeprecatedBehaviour(String behaviour) {
        if (isEnabled()) {
            nagUserOfDeprecated(String.format("%s. This behaviour", behaviour));
        }
    }

    public static void nagUserOfDeprecatedThing(String thing, String explanation) {
        if (isEnabled()) {
            if (StringUtils.isEmpty(explanation)) {
                nagUserWith(String.format("%s. This %s.", thing, getDeprecationMessage()));
            } else {
                nagUserWith(String.format("%s. This %s. %s.", thing, getDeprecationMessage(), explanation));
            }
        }
    }

    @Nullable
    public static <T> T whileDisabled(Factory<T> factory) {
        ENABLED.set(false);
        try {
            return factory.create();
        } finally {
            ENABLED.set(true);
        }
    }

    public static void whileDisabled(Runnable action) {
        ENABLED.set(false);
        try {
            action.run();
        } finally {
            ENABLED.set(true);
        }
    }

    private static boolean isEnabled() {
        return ENABLED.get();
    }

    public static void incubatingFeatureUsed(String incubatingFeature) {
        nagUserWith(incubatingFeatureHandler, new FeatureUsage(incubatingFeature, SingleMessageLogger.class));
    }
}
