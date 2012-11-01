/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.internal.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DeprecationLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeprecationLogger.class);
    private static final Set<String> PLUGINS = Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> TASKS = Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> METHODS = Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> DYNAMIC_PROPERTIES = Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> PROPERTIES = Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> NAMED_PARAMETERS = Collections.synchronizedSet(new HashSet<String>());
    
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
    private static final String DEPRECATION_MESSAGE = "has been deprecated and will be removed in the next version of Gradle";

    public static void reset() {
        PLUGINS.clear();
        METHODS.clear();
        PROPERTIES.clear();
        NAMED_PARAMETERS.clear();
        DYNAMIC_PROPERTIES.clear();
    }

    public static void nagUserOfReplacedPlugin(String pluginName, String replacement) {
        if (isEnabled() && PLUGINS.add(pluginName)) {
            LOGGER.warn(String.format(
                    "The %s plugin %S. Please use the %s plugin instead.",
                    pluginName, DEPRECATION_MESSAGE, replacement));
            logTraceIfNecessary();
        }
    }

    public static void nagUserOfReplacedTaskType(String taskName, String replacement) {
        if (isEnabled() && TASKS.add(taskName)) {
            LOGGER.warn(String.format(
                    "The %s task type %s. Please use the %s instead.",
                    taskName, DEPRECATION_MESSAGE, replacement));
            logTraceIfNecessary();
        }
    }
    
    public static void nagUserOfReplacedMethod(String methodName, String replacement) {
        if (isEnabled() && METHODS.add(methodName)) {
            LOGGER.warn(String.format(
                    "The %s method %s. Please use the %s method instead.",
                    methodName, DEPRECATION_MESSAGE, replacement));
            logTraceIfNecessary();
        }
    }

    public static void nagUserOfReplacedProperty(String propertyName, String replacement) {
        if (isEnabled() && PROPERTIES.add(propertyName)) {
            LOGGER.warn(String.format(
                    "The %s property %s. Please use the %s property instead.",
                    propertyName, DEPRECATION_MESSAGE, replacement));
            logTraceIfNecessary();
        }
    }

    public static void nagUserOfDiscontinuedMethod(String methodName) {
        if (isEnabled() && METHODS.add(methodName)) {
            LOGGER.warn(String.format("The %s method %s.",
                    methodName, DEPRECATION_MESSAGE));
            logTraceIfNecessary();
        }
    }

    public static void nagUserOfDiscontinuedProperty(String propertyName, String advice) {
        if (isEnabled() && PROPERTIES.add(propertyName)) {
            LOGGER.warn(String.format("The %s property %s. %s",
                    propertyName, DEPRECATION_MESSAGE, advice));
            logTraceIfNecessary();
        }
    }

    public static void nagUserOfReplacedNamedParameter(String parameterName, String replacement) {
        if (isEnabled() && NAMED_PARAMETERS.add(parameterName)) {
            LOGGER.warn(String.format(
                    "The %s named parameter %s. Please use the %s named parameter instead.",
                    parameterName, DEPRECATION_MESSAGE, replacement));
            logTraceIfNecessary();
        }
    }

    public static void nagUserWith(String message) {
        if (isEnabled() && METHODS.add(message)) {
            LOGGER.warn(message);
            logTraceIfNecessary();
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
        nagUserWith("Dynamic properties are deprecated: http://gradle.org/docs/current/dsl/org.gradle.api.plugins.ExtraPropertiesExtension.html");

        String propertyWithClass = target.getClass().getName() + "." + propertyName;
        if (DYNAMIC_PROPERTIES.add(propertyWithClass)) {
            String propertyWithTarget = String.format("\"%s\" on \"%s\"", propertyName, target);
            String theValue = (value==null)? "null" : StringUtils.abbreviate(value.toString(), 25);
            nagUserWith(String.format("Deprecated dynamic property: %s, value: \"%s\".", propertyWithTarget, theValue));
        } else {
            nagUserWith(String.format("Deprecated dynamic property \"%s\" created in multiple locations.", propertyName));
        }
    }
}