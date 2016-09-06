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

import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.Factory;
import org.gradle.util.GradleVersion;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

// TODO: Class should be package-private after DeprecationLogger and SingleMessageLogger have been removed.
@ThreadSafe
public class UsageNagger implements BasicNagger, DeprecationNagger, IncubationNagger {
    private static final Lock LOCK = new ReentrantLock();
    private static final ThreadLocal<Boolean> ENABLED = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return true;
        }
    };
    private static final LoggingFeatureUsageHandler HANDLER = new LoggingFeatureUsageHandler();

    private static String deprecationMessage;

    private final Set<String> previousMessages = new HashSet<String>();
    private final Class<?> calledFrom;

    public UsageNagger(Class<?> calledFrom) {
        if (calledFrom == null) {
            throw new NullPointerException("calledFrom");
        }
        this.calledFrom = calledFrom;
    }

    public void incubatingFeatureUsed(String incubatingFeature) {
        incubatingFeatureUsed(incubatingFeature, null);
    }

    public void incubatingFeatureUsed(String incubatingFeature, String additionalWarning) {
        String message = String.format("%s is an incubating feature.", incubatingFeature);
        if (additionalWarning != null) {
            message = message + '\n' + additionalWarning;
        }
        nagUserOnceWith(message);
    }

    public void nagUserOfDeprecated(String thing) {
        nagUserWith(String.format("%s %s.", thing, getDeprecationMessage()));
    }

    public void nagUserOfDeprecated(String thing, String explanation) {
        nagUserWith(String.format("%s %s. %s", thing, getDeprecationMessage(), explanation));
    }

    public void nagUserOfDeprecatedBehaviour(String behaviour) {
        nagUserWith(String.format("%s. This behaviour %s.",
            behaviour, getDeprecationMessage()));
    }

    public void nagUserOfDiscontinuedApi(String api, String advice) {
        nagUserWith(String.format("The %s %s. %s",
            api, getDeprecationMessage(), advice));
    }

    public void nagUserOfDiscontinuedMethod(String methodName) {
        nagUserWith(String.format("The %s method %s.",
            methodName, getDeprecationMessage()));
    }

    public void nagUserOfDiscontinuedMethod(String methodName, String advice) {
        nagUserWith(String.format("The %s method %s. %s",
            methodName, getDeprecationMessage(), advice));
    }

    public void nagUserOfDiscontinuedProperty(String propertyName, String advice) {
        nagUserWith(String.format("The %s property %s. %s",
            propertyName, getDeprecationMessage(), advice));
    }

    public void nagUserOfPluginReplacedWithExternalOne(String pluginName, String replacement) {
        nagUserWith(String.format(
            "The %s plugin %s. Consider using the %s plugin instead.",
            pluginName, getDeprecationMessage(), replacement));
    }

    public void nagUserOfReplacedMethod(String methodName, String replacement) {
        nagUserWith(String.format(
            "The %s method %s. Please use the %s method instead.",
            methodName, getDeprecationMessage(), replacement));
    }

    public void nagUserOfReplacedNamedParameter(String parameterName, String replacement) {
        nagUserWith(String.format(
            "The %s named parameter %s. Please use the %s named parameter instead.",
            parameterName, getDeprecationMessage(), replacement));
    }

    public void nagUserOfReplacedPlugin(String pluginName, String replacement) {
        nagUserWith(String.format(
            "The %s plugin %s. Please use the %s plugin instead.",
            pluginName, getDeprecationMessage(), replacement));
    }

    public void nagUserOfReplacedProperty(String propertyName, String replacement) {
        nagUserWith(String.format(
            "The %s property %s. Please use the %s property instead.",
            propertyName, getDeprecationMessage(), replacement));
    }

    public void nagUserOfReplacedTask(String taskName, String replacement) {
        nagUserWith(String.format(
            "The %s task %s. Please use the %s task instead.",
            taskName, getDeprecationMessage(), replacement));
    }

    public void nagUserOfReplacedTaskType(String taskName, String replacement) {
        nagUserWith(String.format(
            "The %s task type %s. Please use the %s instead.",
            taskName, getDeprecationMessage(), replacement));
    }

    public void nagUserWith(String message) {
        if (ENABLED.get()) {
            LOCK.lock();
            try {
                HANDLER.featureUsed(new FeatureUsage(message, calledFrom));
            } finally {
                LOCK.unlock();
            }
        }

    }

    public void nagUserOnceWith(String message) {
        if (ENABLED.get()) {
            LOCK.lock();
            try {
                if (previousMessages.add(message)) {
                    HANDLER.featureUsed(new FeatureUsage(message, calledFrom));
                }
            } finally {
                LOCK.unlock();
            }
        }
    }

    public void reset() {
        LOCK.lock();
        try {
            previousMessages.clear();
        } finally {
            LOCK.unlock();
        }
    }

    public void useLocationReporter(UsageLocationReporter reporter) {
        LOCK.lock();
        try {
            HANDLER.setLocationReporter(reporter);
        } finally {
            LOCK.unlock();
        }
    }

    public <T> T whileDisabled(Factory<T> factory) {
        final boolean previouslyEnabled = ENABLED.get();
        ENABLED.set(false);
        try {
            return factory.create();
        } finally {
            ENABLED.set(previouslyEnabled);
        }
    }

    public void whileDisabled(Runnable action) {
        final boolean previouslyEnabled = ENABLED.get();
        ENABLED.set(false);
        try {
            action.run();
        } finally {
            ENABLED.set(previouslyEnabled);
        }
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
