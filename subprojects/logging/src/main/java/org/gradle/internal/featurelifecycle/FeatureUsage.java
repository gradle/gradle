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

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable description of the usage of a deprecated feature.
 */
public class FeatureUsage {
    private final String message;
    private final String details;
    private final String advice;
    private final Class<?> calledFrom;
    private final Exception stackTraceRoot;

    private List<StackTraceElement> stack;

    public FeatureUsage(String message, String details, String advice, Class<?> calledFrom) {
        this.message = message;
        this.details = details;
        this.advice = advice;
        this.calledFrom = calledFrom;
        this.stackTraceRoot = null;
    }

    FeatureUsage(FeatureUsage usage, Exception stackTraceRoot) {
        if (stackTraceRoot == null) {
            throw new NullPointerException("stackTraceRoot");
        }
        this.message = usage.message;
        this.details = usage.details;
        this.advice = usage.advice;
        this.calledFrom = usage.calledFrom;
        this.stackTraceRoot= stackTraceRoot;
    }

    public String getMessage() {
        return message;
    }

    public String getDetails() {
        return details;
    }

    public String getAdvice() {
        return advice;
    }

    public List<StackTraceElement> getStack() {
        if(stackTraceRoot == null){
            return Collections.emptyList();
        }

        if(stack == null) {
            stack = calculateStack(calledFrom, stackTraceRoot);
        }
        return stack;
    }

    private static List<StackTraceElement> calculateStack(Class<?> calledFrom, Exception traceRoot) {
        StackTraceElement[] originalStack = traceRoot.getStackTrace();
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
        return result;
    }

    /**
     * Creates a copy of this usage with the a deterministic trace root. Implementation is a bit limited in that it assumes that
     * this method is called from the same thread that triggered the usage.
     */
    public FeatureUsage withStackTrace() {
        if (stackTraceRoot != null) {
            return this;
        }
        return new FeatureUsage(this, new Exception());
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

    public String formattedMessage() {
        StringBuilder outputBuilder = new StringBuilder(message);
        if (!StringUtils.isEmpty(details)) {
            outputBuilder.append(" ").append(details);
        }
        if (!StringUtils.isEmpty(advice)) {
            outputBuilder.append(" ").append(advice);
        }
        return outputBuilder.toString();
    }
}
