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

import org.apache.commons.lang.StringUtils;
import org.codehaus.groovy.runtime.StackTraceUtils;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Factory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SingleMessageLogger {
    private static final Logger LOGGER = Logging.getLogger(DeprecationLogger.class);
    private static final Set<String> DYNAMIC_PROPERTIES = Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> MESSAGES = Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> FEATURES = Collections.synchronizedSet(new HashSet<String>());

    private static final ThreadLocal<Boolean> ENABLED = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return true;
        }
    };

    private static final ThreadLocal<Boolean> LOG_TRACE = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };

    public static final String ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME = "org.gradle.deprecation.trace";

    private static String deprecationMessage;
    private static Lock deprecationMessageLock = new ReentrantLock();
    public static final String INCUBATION_MESSAGE = "%s is an incubating feature.";

    private static String getDeprecationMessage() {
        if (deprecationMessage == null) {
            deprecationMessageLock.lock();
            try {
                if (deprecationMessage == null) {
                    String messageBase = "has been deprecated and is scheduled to be removed in";
                    String when;

                    GradleVersion currentVersion = GradleVersion.current();
                    int versionMajor = currentVersion.getMajor();
                    if (versionMajor == -1) { // don't understand version number
                        when = "the next major version of Gradle";
                    } else {
                        when = String.format("Gradle %d.0", versionMajor + 1);
                    }

                    deprecationMessage = String.format("%s %s", messageBase, when);
                }
            } finally {
                deprecationMessageLock.unlock();
            }
        }

        return deprecationMessage;
    }

    public static void reset() {
        DYNAMIC_PROPERTIES.clear();
        MESSAGES.clear();
        FEATURES.clear();
    }

    public static void nagUserOfReplacedPlugin(String pluginName, String replacement) {
        nagUserWith(String.format(
                "The %s plugin %S. Please use the %s plugin instead.",
                pluginName, getDeprecationMessage(), replacement));
    }

    public static void nagUserOfReplacedTaskType(String taskName, String replacement) {
        nagUserWith(String.format(
                "The %s task type %s. Please use the %s instead.",
                taskName, getDeprecationMessage(), replacement));
    }

    public static void nagUserOfReplacedMethod(String methodName, String replacement) {
        nagUserWith(String.format(
                "The %s method %s. Please use the %s method instead.",
                methodName, getDeprecationMessage(), replacement));
    }

    public static void nagUserOfReplacedProperty(String propertyName, String replacement) {
        nagUserWith(String.format(
                "The %s property %s. Please use the %s property instead.",
                propertyName, getDeprecationMessage(), replacement));
    }

    public static void nagUserOfDiscontinuedMethod(String methodName) {
        nagUserWith(String.format("The %s method %s.",
                methodName, getDeprecationMessage()));
    }

    public static void nagUserOfDiscontinuedProperty(String propertyName, String advice) {
        nagUserWith(String.format("The %s property %s. %s",
                propertyName, getDeprecationMessage(), advice));
    }

    public static void nagUserOfDiscontinuedConfiguration(String configurationName, String advice) {
        nagUserWith(String.format("The %s configuration %s. %s",
                configurationName, getDeprecationMessage(), advice));
    }

    public static void nagUserOfReplacedNamedParameter(String parameterName, String replacement) {
        nagUserWith(String.format(
                "The %s named parameter %s. Please use the %s named parameter instead.",
                parameterName, getDeprecationMessage(), replacement));
    }

    /**
     * Try to avoid using this nagging method. The other methods use a consistent wording for when things will be removed.
     */
    public static void nagUserWith(String message) {
        if (isEnabled() && MESSAGES.add(message)) {
            LOGGER.warn(message);
            logTraceIfNecessary();
        }
    }

    /**
     * Avoid using this method, use the variant with an explanation instead.
     */
    public static void nagUserOfDeprecated(String thing) {
        nagUserWith(String.format("%s %s", thing, getDeprecationMessage()));
    }

    public static void nagUserOfDeprecated(String thing, String explanation) {
        nagUserWith(String.format("%s %s. %s.", thing, getDeprecationMessage(), explanation));
    }

    public static void nagUserOfDeprecatedBehaviour(String behaviour) {
        nagUserOfDeprecated(String.format("%s. This behaviour", behaviour));
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

    private static boolean isTraceLoggingEnabled() {
        return Boolean.getBoolean(ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME) || LOG_TRACE.get();
    }

    private static void logTraceIfNecessary() {
        if (isTraceLoggingEnabled()) {
            StackTraceElement[] stack = StackTraceUtils.sanitize(new Exception()).getStackTrace();
            for (StackTraceElement frame : stack) {
                if (!frame.getClassName().startsWith(DeprecationLogger.class.getName())) {
                    LOGGER.warn("    {}", frame.toString());
                }
            }
        }
    }

    private static boolean isEnabled() {
        return ENABLED.get();
    }

    public static void setLogTrace(boolean flag) {
        LOG_TRACE.set(flag);
    }

    public static void nagUserAboutDynamicProperty(String propertyName, Object target, Object value) {
        if (!isEnabled()) {
            return;
        }
        nagUserOfDeprecated("Creating properties on demand (a.k.a. dynamic properties)", "Please read http://gradle.org/docs/current/dsl/org.gradle.api.plugins.ExtraPropertiesExtension.html for information on the replacement for dynamic properties");

        String propertyWithClass = target.getClass().getName() + "." + propertyName;
        if (DYNAMIC_PROPERTIES.add(propertyWithClass)) {
            String propertyWithTarget = String.format("\"%s\" on \"%s\"", propertyName, target);
            String theValue = (value==null)? "null" : StringUtils.abbreviate(value.toString(), 25);
            nagUserWith(String.format("Deprecated dynamic property: %s, value: \"%s\".", propertyWithTarget, theValue));
        } else {
            nagUserWith(String.format("Deprecated dynamic property \"%s\" created in multiple locations.", propertyName));
        }
    }

    public static void incubatingFeatureUsed(String incubatingFeature) {
        if (FEATURES.add(incubatingFeature)) {
            LOGGER.lifecycle(String.format(INCUBATION_MESSAGE, incubatingFeature));
        }
    }
}
