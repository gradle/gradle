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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
            StringBuilder message = new StringBuilder();
            message.append(incubatingFeature)
                .append(" is an incubating feature.");

            if (additionalWarning != null) {
                message.append('\n')
                    .append(additionalWarning);
            }

            getBasicNagger().nagUserOnceWith(message.toString());
        }
    }

    private static abstract class AbstractDeprecationNagger implements DeprecationNagger {
        private static final GradleVersion NEXT_MAJOR_GRADLE_VERSION = GradleVersion.current().getNextMajor();

        public void nagUserOfDeprecated(String thing) {
            nagUserOfDeprecated(thing, NEXT_MAJOR_GRADLE_VERSION);
        }

        public void nagUserOfDeprecated(String thing, String explanation) {
            nagUserOfDeprecated(thing, explanation, NEXT_MAJOR_GRADLE_VERSION);
        }

        public void nagUserOfDeprecatedBehaviour(String behaviour) {
            nagUserOfDeprecatedBehaviour(behaviour, NEXT_MAJOR_GRADLE_VERSION);
        }

        public void nagUserOfDiscontinuedApi(String api, String advice) {
            nagUserOfDiscontinuedApi(api, advice, NEXT_MAJOR_GRADLE_VERSION);
        }

        public void nagUserOfDiscontinuedMethod(String methodName) {
            nagUserOfDiscontinuedMethod(methodName, NEXT_MAJOR_GRADLE_VERSION);
        }

        public void nagUserOfDiscontinuedMethod(String methodName, String advice) {
            nagUserOfDiscontinuedMethod(methodName, advice, NEXT_MAJOR_GRADLE_VERSION);
        }

        public void nagUserOfDiscontinuedProperty(String propertyName, String advice) {
            nagUserOfDiscontinuedProperty(propertyName, advice, NEXT_MAJOR_GRADLE_VERSION);
        }

        public void nagUserOfPluginReplacedWithExternalOne(String pluginName, String replacement) {
            nagUserOfPluginReplacedWithExternalOne(pluginName, replacement, NEXT_MAJOR_GRADLE_VERSION);
        }

        public void nagUserOfReplacedMethod(String methodName, String replacement) {
            nagUserOfReplacedMethod(methodName, replacement, NEXT_MAJOR_GRADLE_VERSION);
        }

        public void nagUserOfReplacedNamedParameter(String parameterName, String replacement) {
            nagUserOfReplacedNamedParameter(parameterName, replacement, NEXT_MAJOR_GRADLE_VERSION);
        }

        public void nagUserOfReplacedPlugin(String pluginName, String replacement) {
            nagUserOfReplacedPlugin(pluginName, replacement, NEXT_MAJOR_GRADLE_VERSION);
        }

        public void nagUserOfReplacedProperty(String propertyName, String replacement) {
            nagUserOfReplacedProperty(propertyName, replacement, NEXT_MAJOR_GRADLE_VERSION);
        }

        public void nagUserOfReplacedTask(String taskName, String replacement) {
            nagUserOfReplacedTask(taskName, replacement, NEXT_MAJOR_GRADLE_VERSION);
        }

        public void nagUserOfReplacedTaskType(String taskType, String replacement) {
            nagUserOfReplacedTaskType(taskType, replacement, NEXT_MAJOR_GRADLE_VERSION);
        }
    }
    private static class DefaultDeprecationNagger extends AbstractDeprecationNagger {
        private static final ConcurrentMap<String, String> MESSAGE_MAP = new ConcurrentHashMap<String, String>();

        public void nagUserOfDeprecated(String thing, GradleVersion gradleVersion) {
            nagUserOfDeprecated(thing, null, gradleVersion);
        }

        public void nagUserOfDeprecated(String thing, String explanation, GradleVersion gradleVersion) {
            StringBuilder message = new StringBuilder();
            message.append(thing)
                .append(' ')
                .append(getDeprecationMessage(gradleVersion))
                .append('.');

            if (explanation != null) {
                message.append(' ')
                    .append(explanation);
            }

            getBasicNagger().nagUserWith(message.toString());
        }

        public void nagUserOfDeprecatedBehaviour(String behaviour, GradleVersion gradleVersion) {
            String message = behaviour
                + ". This behaviour "
                + getDeprecationMessage(gradleVersion)
                + '.';

            getBasicNagger().nagUserWith(message);
        }

        public void nagUserOfDiscontinuedApi(String api, String advice, GradleVersion gradleVersion) {
            String message = "The "
                + api
                + ' '
                + getDeprecationMessage(gradleVersion)
                + ". "
                + advice;

            getBasicNagger().nagUserWith(message);
        }

        public void nagUserOfDiscontinuedMethod(String methodName, GradleVersion gradleVersion) {
            nagUserOfDiscontinuedMethod(methodName, null, gradleVersion);
        }

        public void nagUserOfDiscontinuedMethod(String methodName, String advice, GradleVersion gradleVersion) {
            StringBuilder message = new StringBuilder();
            message.append("The ")
                .append(methodName)
                .append(" method ")
                .append(getDeprecationMessage(gradleVersion))
                .append('.');

            if (advice != null) {
                message.append(' ')
                    .append(advice);
            }

            getBasicNagger().nagUserWith(message.toString());
        }

        public void nagUserOfDiscontinuedProperty(String propertyName, String advice, GradleVersion gradleVersion) {
            String message = "The "
                + propertyName
                + " property "
                + getDeprecationMessage(gradleVersion)
                + ". "
                + advice;

            getBasicNagger().nagUserWith(message);
        }

        public void nagUserOfPluginReplacedWithExternalOne(String pluginName, String replacement, GradleVersion gradleVersion) {
            String message = "The "
                + pluginName
                + " plugin "
                + getDeprecationMessage(gradleVersion)
                + ". Consider using the "
                + replacement
                + " plugin instead.";

            getBasicNagger().nagUserWith(message);
        }

        public void nagUserOfReplacedMethod(String methodName, String replacement, GradleVersion gradleVersion) {
            String message = "The "
                + methodName
                + " method "
                + getDeprecationMessage(gradleVersion)
                + ". Please use the "
                + replacement
                + " method instead.";

            getBasicNagger().nagUserWith(message);
        }

        public void nagUserOfReplacedNamedParameter(String parameterName, String replacement, GradleVersion gradleVersion) {
            String message = "The "
                + parameterName
                + " named parameter "
                + getDeprecationMessage(gradleVersion)
                + ". Please use the "
                + replacement
                + " named parameter instead.";

            getBasicNagger().nagUserWith(message);
        }

        public void nagUserOfReplacedPlugin(String pluginName, String replacement, GradleVersion gradleVersion) {
            String message = "The "
                + pluginName
                + " plugin "
                + getDeprecationMessage(gradleVersion)
                + ". Please use the "
                + replacement
                + " plugin instead.";

            getBasicNagger().nagUserWith(message);
        }

        public void nagUserOfReplacedProperty(String propertyName, String replacement, GradleVersion gradleVersion) {
            String message = "The "
                + propertyName
                + " property "
                + getDeprecationMessage(gradleVersion)
                + ". Please use the "
                + replacement
                + " property instead.";

            getBasicNagger().nagUserWith(message);
        }

        public void nagUserOfReplacedTask(String taskName, String replacement, GradleVersion gradleVersion) {
            String message = "The "
                + taskName
                + " task "
                + getDeprecationMessage(gradleVersion)
                + ". Please use the "
                + replacement
                + " task instead.";

            getBasicNagger().nagUserWith(message);
        }

        public void nagUserOfReplacedTaskType(String taskType, String replacement, GradleVersion gradleVersion) {
            String message =
                "The "
                + taskType
                + " task type "
                + getDeprecationMessage(gradleVersion)
                + ". Please use the "
                + replacement
                + " instead.";

            getBasicNagger().nagUserWith(message);
        }

        private static String getDeprecationMessage(GradleVersion gradleVersion) {
            if (gradleVersion == null) {
                throw new NullPointerException("gradleVersion must not be null!");
            }
            final String versionString = gradleVersion.getVersion();
            String result = MESSAGE_MAP.get(versionString);
            if (result == null) {
                result = "has been deprecated and is scheduled to be removed in Gradle "
                    + versionString;
                MESSAGE_MAP.putIfAbsent(versionString, result);
            }
            return result;
        }
    }
}
