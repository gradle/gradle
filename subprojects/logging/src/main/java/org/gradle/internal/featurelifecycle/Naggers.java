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
import org.gradle.util.GradleVersion;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Naggers {
    public static final String ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME = "org.gradle.deprecation.trace";

    private static boolean traceLoggingEnabled;
    private static final Lock LOCK = new ReentrantLock();
    private static final ThreadLocal<Boolean> ENABLED = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return true;
        }
    };

    private static final DefaultBasicNagger DEFAULT_BASIC_NAGGER = new DefaultBasicNagger();
    private static final IncubationNagger DEFAULT_INCUBATION_NAGGER = new DefaultIncubationNagger();
    private static final DeprecationNagger DEFAULT_DEPRECATION_NAGGER = new DefaultDeprecationNagger();

    private static DeprecationNagger deprecationNagger;
    private static IncubationNagger incubationNagger;
    private static BasicNagger basicNagger;

    public static void setDeprecationNagger(DeprecationNagger deprecationNagger) {
        LOCK.lock();
        try {
            Naggers.deprecationNagger = deprecationNagger;
        } finally {
            LOCK.unlock();
        }
    }

    public static DeprecationNagger getDeprecationNagger() {
        LOCK.lock();
        try {
            if (deprecationNagger == null) {
                return DEFAULT_DEPRECATION_NAGGER;
            }
            return deprecationNagger;
        } finally {
            LOCK.unlock();
        }
    }

    public static void setIncubationNagger(IncubationNagger incubationNagger) {
        LOCK.lock();
        try {
            Naggers.incubationNagger = incubationNagger;
        } finally {
            LOCK.unlock();
        }
    }

    public static IncubationNagger getIncubationNagger() {
        LOCK.lock();
        try {
            if (incubationNagger == null) {
                return DEFAULT_INCUBATION_NAGGER;
            }
            return incubationNagger;
        } finally {
            LOCK.unlock();
        }
    }

    public static void setBasicNagger(BasicNagger basicNagger) {
        LOCK.lock();
        try {
            Naggers.basicNagger = basicNagger;
        } finally {
            LOCK.unlock();
        }
    }

    public static BasicNagger getBasicNagger() {
        LOCK.lock();
        try {
            if (basicNagger == null) {
                return DEFAULT_BASIC_NAGGER;
            }
            return basicNagger;
        } finally {
            LOCK.unlock();
        }
    }

    public static void useLocationReporter(UsageLocationReporter reporter) {
        DEFAULT_BASIC_NAGGER.useLocationReporter(reporter);
    }

    public static <T> T whileDisabled(Factory<T> factory) {
        final boolean previouslyEnabled = ENABLED.get();
        ENABLED.set(false);
        try {
            return factory.create();
        } finally {
            ENABLED.set(previouslyEnabled);
        }
    }

    public static void whileDisabled(Runnable action) {
        final boolean previouslyEnabled = ENABLED.get();
        ENABLED.set(false);
        try {
            action.run();
        } finally {
            ENABLED.set(previouslyEnabled);
        }
    }

    public static void reset() {
        LOCK.lock();
        try {
            deprecationNagger = null;
            incubationNagger = null;
            basicNagger = null;
            DEFAULT_BASIC_NAGGER.reset();
        } finally {
            LOCK.unlock();
        }
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
        if (value == null) {
            return traceLoggingEnabled;
        }
        return Boolean.parseBoolean(value);
    }

    private static class DefaultBasicNagger implements BasicNagger {
        private static final Set<String> PREVIOUS_MESSAGES = new HashSet<String>();
        private static final LoggingFeatureUsageHandler HANDLER = new LoggingFeatureUsageHandler();

        public void nagUserWith(String message) {
            if (ENABLED.get()) {
                LOCK.lock();
                try {
                    HANDLER.featureUsed(new FeatureUsage(message));
                } finally {
                    LOCK.unlock();
                }
            }

        }

        public void nagUserOnceWith(String message) {
            if (ENABLED.get()) {
                LOCK.lock();
                try {
                    if (PREVIOUS_MESSAGES.add(message)) {
                        HANDLER.featureUsed(new FeatureUsage(message));
                    }
                } finally {
                    LOCK.unlock();
                }
            }
        }

        private void reset() {
            LOCK.lock();
            try {
                PREVIOUS_MESSAGES.clear();
            } finally {
                LOCK.unlock();
            }
        }

        private void useLocationReporter(UsageLocationReporter reporter) {
            LOCK.lock();
            try {
                HANDLER.setLocationReporter(reporter);
            } finally {
                LOCK.unlock();
            }
        }
    }

    private static class DefaultIncubationNagger implements IncubationNagger {

        public void incubatingFeatureUsed(String incubatingFeature) {
            incubatingFeatureUsed(incubatingFeature, null);
        }

        public void incubatingFeatureUsed(String incubatingFeature, String additionalWarning) {
            String message = getIncubationMessage(incubatingFeature);
            if (additionalWarning != null) {
                message = message + '\n' + additionalWarning;
            }
            getBasicNagger().nagUserOnceWith(message);
        }

        private static String getIncubationMessage(String incubatingFeature) {
            return String.format("%s is an incubating feature.", incubatingFeature);
        }
    }

    private static class DefaultDeprecationNagger implements DeprecationNagger {
        private static String deprecationMessage;

        public void nagUserOfDeprecated(String thing) {
            getBasicNagger().nagUserWith(String.format("%s %s.", thing, getDeprecationMessage()));
        }

        public void nagUserOfDeprecated(String thing, String explanation) {
            getBasicNagger().nagUserWith(String.format("%s %s. %s", thing, getDeprecationMessage(), explanation));
        }

        public void nagUserOfDeprecatedBehaviour(String behaviour) {
            getBasicNagger().nagUserWith(String.format("%s. This behaviour %s.",
                behaviour, getDeprecationMessage()));
        }

        public void nagUserOfDiscontinuedApi(String api, String advice) {
            getBasicNagger().nagUserWith(String.format("The %s %s. %s",
                api, getDeprecationMessage(), advice));
        }

        public void nagUserOfDiscontinuedMethod(String methodName) {
            getBasicNagger().nagUserWith(String.format("The %s method %s.",
                methodName, getDeprecationMessage()));
        }

        public void nagUserOfDiscontinuedMethod(String methodName, String advice) {
            getBasicNagger().nagUserWith(String.format("The %s method %s. %s",
                methodName, getDeprecationMessage(), advice));
        }

        public void nagUserOfDiscontinuedProperty(String propertyName, String advice) {
            getBasicNagger().nagUserWith(String.format("The %s property %s. %s",
                propertyName, getDeprecationMessage(), advice));
        }

        public void nagUserOfPluginReplacedWithExternalOne(String pluginName, String replacement) {
            getBasicNagger().nagUserWith(String.format(
                "The %s plugin %s. Consider using the %s plugin instead.",
                pluginName, getDeprecationMessage(), replacement));
        }

        public void nagUserOfReplacedMethod(String methodName, String replacement) {
            getBasicNagger().nagUserWith(String.format(
                "The %s method %s. Please use the %s method instead.",
                methodName, getDeprecationMessage(), replacement));
        }

        public void nagUserOfReplacedNamedParameter(String parameterName, String replacement) {
            getBasicNagger().nagUserWith(String.format(
                "The %s named parameter %s. Please use the %s named parameter instead.",
                parameterName, getDeprecationMessage(), replacement));
        }

        public void nagUserOfReplacedPlugin(String pluginName, String replacement) {
            getBasicNagger().nagUserWith(String.format(
                "The %s plugin %s. Please use the %s plugin instead.",
                pluginName, getDeprecationMessage(), replacement));
        }

        public void nagUserOfReplacedProperty(String propertyName, String replacement) {
            getBasicNagger().nagUserWith(String.format(
                "The %s property %s. Please use the %s property instead.",
                propertyName, getDeprecationMessage(), replacement));
        }

        public void nagUserOfReplacedTask(String taskName, String replacement) {
            getBasicNagger().nagUserWith(String.format(
                "The %s task %s. Please use the %s task instead.",
                taskName, getDeprecationMessage(), replacement));
        }

        public void nagUserOfReplacedTaskType(String taskName, String replacement) {
            getBasicNagger().nagUserWith(String.format(
                "The %s task type %s. Please use the %s instead.",
                taskName, getDeprecationMessage(), replacement));
        }

        private static String getDeprecationMessage() {
            LOCK.lock();
            try {
                if (deprecationMessage == null) {
                    String nextMajorVersionString = GradleVersion.current().getNextMajor().getVersion();

                    deprecationMessage = "has been deprecated and is scheduled to be removed in Gradle " + nextMajorVersionString;
                }
                return deprecationMessage;
            } finally {
                LOCK.unlock();
            }
        }
    }
}
