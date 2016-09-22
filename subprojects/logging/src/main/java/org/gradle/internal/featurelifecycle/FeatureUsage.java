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

package org.gradle.internal.featurelifecycle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable description of the usage of a feature.
 */
public class FeatureUsage {
    private final String message;
    private final List<StackTraceElement> stack;

    public FeatureUsage(String message) {
        this.message = message;
        this.stack = Collections.unmodifiableList(createStackTrace());
    }

    /*
     * Only used in tests.
     */
    FeatureUsage(String message, List<StackTraceElement> stack) {
        if (stack == null) {
            throw new NullPointerException("stack");
        }
        this.message = message;
        this.stack = Collections.unmodifiableList(new ArrayList<StackTraceElement>(stack));
    }

    public String getMessage() {
        return message;
    }

    public List<StackTraceElement> getStack() {
        return stack;
    }

    private static List<StackTraceElement> createStackTrace() {
        StackTraceElement[] originalStack = new Exception().getStackTrace();
        boolean calledFromFound = false;
        int caller;
        for (caller = 0; caller < originalStack.length; caller++) {
            StackTraceElement current = originalStack[caller];
            String className = current.getClassName();
            if (!calledFromFound) {
                if (isFeatureUsageElement(className)) {
                    calledFromFound = true;
                }
            } else {
                if (!isFeatureUsageElement(className)) {
                    break;
                }
            }
        }

        caller = skipSystemStackElements(originalStack, caller);

        List<StackTraceElement> result = new ArrayList<StackTraceElement>();
        for (; caller < originalStack.length; caller++) {
            result.add(originalStack[caller]);
        }
        return result;
    }

    private static int skipSystemStackElements(StackTraceElement[] stackTrace, int caller) {
        for (; caller < stackTrace.length; caller++) {
            String currentClassName = stackTrace[caller].getClassName();
            if (!isSystemStackElement(currentClassName)) {
                break;
            }
        }
        return caller;
    }

    private static boolean isFeatureUsageElement(String className) {
        return className.startsWith("org.gradle.internal.featurelifecycle.")
            // TODO: remove the following line after deleting old loggers
            || className.startsWith("org.gradle.util.");
    }

    private static boolean isSystemStackElement(String className) {
        return className.startsWith("org.codehaus.groovy.")
            || className.startsWith("org.gradle.internal.metaobject.")
            || className.startsWith("groovy.")
            || className.startsWith("java.")
            || className.startsWith("jdk.internal.");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        FeatureUsage that = (FeatureUsage) o;

        if (message != null ? !message.equals(that.message) : that.message != null) {
            return false;
        }
        return stack != null ? stack.equals(that.stack) : that.stack == null;

    }

    @Override
    public int hashCode() {
        int result = message != null ? message.hashCode() : 0;
        result = 31 * result + (stack != null ? stack.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "FeatureUsage{message='" + message + "', stack=" + stack + "}";
    }
}
