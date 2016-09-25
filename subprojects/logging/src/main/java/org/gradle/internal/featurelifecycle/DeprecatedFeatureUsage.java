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
 * An immutable description of the usage of a deprecated feature.
 */
public class DeprecatedFeatureUsage {
    private final String message;
    private final List<StackTraceElement> stack;
    private final Class<?> calledFrom;

    public DeprecatedFeatureUsage(String message, Class<?> calledFrom) {
        this.message = message;
        this.calledFrom = calledFrom;
        this.stack = Collections.emptyList();
    }

    DeprecatedFeatureUsage(DeprecatedFeatureUsage usage, List<StackTraceElement> stack) {
        if (stack == null) {
            throw new NullPointerException("stack");
        }
        this.message = usage.message;
        this.calledFrom = usage.calledFrom;
        this.stack = Collections.unmodifiableList(new ArrayList<StackTraceElement>(stack));
    }

    public String getMessage() {
        return message;
    }

    public List<StackTraceElement> getStack() {
        return stack;
    }

    /**
     * Creates a copy of this usage with the stack trace populated. Implementation is a bit limited in that it assumes that
     * this method is called from the same thread that triggered the usage.
     */
    public DeprecatedFeatureUsage withStackTrace() {
        if (!stack.isEmpty()) {
            return this;
        }

        StackTraceElement[] originalStack = new Exception().getStackTrace();
        final String calledFromName = calledFrom.getName();
        boolean calledFromFound = false;
        int caller;
        for (caller = 0; caller < originalStack.length; caller++) {
            StackTraceElement current = originalStack[caller];
            if (!calledFromFound) {
                if (current.getClassName().startsWith(calledFromName)) {
                    calledFromFound = true;
                }
            } else {
                if (!current.getClassName().startsWith(calledFromName)) {
                    break;
                }
            }
        }

        caller = skipSystemStackElements(originalStack, caller);

        List<StackTraceElement> result = new ArrayList<StackTraceElement>();
        for (; caller < originalStack.length; caller++) {
            result.add(originalStack[caller]);
        }
        return new DeprecatedFeatureUsage(this, result);
    }

    private static int skipSystemStackElements(StackTraceElement[] stackTrace, int caller) {
        for (; caller < stackTrace.length; caller++) {
            String currentClassName = stackTrace[caller].getClassName();
            if (!currentClassName.startsWith("org.codehaus.groovy.")
                && !currentClassName.startsWith("org.gradle.internal.metaobject.")
                && !currentClassName.startsWith("groovy.")
                && !currentClassName.startsWith("java.")
                && !currentClassName.startsWith("jdk.internal.")
                ) {
                break;
            }
        }
        return caller;
    }
}
