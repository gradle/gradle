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
import org.gradle.internal.featurelifecycle.DeprecatedFeatureUsage;
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler;
import org.gradle.internal.featurelifecycle.UsageLocationReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@ThreadSafe
public class SingleMessageLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeprecationLogger.class);
    private static final Set<String> FEATURES = Collections.synchronizedSet(new HashSet<String>());

    private static final ThreadLocal<Boolean> ENABLED = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return true;
        }
    };

    public static final String INCUBATION_MESSAGE = "%s is an incubating feature.";

    private static final Lock LOCK = new ReentrantLock();
    private static LoggingDeprecatedFeatureHandler handler = new LoggingDeprecatedFeatureHandler();
    private static String deprecationMessage;

    public static String getDeprecationMessage() {
        LOCK.lock();
        try {
            if (deprecationMessage == null) {
                String messageBase = "has been deprecated and is scheduled to be removed in";

                GradleVersion currentVersion = GradleVersion.current();
                String when = String.format("Gradle %s", currentVersion.getNextMajor().getVersion());

                deprecationMessage = String.format("%s %s", messageBase, when);
            }
            return deprecationMessage;
        } finally {
            LOCK.unlock();
        }
    }

    public static void reset() {
        FEATURES.clear();
        LOCK.lock();
        try {
            handler = new LoggingDeprecatedFeatureHandler();
        } finally {
            LOCK.unlock();
        }
    }

    public static void useLocationReporter(UsageLocationReporter reporter) {
        LOCK.lock();
        try {
            handler.setLocationReporter(reporter);
        } finally {
            LOCK.unlock();
        }
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
            LOCK.lock();
            try {
                handler.deprecatedFeatureUsed(new DeprecatedFeatureUsage(message, SingleMessageLogger.class));
            } finally {
                LOCK.unlock();
            }
        }
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
        incubatingFeatureUsed(incubatingFeature, null);
    }

    public static void incubatingFeatureUsed(String incubatingFeature, String additionalWarning) {
        if (FEATURES.add(incubatingFeature)) {
            String message = String.format(INCUBATION_MESSAGE, incubatingFeature);
            if (additionalWarning != null) {
                message = message + "\n" + additionalWarning;
            }
            LOGGER.warn(message);
        }
    }
}
