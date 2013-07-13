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

import org.codehaus.groovy.runtime.StackTraceUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable description of the usage of a deprecated feature.
 */
public class DeprecatedFeatureUsage {
    private final String message;
    private final Class<?> calledFrom;
    private final List<StackTraceElement> stack;

    public DeprecatedFeatureUsage(String message, Class<?> calledFrom) {
        this.message = message;
        this.calledFrom = calledFrom;
        this.stack = Collections.emptyList();
    }

    private DeprecatedFeatureUsage(DeprecatedFeatureUsage usage, List<StackTraceElement> stack) {
        this.message = usage.message;
        this.calledFrom = usage.calledFrom;
        this.stack = stack;
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

        StackTraceElement[] originalStack = StackTraceUtils.sanitize(new Exception()).getStackTrace();
        int caller = 0;
        while (caller < originalStack.length && !originalStack[caller].getClassName().startsWith(calledFrom.getName())) {
            caller++;
        }
        while (caller < originalStack.length && originalStack[caller].getClassName().startsWith(calledFrom.getName())) {
            caller++;
        }
        caller++;
        List<StackTraceElement> stack = new ArrayList<StackTraceElement>();
        for (; caller < originalStack.length; caller++) {
            stack.add(originalStack[caller]);
        }
        return new DeprecatedFeatureUsage(this, stack);
    }
}
